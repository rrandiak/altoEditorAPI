"""
PERO OCR relay client (single document): uploads one image to MinIO,
enqueues one job to Redis, polls for completion and downloads txt + ALTO to -t / -a paths.
Run the worker (pero_local_worker.py) on a host that can reach the PERO server.
"""

import argparse
import json
import os
import sys
import tempfile
import uuid
from time import sleep, time

try:
    import redis
except ImportError:
    sys.stderr.write(
        "Error: redis package required. Install with: pip install redis\n"
    )
    sys.exit(-1)
try:
    from minio import Minio
except ImportError:
    sys.stderr.write(
        "Error: minio package required. Install with: pip install minio\n"
    )
    sys.exit(-1)

from PIL import Image

BUCKET = "pero-jobs"
QUEUE_KEY = "pero:queue"
JOB_KEY_PREFIX = "pero:job:"
POLL_TIMEOUT = 300
POLL_SLEEP = 0.5

pero_temp_path = tempfile.mkdtemp(prefix="pero_client_")


def get_content_type(file_extension: str) -> str:
    tiff = [".tiff", ".tif"]
    jpg = [".jpg", ".jpeg", ".JPG"]
    jp2 = [".jp2"]
    if file_extension in tiff:
        return "image/tiff"
    if file_extension in jpg:
        return "image/jpeg"
    if file_extension in jp2:
        return "image/jp2"
    sys.stderr.write(
        f"Error: the extension {file_extension} is not supported.\n"
    )
    sys.exit(-1)


def convert_tif(image_path: str) -> str:
    filename = os.path.splitext(os.path.basename(image_path))[0]
    output_file = f"{filename}.jpg"
    with Image.open(image_path) as img:
        output_path = os.path.join(pero_temp_path, output_file)
        img.save(output_path, "JPEG", quality=50)
    return output_path


def main():
    if not os.path.isdir(pero_temp_path):
        os.makedirs(pero_temp_path, mode=0o777)

    parser = argparse.ArgumentParser(
        description="PERO OCR relay client – single document (Redis + MinIO)"
    )
    parser.add_argument(
        "-i", "--image",
        help="Input image path.",
        type=str,
        required=True,
    )
    parser.add_argument(
        "-t", "--txt",
        help="Output path for OCR txt.",
        type=str,
        required=True,
    )
    parser.add_argument(
        "-a", "--alto",
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
        choices=range(1, 8),
    )
    args = parser.parse_args()

    if not os.path.exists(args.image):
        sys.stderr.write(f"Error: Image file {args.image} does not exist.\n")
        sys.exit(-1)

    if os.path.exists(args.txt) and os.path.exists(args.alto):
        sys.exit(0)

    img_name = os.path.splitext(os.path.basename(args.image))[0]
    img_path = args.image
    ext = os.path.splitext(os.path.basename(img_path))[1].lower()
    if ext in (".tif", ".tiff"):
        if os.path.exists(
            os.path.join(os.path.dirname(img_path), img_name + ".jpg")
        ):
            img_path = os.path.join(
                os.path.dirname(img_path), img_name + ".jpg"
            )
            ext = ".jpg"
        else:
            img_path = convert_tif(img_path)
            ext = ".jpg"

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
        sys.exit(-1)

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
        sys.exit(-1)

    job_id = str(uuid.uuid4())
    input_key = f"{job_id}/input{ext}"

    try:
        minio_client.fput_object(BUCKET, input_key, img_path)
    except Exception as e:
        sys.stderr.write(f"Error uploading {img_path} to MinIO: {e}\n")
        sys.exit(-1)

    payload = {
        "job_id": job_id,
        "img_name": img_name,
        "bucket": BUCKET,
        "input_key": input_key,
        "engine": args.engine,
    }
    r.rpush(QUEUE_KEY, json.dumps(payload))
    r.hset(JOB_KEY_PREFIX + job_id, mapping={"status": "pending"})

    key = JOB_KEY_PREFIX + job_id
    end = time() + POLL_TIMEOUT
    while time() < end:
        status = r.hget(key, "status")
        if status == "done":
            txt_key = r.hget(key, "minio_txt_key")
            alto_key = r.hget(key, "minio_alto_key")
            if txt_key and alto_key:
                try:
                    minio_client.fget_object(BUCKET, txt_key, args.txt)
                    minio_client.fget_object(BUCKET, alto_key, args.alto)
                except Exception as e:
                    sys.stderr.write(
                        f"Error downloading results: {e}\n"
                    )
                    r.delete(key)
                    sys.exit(-1)
            r.delete(key)
            break
        if status == "failed":
            err = r.hget(key, "error") or "Unknown error"
            sys.stderr.write(f"Job failed: {err}\n")
            r.delete(key)
            sys.exit(-1)
        sleep(POLL_SLEEP)
    else:
        sys.stderr.write("Error: Job did not complete within timeout.\n")
        sys.exit(-1)

    if os.path.isdir(pero_temp_path):
        try:
            os.rmdir(pero_temp_path)
        except OSError:
            pass

    sys.exit(0)


if __name__ == "__main__":
    main()
