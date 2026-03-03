"""
PERO OCR relay worker: pops jobs from Redis, downloads input from MinIO,
sends to PERO server, waits for ALTO OCR, uploads results to MinIO
and marks job done. Runs indefinitely until stopped (e.g. Ctrl+C).
Run on a host that can reach the PERO server. Requires Redis and MinIO.
"""

import argparse
import json
import os
import posixpath
import sys
import tempfile
import time
from time import sleep

import requests
from requests_toolbelt import MultipartEncoder

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

BUCKET = "pero-jobs"
QUEUE_KEY = "pero:queue"
JOB_KEY_PREFIX = "pero:job:"
DOWNLOAD_INTERVAL = 0.5
IMG_PROCESS_TIMEOUT = 10
CLEANUP_AGE_SECONDS = 3600  # 1 hour
CLEANUP_RUN_INTERVAL = 600  # run cleanup at most every 10 min when idle

pero_temp_path = tempfile.mkdtemp(prefix="pero_worker_")


def cleanup_stranded_minio(
    minio_client: Minio, bucket: str, older_than_sec: int
) -> int:
    """
    Remove objects in bucket that are older than older_than_sec.
    Returns count removed.
    """
    from datetime import timezone

    removed = 0
    cutoff = time.time() - older_than_sec
    try:
        objects = minio_client.list_objects(bucket, recursive=True)
        for obj in objects:
            if obj.last_modified is None:
                continue
            dt = obj.last_modified
            if dt.tzinfo is not None:
                mtime = dt.timestamp()
            else:
                mtime = dt.replace(tzinfo=timezone.utc).timestamp()
            if mtime < cutoff:
                try:
                    minio_client.remove_object(bucket, obj.object_name)
                    removed += 1
                except Exception as e:
                    sys.stderr.write(
                        f"Cleanup: failed to remove {obj.object_name}: {e}\n"
                    )
    except Exception as e:
        sys.stderr.write(f"Cleanup: list/remove error: {e}\n")
    return removed


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
    raise ValueError(f"Unsupported extension: {file_extension}")


def create_json(file_name: str, engine_id: int) -> str:
    data_dict = {"engine": engine_id, "images": {file_name: None}}
    return json.dumps(data_dict, ensure_ascii=False)


def post_processing_request(
    session: requests.Session, server_url: str, data: str, api_key: str
) -> str:
    url = posixpath.join(
        server_url.rstrip("/") + "/", "post_processing_request"
    )
    headers = {"api-key": api_key, "Content-Type": "application/json"}
    response = session.post(url, data=data, headers=headers)
    if response.status_code >= 400:
        raise RuntimeError(
            f"post_processing_request failed: {response.status_code}"
        )
    out = response.json()
    request_id = out.get("request_id")
    if not request_id:
        raise RuntimeError("No request_id in response")
    retries = 5
    while out.get("status") != "success" and retries > 0:
        sleep(15)
        retries -= 1
        response = session.get(
            posixpath.join(
                server_url.rstrip("/") + "/", "request_status", request_id
            ),
            headers=headers,
        )
        if response.status_code == 200:
            out = response.json()
    return request_id


def upload_image(
    session: requests.Session,
    server_url: str,
    request_id: str,
    file_name: str,
    image_path: str,
    content_type: str,
    api_key: str,
) -> None:
    url = posixpath.join(
        server_url.rstrip("/") + "/", "upload_image", request_id, file_name
    )
    with open(image_path, "rb") as f:
        m = MultipartEncoder(fields={"file": (image_path, f, content_type)})
        headers = {"api-key": api_key, "Content-Type": m.content_type}
        response = session.post(url, data=m, headers=headers, timeout=60)
    if response.status_code >= 400:
        raise RuntimeError(f"upload_image failed: {response.status_code}")


def get_job_status(
    session: requests.Session, server_url: str, request_id: str, api_key: str
) -> dict:
    url = posixpath.join(
        server_url.rstrip("/") + "/", "request_status", request_id
    )
    headers = {"api-key": api_key, "Content-Type": "application/json"}
    response = session.get(url, headers=headers)
    if response.status_code == 200:
        return response.json()
    return {"status": "failure", "message": response.text}


def download_results(
    session: requests.Session,
    server_url: str,
    output_path: str,
    request_id: str,
    file_name: str,
    result_format: str,
    api_key: str,
) -> None:
    url = posixpath.join(
        server_url.rstrip("/") + "/",
        "download_results",
        request_id,
        file_name,
        result_format,
    )
    headers = {"api-key": api_key, "Content-Type": "application/json"}
    response = session.get(url, headers=headers, timeout=30)
    if response.status_code != 200:
        raise RuntimeError(f"download_results failed: {response.status_code}")
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(response.text)


def process_one_job(
    session: requests.Session,
    server_url: str,
    api_key: str,
    engine: int,
    local_image_path: str,
    img_name: str,
    result_txt_path: str,
    result_alto_path: str,
) -> None:
    ext = os.path.splitext(local_image_path)[1].lower()
    content_type = get_content_type(ext)
    data = create_json(img_name, engine)
    request_id = post_processing_request(session, server_url, data, api_key)
    upload_image(
        session,
        server_url,
        request_id,
        img_name,
        local_image_path,
        content_type,
        api_key,
    )

    start = time.time()
    while time.time() - start < IMG_PROCESS_TIMEOUT:
        result = get_job_status(session, server_url, request_id, api_key)
        if result.get("status") == "failure":
            raise RuntimeError(result.get("message", "Unknown failure"))
        statuses = result.get("request_status") or {}
        st = statuses.get(img_name, {})
        if st.get("state") == "PROCESSED":
            download_results(
                session,
                server_url,
                result_txt_path,
                request_id,
                img_name,
                "txt",
                api_key,
            )
            download_results(
                session,
                server_url,
                result_alto_path,
                request_id,
                img_name,
                "alto",
                api_key,
            )
            return
        sleep(DOWNLOAD_INTERVAL)
    raise RuntimeError("Timeout waiting for PERO server to process image")


def main():
    if not os.path.isdir(pero_temp_path):
        os.makedirs(pero_temp_path, mode=0o777)

    parser = argparse.ArgumentParser(
        description="PERO OCR relay worker (Redis + MinIO + server)"
    )
    parser.add_argument(
        "--server-url", help="PERO server URL", type=str, required=True
    )
    parser.add_argument(
        "--api-key", help="PERO API key", type=str, required=True
    )
    parser.add_argument(
        "--redis-url", help="Redis URL", type=str, required=True
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
        "--minio-url", help="MinIO endpoint", type=str, required=True
    )
    parser.add_argument(
        "--minio-access-key", help="MinIO access key", type=str, required=True
    )
    parser.add_argument(
        "--minio-secret-key", help="MinIO secret key", type=str, required=True
    )
    parser.add_argument(
        "--minio-secure", action="store_true", help="Use HTTPS for MinIO"
    )
    parser.add_argument(
        "--queue-timeout",
        type=int,
        default=30,
        help="Seconds to block waiting for a job (default 30)",
    )
    parser.add_argument(
        "--session-inactivity-timeout",
        type=int,
        default=60,
        help=(
            "Close HTTP session after this many seconds "
            "without a job (default 60)"
        ),
    )
    parser.add_argument(
        "--cleanup-age",
        type=int,
        default=CLEANUP_AGE_SECONDS,
        help=(
            "Remove MinIO objects older than this many seconds "
            "(default 3600 = 1 hour)"
        ),
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
        sys.stderr.write(f"Error connecting to Redis: {e}\n")
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
        sys.stderr.write(f"Error connecting to MinIO: {e}\n")
        sys.exit(-1)

    session = None
    last_activity = 0.0
    last_cleanup = 0.0

    while True:
        try:
            raw = r.brpop(QUEUE_KEY, timeout=args.queue_timeout)
            if not raw:
                now = time.time()
                if (now - last_cleanup) >= CLEANUP_RUN_INTERVAL:
                    n = cleanup_stranded_minio(
                        minio_client, BUCKET, args.cleanup_age
                    )
                    if n > 0:
                        sys.stdout.write(
                            "Cleanup: "
                            f"removed {n} stranded object(s) from MinIO.\n"
                        )
                    last_cleanup = now
                continue

            now = time.time()
            if (
                session is not None
                and (now - last_activity) > args.session_inactivity_timeout
            ):
                session.close()
                session = None
            if session is None:
                session = requests.Session()
            last_activity = now

            _, payload_str = raw
            payload = json.loads(payload_str)
            job_id = payload["job_id"]
            img_name = payload["img_name"]
            bucket = payload.get("bucket", BUCKET)
            input_key = payload["input_key"]
            engine = payload.get("engine", 1)
            if engine not in range(1, 8):
                engine = 1

            job_key = JOB_KEY_PREFIX + job_id
            r.hset(job_key, mapping={"status": "processing"})

            local_input = os.path.join(
                pero_temp_path,
                f"{job_id}_input{os.path.splitext(input_key)[1]}",
            )
            local_txt = os.path.join(pero_temp_path, f"{job_id}.txt")
            local_alto = os.path.join(pero_temp_path, f"{job_id}.alto")

            try:
                minio_client.fget_object(bucket, input_key, local_input)
            except Exception as e:
                r.hset(job_key, mapping={"status": "failed", "error": str(e)})
                sys.stderr.write(f"Job {job_id}: MinIO download failed: {e}\n")
                continue

            try:
                process_one_job(
                    session,
                    args.server_url,
                    args.api_key,
                    engine,
                    local_input,
                    img_name,
                    local_txt,
                    local_alto,
                )
                last_activity = time.time()
                txt_key = f"{job_id}/result.txt"
                alto_key = f"{job_id}/result.alto"
                minio_client.fput_object(bucket, txt_key, local_txt)
                minio_client.fput_object(bucket, alto_key, local_alto)
                r.hset(
                    job_key,
                    mapping={
                        "status": "done",
                        "minio_txt_key": txt_key,
                        "minio_alto_key": alto_key,
                    },
                )
                sys.stdout.write(f"Job {job_id} done.\n")
            except Exception as e:
                r.hset(job_key, mapping={"status": "failed", "error": str(e)})
                sys.stderr.write(f"Job {job_id} failed: {e}\n")
                continue
            finally:
                for p in (local_input, local_txt, local_alto):
                    if os.path.exists(p):
                        try:
                            os.remove(p)
                        except OSError:
                            pass
        except KeyboardInterrupt:
            if session is not None:
                session.close()
            sys.stdout.write("Worker stopped.\n")
            break
        except Exception as e:
            sys.stderr.write(f"Worker error: {e}\n")

    sys.exit(0)


if __name__ == "__main__":
    main()
