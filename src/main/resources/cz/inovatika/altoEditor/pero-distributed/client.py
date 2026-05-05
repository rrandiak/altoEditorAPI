"""
PERO OCR relay client (single document): uploads one image to MinIO,
enqueues one job to Redis, polls for completion,
downloads txt + ALTO to -t / -a paths.
Run the worker (pero_local_worker.py) on a host that can reach the PERO server.
"""

import argparse
import os
import shutil
import sys
import tempfile
import uuid
from time import sleep, time

import redis
from minio import Minio

from constants import (
    BUCKET,
    MAX_ENGINE_ID,
    MIN_ENGINE_ID,
    QUEUE_FINAL_KEY,
    QUEUE_INCOMING_KEY,
)
from convert import tiff_to_jpeg
from models import IncomingQueueJob

DEFAULT_POLL_INTERVAL = 2
DEFAULT_JOB_TTL_SEC = 3600
PASS_THROUGH = [".jpg", ".jpeg", ".jp2"]
CONVERT_FUNCTIONS = {
    ".tif": tiff_to_jpeg,
    ".tiff": tiff_to_jpeg,
}

pero_temp_path = tempfile.mkdtemp(prefix="pero_client_")


def main():
    if not os.path.isdir(pero_temp_path):
        os.makedirs(pero_temp_path, mode=0o777)

    parser = argparse.ArgumentParser(
        description="PERO OCR relay client - single document (Redis + MinIO)"
    )
    parser.add_argument(
        "-i",
        "--image",
        help="Input image path.",
        type=str,
        required=True,
    )
    parser.add_argument(
        "-t",
        "--txt",
        help="Output path for OCR txt.",
        type=str,
        required=True,
    )
    parser.add_argument(
        "-a",
        "--alto",
        help="Output path for ALTO xml.",
        type=str,
        required=True,
    )
    parser.add_argument(
        "--redis-url",
        help="Redis URL (e.g. redis://localhost:6379/0)",
        type=str,
        required=True,
    )
    parser.add_argument(
        "--redis-username",
        help="Redis username (optional)",
        type=str,
        default=None,
    )
    parser.add_argument(
        "--redis-password",
        help="Redis password (optional)",
        type=str,
        default=None,
    )
    parser.add_argument(
        "--minio-url",
        help="MinIO endpoint (e.g. localhost:9000)",
        type=str,
        required=True,
    )
    parser.add_argument(
        "--minio-access-key", help="MinIO access key", type=str, required=True
    )
    parser.add_argument(
        "--minio-secret-key", help="MinIO secret key", type=str, required=True
    )
    parser.add_argument(
        "--minio-secure",
        help="Use HTTPS for MinIO",
        action="store_true",
        default=False,
    )
    parser.add_argument(
        "--engine",
        help="Engine to use for OCR (1-7)",
        type=int,
        default=1,
        choices=range(MIN_ENGINE_ID, MAX_ENGINE_ID + 1),
    )
    parser.add_argument(
        "--poll-interval",
        help="Poll interval in seconds",
        type=float,
        default=DEFAULT_POLL_INTERVAL,
    )
    parser.add_argument(
        "--job-ttl-sec",
        help="TTL for the Redis job hash; stranded entries expire after this many seconds",
        type=int,
        default=DEFAULT_JOB_TTL_SEC,
    )
    args = parser.parse_args()

    if not os.path.exists(args.image):
        sys.stderr.write(f"Error: Image file {args.image} does not exist.\n")
        sys.exit(-2)

    if os.path.exists(args.txt) and os.path.exists(args.alto):
        sys.exit(0)

    img_path = args.image
    ext = os.path.splitext(os.path.basename(img_path))[1].lower()

    if ext in PASS_THROUGH:
        pass
    elif ext in CONVERT_FUNCTIONS:
        img_path, ext = CONVERT_FUNCTIONS[ext](img_path)
    else:
        sys.stderr.write(f"Error: Unsupported image format: {ext}\n")
        sys.exit(-3)

    # Redis
    try:
        if args.redis_username or args.redis_password:
            from urllib.parse import urlparse

            parsed = urlparse(args.redis_url)
            r = redis.Redis(
                host=parsed.hostname or "localhost",
                port=parsed.port or 6379,
                db=int(parsed.path.lstrip("/") or 0),
                username=args.redis_username,
                password=args.redis_password,
                decode_responses=True,
            )
        else:
            r = redis.Redis.from_url(args.redis_url, decode_responses=True)
        r.ping()
    except Exception as e:
        sys.stderr.write(f"Error connecting to Redis: {e}\n")
        sys.exit(-4)

    # MinIO
    secure = args.minio_secure or args.minio_url.startswith("https://")
    endpoint = args.minio_url.replace("https://", "").replace("http://", "")
    try:
        minio_client = Minio(
            endpoint,
            access_key=args.minio_access_key,
            secret_key=args.minio_secret_key,
            secure=secure,
        )
        if not minio_client.bucket_exists(BUCKET):
            minio_client.make_bucket(BUCKET)
    except Exception as e:
        sys.stderr.write(f"Error connecting to MinIO: {e}\n")
        sys.exit(-5)

    job = IncomingQueueJob(job_id=str(uuid.uuid4()), ext=ext, engine=args.engine)

    try:
        minio_client.fput_object(BUCKET, job.img_object_key, img_path)
    except Exception as e:
        sys.stderr.write(f"Error uploading {img_path} to MinIO: {e}\n")
        sys.exit(-6)

    now = time()
    # Set job metadata with a TTL so stranded entries self-expire if this process is killed.
    r.hset(job.job_key, mapping=job.model_dump())
    r.expire(job.job_key, args.job_ttl_sec)
    r.zadd(QUEUE_INCOMING_KEY, {job.job_id: now})
    poll_interval = args.poll_interval

    def _cleanup(
        txt_key: str | None = None, alto_key: str | None = None
    ) -> None:
        """Remove Redis job hash, queue entries, and MinIO objects."""
        try:
            r.delete(job.job_key)
        except Exception as e:
            sys.stderr.write(f"Cleanup: Redis {e}\n")
        for qk in (QUEUE_INCOMING_KEY, QUEUE_FINAL_KEY):
            try:
                r.zrem(qk, job.job_id)
            except Exception as e:
                sys.stderr.write(f"Cleanup: Redis queue {qk}: {e}\n")
        for key in (job.img_object_key, txt_key, alto_key):
            if not key:
                continue
            try:
                minio_client.remove_object(BUCKET, key)
            except Exception as e:
                sys.stderr.write(f"Cleanup: MinIO {key}: {e}\n")

    # Stage 1: wait while the feeder has not yet picked up the job.
    while r.zscore(QUEUE_INCOMING_KEY, job.job_id) is not None:
        sleep(poll_interval)

    # Stage 2: wait until the harvester signals completion via the final queue.
    while r.zscore(QUEUE_FINAL_KEY, job.job_id) is None:
        sleep(poll_interval)

    # Read the actual outcome from the job hash.
    job_data = r.hgetall(job.job_key)
    status = job_data.get("status")
    if status != "done":
        error = job_data.get("error", "unknown error")
        sys.stderr.write(f"Job finished with status '{status}': {error}\n")
        _cleanup()
        sys.exit(-9)

    try:
        minio_client.fget_object(BUCKET, job.txt_object_key, args.txt)
        minio_client.fget_object(BUCKET, job.alto_object_key, args.alto)
    except Exception as e:
        sys.stderr.write(f"Error downloading results: {e}\n")
        _cleanup(job.txt_object_key, job.alto_object_key)
        sys.exit(-7)

    # Success: clean up Redis and MinIO so entries don't accumulate.
    _cleanup(job.txt_object_key, job.alto_object_key)

    if os.path.isdir(pero_temp_path):
        try:
            shutil.rmtree(pero_temp_path)
        except OSError:
            pass

    sys.exit(0)


if __name__ == "__main__":
    main()
