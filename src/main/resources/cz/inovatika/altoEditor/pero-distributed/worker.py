"""
PERO OCR relay worker: 4-thread pipeline.

  Thread 1: Redis blpop → download JPEG from MinIO → queue_1_2
  Thread 2: queue_1_2 → batch by engine → upload to PERO → queue_2_3
  Thread 3: queue_2_3 → poll PERO status → download results → queue_3_4
  Thread 4: queue_3_4 → upload txt/alto to MinIO → mark Redis done
  Cleanup:  cron-scheduled removal of stranded MinIO objects
"""

import argparse
import json
import os
import sys
import tempfile
import threading
import time
from collections import defaultdict
from datetime import datetime, timezone
from queue import Empty, Full, Queue

import redis
from constants import BUCKET, MAX_ENGINE_ID, MIN_ENGINE_ID, QUEUE_KEY
from croniter import croniter
from minio import Minio
from models import RedisJob
from pero_client import PeroClient

QUEUE_BLOCK_TIMEOUT = 1
QUEUE_PUT_TIMEOUT = 60  # avoid blocking forever if downstream queue is full
REDIS_SOCKET_TIMEOUT = 30  # avoid blocking forever on unresponsive Redis
REDIS_SOCKET_CONNECT_TIMEOUT = 10
IMG_PROCESS_TIMEOUT = 600
CLEANUP_AGE_SECONDS = 3600
CLEANUP_CRON_DEFAULT = "*/10 * * * *"

BATCH_MIN_SIZE = 4
BATCH_MAX_INTERVAL = 2.0
MAX_PENDING_BATCHES = 4

PERO_TEMP_PATH = tempfile.mkdtemp(prefix="pero_worker_")
shutdown = threading.Event()

queue_1_2: Queue[RedisJob] = Queue(maxsize=500)
queue_2_3: Queue[tuple[str, list[RedisJob]]] = Queue(maxsize=100)
queue_3_repoll: Queue[tuple[str, list[RedisJob], float]] = Queue(maxsize=500)
queue_3_4: Queue[RedisJob] = Queue(maxsize=500)


# ---------------------------------------------------------------------------
# Logging helpers
# ---------------------------------------------------------------------------


def _ts() -> str:
    return datetime.now().isoformat()


def _log_out(msg: str) -> None:
    sys.stdout.write(f"{_ts()} {msg.rstrip()}\n")


def _log_err(msg: str) -> None:
    sys.stderr.write(f"{_ts()} {msg.rstrip()}\n")


# ---------------------------------------------------------------------------
# File-system helpers
# ---------------------------------------------------------------------------


def _safe_remove(path: str | None) -> None:
    if path and os.path.exists(path):
        try:
            os.remove(path)
        except OSError:
            pass


# ---------------------------------------------------------------------------
# Redis helpers
# ---------------------------------------------------------------------------


def _set_job_status(
    redis_client: redis.Redis,
    job: RedisJob,
    status: str,
    error: str | None = None,
) -> None:
    mapping = {"status": status}
    if error:
        mapping["error"] = error
        _log_err(f"Job {job.job_id}: {status}: {error}")
    else:
        _log_out(f"Job {job.job_id}: {status}")
    redis_client.hset(job.job_key, mapping=mapping)


def _fail_job(redis_client: redis.Redis, job: RedisJob, reason: str) -> None:
    """Mark a single job as failed and clean up its local temp files."""
    _set_job_status(redis_client, job, "failed", reason)
    _safe_remove(job.get_local_img_path(PERO_TEMP_PATH))
    _safe_remove(job.get_local_txt_path(PERO_TEMP_PATH))
    _safe_remove(job.get_local_alto_path(PERO_TEMP_PATH))


def _fail_jobs(
    redis_client: redis.Redis, jobs: list[RedisJob], reason: str
) -> None:
    """Mark multiple jobs as failed and clean up their local temp files."""
    for job in jobs:
        _fail_job(redis_client, job, reason)


# ---------------------------------------------------------------------------
# Queue helpers
# ---------------------------------------------------------------------------


def _queue_put(
    q: Queue,
    item,
    redis_client: redis.Redis,
    jobs: list[RedisJob],
    label: str,
) -> bool:
    """
    Put *item* onto *q* with a timeout.
    On failure, mark all *jobs* as failed and log the error.
    Returns True on success, False on failure.
    """
    try:
        q.put(item, timeout=QUEUE_PUT_TIMEOUT)
        return True
    except Exception as qe:
        msg = f"Pipeline full: {qe}"
        _log_err(f"{label}: queue put failed: {qe}")
        _fail_jobs(redis_client, jobs, msg)
        return False


def _queue_putback(
    q: Queue,
    item,
    redis_client: redis.Redis,
    jobs: list[RedisJob],
    label: str,
) -> bool:
    """
    Re-enqueue *item* onto *q* (e.g. for retry loops).
    On failure, mark all *jobs* as failed.
    Returns True on success, False on failure.
    """
    try:
        q.put(item, timeout=QUEUE_PUT_TIMEOUT)
        return True
    except (Full, Exception) as qe:
        msg = "Worker overloaded (putback failed)"
        _log_err(f"{label}: queue putback failed: {qe}")
        _fail_jobs(redis_client, jobs, msg)
        return False


# ---------------------------------------------------------------------------
# PERO status helper
# ---------------------------------------------------------------------------


def _get_pero_image_state(statuses: dict, job: RedisJob) -> str | None:
    """Return the PERO state string for this job's image, or None if not found."""
    for key in (job.img_name, job.job_id, f"{job.job_id}.jpg"):
        s = statuses.get(key)
        if isinstance(s, dict):
            state = (s.get("state") or "").upper()
            if state:
                return state
    return None


def _is_processed(statuses: dict, job: RedisJob) -> bool:
    return _get_pero_image_state(statuses, job) == "PROCESSED"


def _is_pero_failed(statuses: dict, job: RedisJob) -> bool:
    state = _get_pero_image_state(statuses, job)
    return state is not None and state not in ("PROCESSED", "WAITING", "PROCESSING", "")


# ---------------------------------------------------------------------------
# Thread 1: Redis → MinIO download → queue_1_2
# ---------------------------------------------------------------------------


def thread_1_redis_downloader(
    redis_client: redis.Redis, minio_client: Minio
) -> None:

    def drain_redis() -> list[bytes]:
        """
        Grab one job via blpop, then drain any immediately available extras.
        """
        raw = redis_client.blpop(QUEUE_KEY, timeout=QUEUE_BLOCK_TIMEOUT)
        if raw is None:
            return []
        payloads = [raw[1]]
        while queue_1_2.qsize() + len(payloads) < queue_1_2.maxsize:
            p = redis_client.lpop(QUEUE_KEY)
            if p is None:
                break
            payloads.append(p)
        return payloads

    def parse_job(payload: bytes) -> RedisJob | None:
        try:
            return RedisJob.model_validate(json.loads(payload))
        except Exception as e:
            _log_err(f"Thread 1: invalid job payload: {e}")
            return None

    def validate_engine(job: RedisJob) -> bool:
        if job.engine not in range(MIN_ENGINE_ID, MAX_ENGINE_ID + 1):
            _set_job_status(redis_client, job, "failed", "Invalid engine")
            return False
        return True

    def download_image(job: RedisJob) -> bool:
        """
        Download the image from MinIO to a local temp file. Returns success.
        """
        _set_job_status(redis_client, job, "downloading")
        local_path = job.get_local_img_path(PERO_TEMP_PATH)
        try:
            minio_client.fget_object(BUCKET, job.img_object_key, local_path)
            if (
                not os.path.exists(local_path)
                or os.path.getsize(local_path) == 0
            ):
                raise RuntimeError(
                    f"Downloaded file missing or empty: {local_path}"
                )
            return True
        except Exception as e:
            _fail_job(redis_client, job, str(e))
            return False

    def process_payload(payload: bytes) -> None:
        job = parse_job(payload)
        if job is None:
            return
        if not validate_engine(job):
            return
        if not download_image(job):
            return
        _queue_put(queue_1_2, job, redis_client, [job], "Thread 1")

    while not shutdown.is_set():
        try:
            for payload in drain_redis():
                if shutdown.is_set():
                    break
                process_payload(payload)
        except redis.ConnectionError as e:
            raise e
        except Exception as e:
            if not shutdown.is_set():
                _log_err(f"Thread 1 error: {e}")

    _log_out("Thread 1 (redis-downloader) stopped.")


# ---------------------------------------------------------------------------
# Thread 2: queue_1_2 → batch → upload to PERO → queue_2_3
# ---------------------------------------------------------------------------


def thread_2_pero_uploader(
    pero_client: PeroClient,
    redis_client: redis.Redis,
    min_batch_size: int,
    max_waiting_time: float,
    max_pending_batches: int,
) -> None:
    batch_by_engine: dict[int, list[RedisJob]] = defaultdict(list)
    last_emit_by_engine: dict[int, float] = defaultdict(time.monotonic)

    def create_request(engine: int, batch: list[RedisJob]) -> str | None:
        """Post a processing request to PERO; fail all jobs on error."""
        try:
            return pero_client.post_processing_request(
                engine, [job.img_name for job in batch]
            )
        except Exception as e:
            _fail_jobs(redis_client, batch, str(e))
            return None

    def upload_images(
        request_id: str, batch: list[RedisJob]
    ) -> list[RedisJob]:
        """Upload each image; return only the successfully uploaded jobs."""
        accepted: list[RedisJob] = []
        for job in batch:
            local_path = job.get_local_img_path(PERO_TEMP_PATH)
            try:
                pero_client.upload_image(
                    request_id, job.img_name, local_path, job.content_type
                )
                accepted.append(job)
            except Exception as e:
                _set_job_status(redis_client, job, "failed", str(e))
            finally:
                _safe_remove(local_path)
        return accepted

    def emit_batch(engine: int) -> None:
        batch = batch_by_engine.pop(engine, [])
        if not batch:
            return

        request_id = create_request(engine, batch)
        if request_id is None:
            return

        accepted = upload_images(request_id, batch)
        if not accepted:
            _log_err(f"Thread 2: all uploads failed for request {request_id}")
            return

        for job in accepted:
            _set_job_status(redis_client, job, "processing")
        _queue_put(
            queue_2_3,
            (request_id, accepted),
            redis_client,
            accepted,
            "Thread 2",
        )

    def maybe_emit() -> None:
        # Don't emit if downstream is already saturated
        # — backpressure to avoid flooding Thread 3
        if queue_2_3.qsize() >= max_pending_batches:
            return

        now = time.monotonic()
        for engine, batch in list(batch_by_engine.items()):
            if not batch:
                continue

            age = now - last_emit_by_engine[engine]

            # Emit when the batch is large enough for efficiency,
            # or when it has been waiting too long
            # (so small batches don't stall indefinitely)
            if len(batch) >= min_batch_size or age >= max_waiting_time:
                emit_batch(engine)
                last_emit_by_engine[engine] = now

    while not shutdown.is_set():
        try:
            job = queue_1_2.get(timeout=QUEUE_BLOCK_TIMEOUT)
            batch_by_engine[job.engine].append(job)
        except Empty:
            pass
        except Exception as e:
            if not shutdown.is_set():
                _log_err(f"Thread 2 error: {e}")
            continue
        maybe_emit()

    # On shutdown, fail any jobs still sitting in unflushed batches
    for batch in batch_by_engine.values():
        _fail_jobs(redis_client, batch, "Worker shutting down")

    _log_out("Thread 2 (pero-uploader) stopped.")


# ---------------------------------------------------------------------------
# Thread 3: queue_2_3 → poll PERO → download results → queue_3_4
# ---------------------------------------------------------------------------


def thread_3_status_checker(
    pero_client: PeroClient, redis_client: redis.Redis
) -> None:

    def next_batch() -> tuple[str, list[RedisJob], float] | None:
        """Drain re-polls first; then block on fresh batches."""
        try:
            return queue_3_repoll.get_nowait()
        except Empty:
            pass
        try:
            request_id, jobs = queue_2_3.get(timeout=QUEUE_BLOCK_TIMEOUT)
            return request_id, jobs, time.monotonic()
        except Empty:
            return None

    def repoll(
        request_id: str, jobs: list[RedisJob], enqueued_at: float
    ) -> None:
        age = time.monotonic() - enqueued_at
        if age > IMG_PROCESS_TIMEOUT:
            _fail_jobs(
                redis_client,
                jobs,
                f"PERO processing timed out after {age:.0f}s",
            )
        else:
            _queue_put(
                queue_3_repoll,
                (request_id, jobs, enqueued_at),
                redis_client,
                jobs,
                "Thread 3",
            )

    def fetch_request_status(
        request_id: str, jobs: list[RedisJob], enqueued_at: float
    ):
        """
        Fetch PERO request status.
        Re-enqueues the batch on network error.
        Returns the result object,
        or None if the batch was re-enqueued / failed.
        """
        try:
            result = pero_client.get_request_status(request_id)
            time.sleep(1)
            return result
        except Exception as e:
            _log_err(f"Thread 3: get_request_status failed: {e}")
            repoll(request_id, jobs, enqueued_at)
            return None

    def download_results(request_id: str, job: RedisJob) -> bool:
        """Download txt + alto for a single processed job. Returns success."""
        try:
            pero_client.download_results(
                request_id,
                job.img_name,
                "txt",
                job.get_local_txt_path(PERO_TEMP_PATH),
            )
            pero_client.download_results(
                request_id,
                job.img_name,
                "alto",
                job.get_local_alto_path(PERO_TEMP_PATH),
            )
            return True
        except Exception as e:
            _fail_job(redis_client, job, str(e))
            return False

    def forward_completed(request_id: str, job: RedisJob) -> None:
        """Download results and push the job to queue_3_4."""
        if download_results(request_id, job):
            _queue_put(queue_3_4, job, redis_client, [job], "Thread 3")

    def handle_batch(
        request_id: str, jobs: list[RedisJob], enqueued_at: float
    ) -> None:
        result = fetch_request_status(request_id, jobs, enqueued_at)
        if result is None:
            return  # already re-enqueued or failed inside fetch_request_status

        if result.status == "failure":
            _fail_jobs(
                redis_client, jobs, result.message or "Unknown PERO failure"
            )
            return

        statuses = result.request_status or {}
        pending: list[RedisJob] = []
        for job in jobs:
            if _is_processed(statuses, job):
                forward_completed(request_id, job)
            elif _is_pero_failed(statuses, job):
                state = _get_pero_image_state(statuses, job)
                _fail_job(
                    redis_client,
                    job,
                    f"PERO processing failed (state={state})",
                )
            else:
                pending.append(job)

        if pending:
            repoll(request_id, pending, enqueued_at)

    while not shutdown.is_set():
        try:
            batch = next_batch()

            if batch is None:
                continue

            request_id, jobs, enqueued_at = batch
            handle_batch(request_id, jobs, enqueued_at)

        except Exception as e:
            if not shutdown.is_set():
                _log_err(f"Thread 3 error: {e}")
            continue

    _log_out("Thread 3 (status-checker) stopped.")


# ---------------------------------------------------------------------------
# Thread 4: queue_3_4 → upload to MinIO → mark Redis done
# ---------------------------------------------------------------------------


def thread_4_minio_writer(
    minio_client: Minio, redis_client: redis.Redis
) -> None:

    def upload_results(job: RedisJob) -> None:
        """Upload txt + alto to MinIO and mark the job done in Redis."""
        txt_path = job.get_local_txt_path(PERO_TEMP_PATH)
        alto_path = job.get_local_alto_path(PERO_TEMP_PATH)
        try:
            _set_job_status(redis_client, job, "uploading_results")

            minio_client.fput_object(BUCKET, job.txt_object_key, txt_path)
            minio_client.fput_object(BUCKET, job.alto_object_key, alto_path)
            redis_client.hset(
                job.job_key,
                mapping={
                    "status": "done",
                    "minio_txt_key": job.txt_object_key,
                    "minio_alto_key": job.alto_object_key,
                },
            )

            _safe_remove(txt_path)
            _safe_remove(alto_path)

            _log_out(f"Job {job.job_id} done")
            sys.stdout.flush()
        except Exception as e:
            _fail_job(redis_client, job, str(e))

    while not shutdown.is_set():
        try:
            job = queue_3_4.get(timeout=QUEUE_BLOCK_TIMEOUT)
        except Empty:
            continue
        upload_results(job)

    _log_out("Thread 4 (minio-writer) stopped.")


# ---------------------------------------------------------------------------
# Cleanup scheduler
# ---------------------------------------------------------------------------


def _cleanup_stranded_minio(minio_client: Minio, older_than_sec: int) -> int:
    removed = 0
    cutoff = time.time() - older_than_sec
    try:
        for obj in minio_client.list_objects(BUCKET, recursive=True):
            if obj.last_modified is None:
                continue
            dt = obj.last_modified
            mtime = (
                dt.timestamp()
                if dt.tzinfo
                else dt.replace(tzinfo=timezone.utc).timestamp()
            )
            if mtime < cutoff:
                try:
                    minio_client.remove_object(BUCKET, obj.object_name)
                    removed += 1
                except Exception as e:
                    _log_err(
                        f"Cleanup: failed to remove {obj.object_name}: {e}"
                    )
    except Exception as e:
        _log_err(f"Cleanup error: {e}")
    return removed


def thread_cleanup_scheduler(
    minio_client: Minio, cleanup_age: int, cron_expr: str
) -> None:

    def wait_for_next_run(cron: croniter) -> bool:
        """
        Sleep until the next scheduled run. Returns False if shutdown fires.
        """
        next_run = cron.get_next(datetime)
        while not shutdown.is_set() and datetime.now() < next_run:
            time.sleep(QUEUE_BLOCK_TIMEOUT)
        return not shutdown.is_set()

    try:
        cron = croniter(cron_expr, datetime.now())
    except Exception as e:
        _log_err(f"Invalid cleanup cron '{cron_expr}': {e}")
        return

    while not shutdown.is_set():
        try:
            if not wait_for_next_run(cron):
                break
            removed = _cleanup_stranded_minio(minio_client, cleanup_age)
            _log_out(f"Cleanup: removed {removed} stranded object(s).")
        except Exception as e:
            if not shutdown.is_set():
                _log_err(f"Cleanup scheduler error: {e}")

    _log_out("Cleanup scheduler stopped.")


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------


def _build_redis_client(args: argparse.Namespace) -> redis.Redis:
    if args.redis_username or args.redis_password:
        from urllib.parse import urlparse

        p = urlparse(args.redis_url)
        return redis.Redis(
            host=p.hostname or "localhost",
            port=p.port or 6379,
            db=int((p.path or "/0").strip("/") or 0),
            username=args.redis_username,
            password=args.redis_password,
            decode_responses=True,
            socket_timeout=REDIS_SOCKET_TIMEOUT,
            socket_connect_timeout=REDIS_SOCKET_CONNECT_TIMEOUT,
        )
    return redis.Redis.from_url(
        args.redis_url,
        decode_responses=True,
        socket_timeout=REDIS_SOCKET_TIMEOUT,
        socket_connect_timeout=REDIS_SOCKET_CONNECT_TIMEOUT,
    )


def _build_minio_client(args: argparse.Namespace) -> Minio:
    secure = args.minio_secure or args.minio_url.startswith("https://")
    endpoint = args.minio_url.removeprefix("https://").removeprefix("http://")
    return Minio(
        endpoint,
        access_key=args.minio_access_key,
        secret_key=args.minio_secret_key,
        secure=secure,
    )


def _build_arg_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="PERO OCR relay worker")
    parser.add_argument("--server-url", required=True)
    parser.add_argument("--api-key", required=True)
    parser.add_argument("--redis-url", required=True)
    parser.add_argument("--redis-username", default=None)
    parser.add_argument("--redis-password", default=None)
    parser.add_argument("--minio-url", required=True)
    parser.add_argument("--minio-access-key", required=True)
    parser.add_argument("--minio-secret-key", required=True)
    parser.add_argument("--minio-secure", action="store_true")
    parser.add_argument("--batch-min-size", type=int, default=BATCH_MIN_SIZE)
    parser.add_argument(
        "--batch-max-interval", type=float, default=BATCH_MAX_INTERVAL
    )
    parser.add_argument(
        "--max-pending-batches", type=int, default=MAX_PENDING_BATCHES
    )
    parser.add_argument("--cleanup-age", type=int, default=CLEANUP_AGE_SECONDS)
    parser.add_argument("--cleanup-cron", default=CLEANUP_CRON_DEFAULT)
    return parser


def main() -> None:
    os.makedirs(PERO_TEMP_PATH, exist_ok=True)

    args = _build_arg_parser().parse_args()

    try:
        r = _build_redis_client(args)
        r.ping()
    except Exception as e:
        _log_err(f"Redis connection failed: {e}")
        sys.exit(1)

    minio_client = _build_minio_client(args)
    pero_client_t2 = PeroClient(args.server_url, args.api_key)
    pero_client_t3 = PeroClient(args.server_url, args.api_key)

    threads = [
        threading.Thread(
            target=thread_1_redis_downloader,
            args=(r, minio_client),
            name="redis-downloader",
        ),
        threading.Thread(
            target=thread_2_pero_uploader,
            args=(
                pero_client_t2,
                r,
                args.batch_min_size,
                args.batch_max_interval,
                args.max_pending_batches,
            ),
            name="pero-uploader",
        ),
        threading.Thread(
            target=thread_3_status_checker,
            args=(pero_client_t3, r),
            name="status-checker",
        ),
        threading.Thread(
            target=thread_4_minio_writer,
            args=(minio_client, r),
            name="minio-writer",
        ),
        threading.Thread(
            target=thread_cleanup_scheduler,
            args=(minio_client, args.cleanup_age, args.cleanup_cron),
            name="cleanup-scheduler",
        ),
    ]

    for t in threads:
        t.daemon = True
        t.start()

    try:
        while any(t.is_alive() for t in threads):
            for t in threads:
                t.join(timeout=0.5)
    except KeyboardInterrupt:
        pass
    finally:
        shutdown.set()
        for t in threads:
            t.join(timeout=3)
        pero_client_t2.close()
        pero_client_t3.close()
        _log_out("Worker stopped.")


if __name__ == "__main__":
    main()
