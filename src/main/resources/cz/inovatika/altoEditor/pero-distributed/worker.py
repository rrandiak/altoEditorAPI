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
from queue import Empty, Queue

import redis
from croniter import croniter
from minio import Minio

from constants import BUCKET, MAX_ENGINE_ID, MIN_ENGINE_ID, QUEUE_KEY
from models import RedisJob
from pero_client import PeroClient

QUEUE_BLOCK_TIMEOUT = 1
IMG_PROCESS_TIMEOUT = 120
CLEANUP_AGE_SECONDS = 3600
CLEANUP_CRON_DEFAULT = "*/10 * * * *"

BATCH_MIN_SIZE = 4
BATCH_MAX_INTERVAL = 2.0
MAX_PENDING_BATCHES = 4

PERO_TEMP_PATH = tempfile.mkdtemp(prefix="pero_worker_")
shutdown = threading.Event()

queue_1_2: Queue[RedisJob] = Queue(maxsize=500)
queue_2_3: Queue[tuple[str, list[RedisJob]]] = Queue(maxsize=100)
queue_3_4: Queue[RedisJob] = Queue(maxsize=500)


# --- Helpers ---
def _ts() -> str:
    return datetime.now().isoformat()


def _log_out(msg: str) -> None:
    sys.stdout.write(f"{_ts()} {msg.rstrip()}\n")


def _log_err(msg: str) -> None:
    sys.stderr.write(f"{_ts()} {msg.rstrip()}\n")


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


def _safe_remove(path: str | None) -> None:
    if path and os.path.exists(path):
        try:
            os.remove(path)
        except OSError:
            pass


def _is_processed(statuses: dict, job: RedisJob) -> bool:
    for key in (job.img_name, job.job_id, f"{job.job_id}.jpg"):
        s = statuses.get(key)
        if (
            isinstance(s, dict)
            and (s.get("state") or "").upper() == "PROCESSED"
        ):
            return True
    return False


# --- Thread 1: Redis → MinIO download → queue_1_2 ---
def thread_1_redis_downloader(
    redis_client: redis.Redis, minio_client: Minio
) -> None:
    while not shutdown.is_set():
        try:
            raw = redis_client.blpop(QUEUE_KEY, timeout=QUEUE_BLOCK_TIMEOUT)
            if raw is None:
                continue

            # Grab first job + drain any immediately available extras
            payloads = [raw[1]]
            while len(payloads) < queue_1_2.maxsize:
                p = redis_client.lpop(QUEUE_KEY)
                if p is None:
                    break
                payloads.append(p)

            for payload in payloads:
                if shutdown.is_set():
                    break
                try:
                    job = RedisJob.model_validate(json.loads(payload))
                except Exception as e:
                    _log_err(f"Thread 1: invalid job payload: {e}")
                    continue

                if job.engine not in range(MIN_ENGINE_ID, MAX_ENGINE_ID + 1):
                    _set_job_status(
                        redis_client, job, "failed", "Invalid engine"
                    )
                    continue

                _set_job_status(redis_client, job, "downloading")
                local_path = job.get_local_img_path(PERO_TEMP_PATH)
                try:
                    minio_client.fget_object(
                        BUCKET, job.img_object_key, local_path
                    )
                    if (
                        not os.path.exists(local_path)
                        or os.path.getsize(local_path) == 0
                    ):
                        raise RuntimeError(
                            f"Downloaded file missing or empty: {local_path}"
                        )
                except Exception as e:
                    _set_job_status(redis_client, job, "failed", str(e))
                    _safe_remove(local_path)
                    continue

                queue_1_2.put(job)

        except Exception as e:
            if not shutdown.is_set():
                _log_err(f"Thread 1 error: {e}")

    _log_out("Thread 1 (redis-downloader) stopped.")


# --- Thread 2: queue_1_2 → batch → upload to PERO → queue_2_3 ---
def thread_2_pero_uploader(
    pero_client: PeroClient,
    redis_client: redis.Redis,
    min_batch_size: int,
    max_waiting_time: float,
    max_pending_batches: int,
) -> None:
    batch_by_engine: dict[int, list[RedisJob]] = defaultdict(list)
    last_emit_by_engine: dict[int, float] = defaultdict(time.monotonic)

    def emit_batch(engine: int) -> None:
        batch = batch_by_engine.pop(engine, [])
        if not batch:
            return

        try:
            request_id = pero_client.post_processing_request(
                engine, [job.img_name for job in batch]
            )
        except Exception as e:
            for job in batch:
                _set_job_status(redis_client, job, "failed", str(e))
                _safe_remove(job.get_local_img_path(PERO_TEMP_PATH))
            return

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

        if accepted:
            for job in accepted:
                _set_job_status(redis_client, job, "processing")
            queue_2_3.put((request_id, accepted))
        else:
            _log_err(
                f"Thread 2: all uploads failed for request {request_id}"
            )

    def maybe_emit() -> None:
        if queue_2_3.qsize() >= max_pending_batches:
            return
        now = time.monotonic()
        for engine, batch in list(batch_by_engine.items()):
            if not batch:
                continue
            if (
                len(batch) >= min_batch_size
                or (now - last_emit_by_engine[engine]) >= max_waiting_time
            ):
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
        for job in batch:
            _set_job_status(
                redis_client, job, "failed", "Worker shutting down"
            )
            _safe_remove(job.get_local_img_path(PERO_TEMP_PATH))

    _log_out("Thread 2 (pero-uploader) stopped.")


# --- Thread 3: queue_2_3 → poll PERO → download results → queue_3_4 ---
def thread_3_status_checker(
    pero_client: PeroClient, redis_client: redis.Redis
) -> None:
    def download_and_forward(request_id: str, job: RedisJob) -> None:
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
            queue_3_4.put(job)
        except Exception as e:
            _set_job_status(redis_client, job, "failed", str(e))
            _safe_remove(job.get_local_txt_path(PERO_TEMP_PATH))
            _safe_remove(job.get_local_alto_path(PERO_TEMP_PATH))

    while not shutdown.is_set():
        try:
            request_id, jobs = queue_2_3.get(timeout=QUEUE_BLOCK_TIMEOUT)
        except Empty:
            continue
        except Exception as e:
            if not shutdown.is_set():
                _log_err(f"Thread 3 error: {e}")
            continue

        try:
            result = pero_client.get_request_status(request_id)
            time.sleep(1)
        except Exception as e:
            _log_err(f"Thread 3: get_request_status failed: {e}")
            queue_2_3.put((request_id, jobs))
            continue

        if result.status == "failure":
            for job in jobs:
                _set_job_status(
                    redis_client,
                    job,
                    "failed",
                    result.message or "Unknown PERO failure",
                )
            continue

        statuses = result.request_status or {}
        pending: list[RedisJob] = []
        for job in jobs:
            if _is_processed(statuses, job):
                download_and_forward(request_id, job)
            else:
                pending.append(job)

        if pending:
            queue_2_3.put((request_id, pending))

    _log_out("Thread 3 (status-checker) stopped.")


# --- Thread 4: queue_3_4 → upload to MinIO → mark Redis done ---
def thread_4_minio_writer(
    minio_client: Minio, redis_client: redis.Redis
) -> None:
    while not shutdown.is_set():
        try:
            job = queue_3_4.get(timeout=QUEUE_BLOCK_TIMEOUT)
        except Empty:
            continue

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
            _log_out(f"Job {job.job_id} done")
            sys.stdout.flush()
        except Exception as e:
            _set_job_status(redis_client, job, "failed", str(e))
        finally:
            _safe_remove(txt_path)
            _safe_remove(alto_path)

    _log_out("Thread 4 (minio-writer) stopped.")


# --- Cleanup scheduler ---
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
    try:
        cron = croniter(cron_expr, datetime.now())
    except Exception as e:
        _log_err(f"Invalid cleanup cron '{cron_expr}': {e}")
        return

    while not shutdown.is_set():
        try:
            next_run = cron.get_next(datetime)
            while not shutdown.is_set() and datetime.now() < next_run:
                time.sleep(QUEUE_BLOCK_TIMEOUT)
            removed = _cleanup_stranded_minio(minio_client, cleanup_age)
            _log_out(
                f"Cleanup: removed {removed} stranded object(s)."
            )
        except Exception as e:
            if not shutdown.is_set():
                _log_err(f"Cleanup scheduler error: {e}")

    _log_out("Cleanup scheduler stopped.")


# --- Entry point ---
def main() -> None:
    os.makedirs(PERO_TEMP_PATH, exist_ok=True)

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
    args = parser.parse_args()

    try:
        if args.redis_username or args.redis_password:
            from urllib.parse import urlparse

            p = urlparse(args.redis_url)
            r = redis.Redis(
                host=p.hostname or "localhost",
                port=p.port or 6379,
                db=int((p.path or "/0").strip("/") or 0),
                username=args.redis_username,
                password=args.redis_password,
                decode_responses=True,
            )
        else:
            r = redis.Redis.from_url(args.redis_url, decode_responses=True)
        r.ping()
    except Exception as e:
        _log_err(f"Redis connection failed: {e}")
        sys.exit(1)

    secure = args.minio_secure or args.minio_url.startswith("https://")
    endpoint = args.minio_url.removeprefix("https://").removeprefix("http://")
    minio_client = Minio(
        endpoint,
        access_key=args.minio_access_key,
        secret_key=args.minio_secret_key,
        secure=secure,
    )

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
