"""
PERO OCR relay worker: 4-thread pipeline.

  Thread 1:
    - Redis blpop for input
    - Download jpeg from MinIO
    - Put job to queue 2.
  Thread 2:
    - Take from queue 2
    - Upload jpeg to PERO
    - Batch (request_id, job_infos) until min size or max interval
    - Put batch to queue 3.
  Thread 3:
    - Take from queue 3
    - Check status by request_id
    - If not done/failed put back
    - If failed write Redis
    - If done download alto/txt, put successful to queue 4.
  Thread 4:
    - Take from queue 3
    - Upload ocr/alto to MinIO
    - Send message through Redis.
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

from constants import (
    BUCKET,
    JOB_KEY_PREFIX,
    MAX_ENGINE_ID,
    MIN_ENGINE_ID,
    QUEUE_KEY,
)
from models import RedisJob
from pero_client import PeroClient

QUEUE_BLOCK_TIMEOUT = 1
IMG_PROCESS_TIMEOUT = 120
CLEANUP_AGE_SECONDS = 3600
CLEANUP_CRON_DEFAULT = "*/10 * * * *"

# Batching for thread 2
BATCH_MIN_SIZE = 4
BATCH_MAX_INTERVAL = 2.0
MAX_PENDING_BATCHES = 4

PERO_TEMP_PATH = tempfile.mkdtemp(prefix="pero_worker_")
shutdown = threading.Event()

# Queue 1→2: (job_id, local_jpeg_path, engine, bucket, input_key)
QUEUE_1_2_MAXSIZE = 500
queue_1_2: Queue[RedisJob] = Queue(maxsize=QUEUE_1_2_MAXSIZE)

# Queue 2→3: list of (request_id, [(job_id, bucket, job_key)])
queue_2_3: Queue[tuple[str, list[RedisJob]]] = Queue(maxsize=100)

# Queue 3→4: RedisJob (with local txt/alto paths)
queue_3_4: Queue[RedisJob] = Queue(maxsize=500)


def _set_job_status(
    redis_client: redis.Redis,
    job_id: str,
    status: str,
    error: str | None = None,
) -> None:
    job_key = JOB_KEY_PREFIX + job_id
    mapping = {"status": status}
    if error:
        mapping["error"] = error
        sys.stderr.write(f"Job {job_id}: {status}: {error}\n")
    else:
        sys.stdout.write(f"Job {job_id}: {status}\n")
    redis_client.hset(job_key, mapping=mapping)


def _safe_remove(path: str | None) -> None:
    if path and os.path.exists(path):
        try:
            os.remove(path)
        except OSError:
            pass


# --- Thread 1: Redis -> MinIO download -> queue_1_2 ---
def thread_1_redis_downloader(
    redis_client: redis.Redis, minio_client: Minio
) -> None:
    """Redis blpop → download jpeg from MinIO → put job to queue 2."""
    while not shutdown.is_set():
        try:
            raw = redis_client.blpop(QUEUE_KEY, timeout=QUEUE_BLOCK_TIMEOUT)
            if raw is None:
                continue

            batch: list[RedisJob] = [
                RedisJob.model_validate(json.loads(raw[1]))
            ]

            for _ in range(QUEUE_1_2_MAXSIZE - 1):
                payload_str = redis_client.lpop(QUEUE_KEY)
                if payload_str is None:
                    break
                batch.append(RedisJob.model_validate(json.loads(payload_str)))

            for job in batch:
                if shutdown.is_set():
                    break

                if job.engine not in range(MIN_ENGINE_ID, MAX_ENGINE_ID + 1):
                    _set_job_status(
                        redis_client, job.job_id, "failed", "Invalid engine"
                    )
                    continue

                _set_job_status(redis_client, job.job_id, "downloading")

                try:
                    minio_client.fget_object(
                        bucket_name=BUCKET,
                        object_name=job.img_object_key,
                        file_path=job.get_local_img_path(PERO_TEMP_PATH),
                    )
                except Exception as e:
                    _set_job_status(redis_client, job.job_id, "failed", str(e))
                    continue

                queue_1_2.put(job)
        except Exception as e:
            if not shutdown.is_set():
                sys.stderr.write(f"Thread 1 error: {e}\n")
    sys.stdout.write("Thread 1 (redis-downloader) stopped.\n")


# --- Thread 2: queue_1_2 -> Upload images to PERO ->
# -> Post processing request -> queue_2_3 ---
def thread_2_pero_uploader(
    pero_client: PeroClient,
    redis_client: redis.Redis,
    min_batch_size: int,
    max_waiting_time: float,
    max_pending_batches: int,
) -> None:
    """
    Take from queue 2, upload jpeg to PERO,
    batch until min size or max interval, put to queue 3.
    """
    batch_by_engine: dict[int, list[RedisJob]] = defaultdict(list)
    last_emit_by_engine: dict[int, float] = defaultdict(lambda: 0.0)

    def emit_batch(engine: int) -> None:
        nonlocal batch_by_engine, last_emit_by_engine
        batch = batch_by_engine[engine]

        try:
            # Create post processing request
            request_id = pero_client.post_processing_request(
                engine, [job.img_name for job in batch]
            )

            # Upload images to the request and put the job to queue 2_3
            for job in batch:
                pero_client.upload_image(
                    request_id,
                    job.img_name,
                    job.get_local_img_path(PERO_TEMP_PATH),
                    job.content_type,
                )

            queue_2_3.put((request_id, batch))

            # Set job status to processing
            for job in batch:
                _set_job_status(redis_client, job.job_id, "processing")

        except Exception as e:
            # Set all jobs to failed
            for job in batch:
                _set_job_status(redis_client, job.job_id, "failed", str(e))
        finally:
            # Remove local image
            for job in batch:
                _safe_remove(job.get_local_img_path(PERO_TEMP_PATH))

    # Emit batch if it has reached the minimum size or the maximum waiting time
    def maybe_emit() -> None:
        nonlocal batch_by_engine, last_emit_by_engine
        now = time.monotonic()

        for engine, batch in batch_by_engine.items():
            if not batch:
                continue
            # Backpressure:
            # don't emit if queue_2_3 has more than max_pending_batches
            if queue_2_3.qsize() > max_pending_batches:
                continue

            # Emit batch if it has reached the minimum size
            # or the maximum waiting time
            if (
                len(batch) >= min_batch_size
                or (now - last_emit_by_engine[engine]) >= max_waiting_time
            ):
                emit_batch(engine)
                batch_by_engine[engine] = []
                last_emit_by_engine[engine] = now

    while not shutdown.is_set():
        try:
            job = queue_1_2.get(timeout=QUEUE_BLOCK_TIMEOUT)
            batch_by_engine[job.engine].append(job)

            maybe_emit()

        except Empty:
            maybe_emit()
            continue
        except Exception as e:
            if not shutdown.is_set():
                sys.stderr.write(f"Thread 2 error: {e}\n")

    sys.stdout.write("Thread 2 (pero-uploader) stopped.\n")


# --- Thread 3: Status checker -> download results -> queue_3_4 ---
def _is_processed(statuses: dict, job: RedisJob) -> bool:
    s = statuses.get(job.img_name)
    if isinstance(s, dict) and s.get("state") == "PROCESSED":
        return True
    s = statuses.get(job.job_id)
    return isinstance(s, dict) and s.get("state") == "PROCESSED"


def thread_3_status_checker(
    pero_client: PeroClient, redis_client: redis.Redis
) -> None:
    """Check status
    - If not done put back
    - If failed write Redis
    - If done download and put to queue 4."""
    while not shutdown.is_set():
        try:
            item = queue_2_3.get(timeout=1)
        except Empty:
            continue

        request_id, jobs = item
        try:
            result = pero_client.get_request_status(request_id)
        except Exception as e:
            sys.stderr.write(f"Thread 3: get_request_status failed: {e}\n")
            queue_2_3.put((request_id, jobs))
            continue

        if result.status == "failure":
            err = result.message or "Unknown failure"
            for job in jobs:
                _set_job_status(redis_client, job.job_id, "failed", err)
            continue

        statuses = result.request_status or {}
        success_jobs: list[RedisJob] = []
        failed_jobs: list[RedisJob] = []

        for job in jobs:
            if _is_processed(statuses, job):
                success_jobs.append(job)
            else:
                failed_jobs.append(job)

        for job in success_jobs:
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
                _set_job_status(redis_client, job.job_id, "failed", str(e))
                _safe_remove(job.get_local_txt_path(PERO_TEMP_PATH))
                _safe_remove(job.get_local_alto_path(PERO_TEMP_PATH))

        if failed_jobs:
            queue_2_3.put((request_id, failed_jobs))

    sys.stdout.write("Thread 3 (status-checker) stopped.\n")


# --- Thread 4: Upload to MinIO -> Redis done ---
def thread_4_minio_writer(
    minio_client: Minio, redis_client: redis.Redis
) -> None:
    """Upload ocr/alto to MinIO, set Redis done, send message."""
    while not shutdown.is_set():
        try:
            job = queue_3_4.get(timeout=1)
        except Empty:
            continue

        try:
            _set_job_status(redis_client, job.job_id, "uploading_results")
            minio_client.fput_object(
                BUCKET,
                job.txt_object_key,
                job.get_local_txt_path(PERO_TEMP_PATH),
            )
            minio_client.fput_object(
                BUCKET,
                job.alto_object_key,
                job.get_local_alto_path(PERO_TEMP_PATH),
            )
            redis_client.hset(
                job.job_key,
                mapping={
                    "status": "done",
                    "minio_txt_key": job.txt_object_key,
                    "minio_alto_key": job.alto_object_key,
                },
            )
            sys.stdout.write(f"Job {job.job_id} done.\n")
        except Exception as e:
            _set_job_status(redis_client, job.job_id, "failed", str(e))
            sys.stderr.write(f"Job {job.job_id}: MinIO write failed: {e}\n")
        finally:
            _safe_remove(job.get_local_txt_path(PERO_TEMP_PATH))
            _safe_remove(job.get_local_alto_path(PERO_TEMP_PATH))
    sys.stdout.write("Thread 4 (minio-writer) stopped.\n")


# --- Cleanup (cron-scheduled) ---
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
                    sys.stderr.write(
                        f"Cleanup: failed to remove {obj.object_name}: {e}\n"
                    )
    except Exception as e:
        sys.stderr.write(f"Cleanup error: {e}\n")
    return removed


def thread_cleanup_scheduler(
    minio_client: Minio, cleanup_age: int, cron_expr: str
) -> None:
    """
    Sleep until cron triggers, then run cleanup
    """

    try:
        cron = croniter(cron_expr, datetime.now())
    except Exception as e:
        sys.stderr.write(f"Invalid cleanup cron '{cron_expr}': {e}\n")
        return

    while not shutdown.is_set():
        try:
            next_run = cron.get_next(datetime)

            while not shutdown.is_set():
                if next_run <= datetime.now():
                    break
                time.sleep(QUEUE_BLOCK_TIMEOUT)

            removed = _cleanup_stranded_minio(minio_client, cleanup_age)
            sys.stdout.write(
                f"Cleanup: removed {removed} stranded object(s).\n"
            )
        except Exception as e:
            if not shutdown.is_set():
                sys.stderr.write(f"Cleanup scheduler error: {e}\n")
    sys.stdout.write("Cleanup scheduler stopped.\n")


def main() -> None:
    if not os.path.isdir(PERO_TEMP_PATH):
        os.makedirs(PERO_TEMP_PATH, mode=0o777)

    parser = argparse.ArgumentParser(
        description="PERO OCR relay worker (4-thread pipeline, Redis + MinIO)"
    )
    parser.add_argument("--server-url", required=True, help="PERO server URL")
    parser.add_argument("--api-key", required=True, help="PERO API key")
    parser.add_argument("--redis-url", required=True, help="Redis URL")
    parser.add_argument(
        "--redis-username", default=None, help="Redis username"
    )
    parser.add_argument(
        "--redis-password", default=None, help="Redis password"
    )
    parser.add_argument("--minio-url", required=True, help="MinIO endpoint")
    parser.add_argument(
        "--minio-access-key", required=True, help="MinIO access key"
    )
    parser.add_argument(
        "--minio-secret-key", required=True, help="MinIO secret key"
    )
    parser.add_argument(
        "--minio-secure", action="store_true", help="Use HTTPS for MinIO"
    )
    parser.add_argument(
        "--batch-min-size",
        type=int,
        default=BATCH_MIN_SIZE,
        help=f"Min batch size for status polling (default: {BATCH_MIN_SIZE})",
    )
    parser.add_argument(
        "--batch-max-interval",
        type=float,
        default=BATCH_MAX_INTERVAL,
        help=f"Max seconds to wait for batch (default: {BATCH_MAX_INTERVAL})",
    )
    parser.add_argument(
        "--max-pending-batches",
        type=int,
        default=MAX_PENDING_BATCHES,
        help=(
            "Don't emit new batches when status queue has more than this many "
            f"(backpressure, default: {MAX_PENDING_BATCHES})"
        ),
    )
    parser.add_argument(
        "--cleanup-age",
        type=int,
        default=CLEANUP_AGE_SECONDS,
        help=(
            "Remove MinIO objects older than N seconds "
            f"(default: {CLEANUP_AGE_SECONDS})"
        ),
    )
    parser.add_argument(
        "--cleanup-cron",
        default=CLEANUP_CRON_DEFAULT,
        help=f"Cron for cleanup (default: {CLEANUP_CRON_DEFAULT})",
    )
    args = parser.parse_args()

    try:
        if args.redis_username or args.redis_password:
            from urllib.parse import urlparse

            parsed = urlparse(args.redis_url)
            r = redis.Redis(
                host=parsed.hostname or "localhost",
                port=parsed.port or 6379,
                db=int((parsed.path or "/0").strip("/") or 0),
                username=args.redis_username,
                password=args.redis_password,
                decode_responses=True,
            )
        else:
            r = redis.Redis.from_url(args.redis_url, decode_responses=True)
        r.ping()
    except Exception as e:
        sys.stderr.write(f"Redis error: {e}\n")
        sys.exit(-1)

    secure = args.minio_secure or args.minio_url.startswith("https://")
    endpoint = args.minio_url.replace("https://", "").replace("http://", "")
    try:
        minio_client = Minio(
            endpoint,
            access_key=args.minio_access_key,
            secret_key=args.minio_secret_key,
            secure=secure,
        )
    except Exception as e:
        sys.stderr.write(f"MinIO error: {e}\n")
        sys.exit(-1)

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
        sys.stdout.write("Worker stopped.\n")

    sys.exit(0)


if __name__ == "__main__":
    main()
