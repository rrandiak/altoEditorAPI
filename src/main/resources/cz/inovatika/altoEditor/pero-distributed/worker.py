"""
PERO OCR relay worker — feeder / harvester design.

Feeder   : keeps PERO fed by maintaining a target number of in-flight images,
           submitting new batches from the incoming queue whenever capacity
           is available.
Harvester: polls every active PERO request, resolves finished images one by
           one, and retries failures back through the incoming queue.
"""

import argparse
import os
import sys
import tempfile
import threading
import time
from datetime import datetime, timezone

import redis
from constants import (
    ACTIVE_REQUESTS_KEY,
    BUCKET,
    INFLIGHT_IMAGES_KEY,
    JOB_KEY_PREFIX,
    MAX_ENGINE_ID,
    MIN_ENGINE_ID,
    QUEUE_FINAL_KEY,
    QUEUE_INCOMING_KEY,
    REQ_KEY_PREFIX,
)
from croniter import croniter
from minio import Minio
from models import IncomingQueueJob
from pero_client import PeroClient

REDIS_SOCKET_TIMEOUT = 30
REDIS_SOCKET_CONNECT_TIMEOUT = 10

PERO_TEMP_PATH = tempfile.mkdtemp(prefix="pero_worker_")
shutdown = threading.Event()


# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------

def _ts() -> str:
    return datetime.now().isoformat()


def _log_out(msg: str) -> None:
    sys.stdout.write(f"{_ts()} {msg.rstrip()}\n")


def _log_err(msg: str) -> None:
    sys.stderr.write(f"{_ts()} {msg.rstrip()}\n")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _safe_remove(path: str | None) -> None:
    if path and os.path.exists(path):
        try:
            os.remove(path)
        except OSError:
            pass


def _job_key(job_id: str) -> str:
    return f"{JOB_KEY_PREFIX}{job_id}"


def _req_jobs_key(request_id: str) -> str:
    """Redis SET key holding unresolved job_ids for a PERO request."""
    return f"{REQ_KEY_PREFIX}{request_id}:jobs"


def _oldest_job_ids(redis_client: redis.Redis, queue_key: str, limit: int) -> list[str]:
    if limit <= 0:
        return []
    return list(redis_client.zrange(queue_key, 0, limit - 1))


def _load_job(redis_client: redis.Redis, job_id: str) -> IncomingQueueJob | None:
    data = redis_client.hgetall(_job_key(job_id))
    if not data:
        return None
    ext = data.get("ext")
    engine_raw = data.get("engine")
    if not ext or not engine_raw:
        return None
    try:
        return IncomingQueueJob(job_id=job_id, ext=ext, engine=int(engine_raw))
    except Exception:
        return None


def _get_pero_image_state(statuses: dict, job: IncomingQueueJob) -> str | None:
    for key in (job.img_name, job.job_id, f"{job.job_id}.jpg"):
        s = statuses.get(key)
        if isinstance(s, dict):
            state = (s.get("state") or "").upper()
            if state:
                return state
    return None


# ---------------------------------------------------------------------------
# Status / retry
# ---------------------------------------------------------------------------

def _retry_or_fail(
    redis_client: redis.Redis,
    job_id: str,
    reason: str,
    max_retries: int,
    status_ttl_sec: int,
    queue_ttl_sec: int,
) -> None:
    key = _job_key(job_id)
    retry_count = int(redis_client.hget(key, "retry_count") or 0)
    pipe = redis_client.pipeline()
    if retry_count < max_retries:
        pipe.hset(key, mapping={
            "status": "retrying",
            "retry_count": str(retry_count + 1),
            "error": reason,
        })
        pipe.expire(key, queue_ttl_sec)
        pipe.zadd(QUEUE_INCOMING_KEY, {job_id: time.time()})
        pipe.execute()
        _log_out(f"Job {job_id} retrying ({retry_count + 1}/{max_retries}): {reason}")
    else:
        pipe.hset(key, mapping={"status": "failed", "error": reason})
        pipe.expire(key, status_ttl_sec)
        pipe.zadd(QUEUE_FINAL_KEY, {job_id: time.time()})
        pipe.execute()
        _log_err(f"Job {job_id} permanently failed after {retry_count} retries: {reason}")


# ---------------------------------------------------------------------------
# Startup: recalculate inflight counter
# ---------------------------------------------------------------------------

def _recalculate_inflight(redis_client: redis.Redis) -> int:
    """
    Derive the correct pero:inflight_images value from active request job sets.
    Called on startup to recover from a crashed worker.
    """
    request_ids = redis_client.zrange(ACTIVE_REQUESTS_KEY, 0, -1)
    total = sum(redis_client.scard(_req_jobs_key(rid)) for rid in request_ids)
    redis_client.set(INFLIGHT_IMAGES_KEY, total)
    _log_out(f"Recalculated inflight_images={total} from {len(request_ids)} active request(s).")
    return total


# ---------------------------------------------------------------------------
# MinIO cleanup
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
                    _log_err(f"Cleanup: failed to remove {obj.object_name}: {e}")
    except Exception as e:
        _log_err(f"Cleanup error: {e}")
    return removed


def _sweep_orphaned_final(
    redis_client: redis.Redis,
    minio_client: Minio,
    batch_size: int,
) -> None:
    """Remove final queue entries whose job hash has already expired."""
    job_ids = _oldest_job_ids(redis_client, QUEUE_FINAL_KEY, batch_size)
    for job_id in job_ids:
        if redis_client.exists(_job_key(job_id)):
            continue
        redis_client.zrem(QUEUE_FINAL_KEY, job_id)
        try:
            for obj in minio_client.list_objects(BUCKET, prefix=f"{job_id}/", recursive=True):
                try:
                    minio_client.remove_object(BUCKET, obj.object_name)
                except Exception:
                    pass
        except Exception:
            pass


# ---------------------------------------------------------------------------
# Feeder thread
# ---------------------------------------------------------------------------

def _thread_feeder(
    redis_client: redis.Redis,
    minio_client: Minio,
    pero_client: PeroClient,
    target_inflight: int,
    batch_size: int,
    max_retries: int,
    status_ttl_sec: int,
    queue_ttl_sec: int,
) -> None:
    try:
        inflight = max(0, int(redis_client.get(INFLIGHT_IMAGES_KEY) or 0))
        if inflight >= target_inflight:
            return

        slots = min(target_inflight - inflight, batch_size)
        job_ids = _oldest_job_ids(redis_client, QUEUE_INCOMING_KEY, slots)
        if not job_ids:
            return

        # Claim jobs atomically; skip any already taken by a concurrent feeder.
        claimed: list[IncomingQueueJob] = []
        for job_id in job_ids:
            if redis_client.zrem(QUEUE_INCOMING_KEY, job_id) == 0:
                continue
            job = _load_job(redis_client, job_id)
            if job is None:
                # Hash expired while waiting in incoming — signal client.
                redis_client.zadd(QUEUE_FINAL_KEY, {job_id: time.time()})
                continue
            if job.engine not in range(MIN_ENGINE_ID, MAX_ENGINE_ID + 1):
                _retry_or_fail(redis_client, job_id, "Invalid engine", max_retries, status_ttl_sec, queue_ttl_sec)
                continue
            claimed.append(job)

        if not claimed:
            return

        # Group by engine for batched PERO requests.
        jobs_by_engine: dict[int, list[IncomingQueueJob]] = {}
        for job in claimed:
            jobs_by_engine.setdefault(job.engine, []).append(job)

        for engine, jobs in jobs_by_engine.items():
            # Download images from MinIO to local temp.
            downloaded: list[IncomingQueueJob] = []
            for job in jobs:
                local_path = job.get_local_img_path(PERO_TEMP_PATH)
                try:
                    minio_client.fget_object(BUCKET, job.img_object_key, local_path)
                    if not os.path.exists(local_path) or os.path.getsize(local_path) == 0:
                        raise RuntimeError("Downloaded file missing or empty")
                    downloaded.append(job)
                except Exception as e:
                    _safe_remove(local_path)
                    _retry_or_fail(redis_client, job.job_id, str(e), max_retries, status_ttl_sec, queue_ttl_sec)

            if not downloaded:
                continue

            # Create PERO request.
            try:
                request_id = pero_client.post_processing_request(
                    engine, [j.img_name for j in downloaded]
                )
            except Exception as e:
                for job in downloaded:
                    _safe_remove(job.get_local_img_path(PERO_TEMP_PATH))
                    _retry_or_fail(redis_client, job.job_id, f"PERO request failed: {e}", max_retries, status_ttl_sec, queue_ttl_sec)
                continue

            # Upload images individually — per-image failures are retried independently.
            uploaded: list[IncomingQueueJob] = []
            for job in downloaded:
                try:
                    pero_client.upload_image(
                        request_id,
                        job.img_name,
                        job.get_local_img_path(PERO_TEMP_PATH),
                        job.content_type,
                    )
                    uploaded.append(job)
                except Exception as e:
                    _retry_or_fail(redis_client, job.job_id, f"Upload to PERO failed: {e}", max_retries, status_ttl_sec, queue_ttl_sec)
                finally:
                    _safe_remove(job.get_local_img_path(PERO_TEMP_PATH))

            if not uploaded:
                continue

            # Atomically record the PERO request and update job hashes.
            req_jobs_key = _req_jobs_key(request_id)
            now = time.time()
            pipe = redis_client.pipeline()
            pipe.sadd(req_jobs_key, *[j.job_id for j in uploaded])
            pipe.zadd(ACTIVE_REQUESTS_KEY, {request_id: now})
            pipe.incrby(INFLIGHT_IMAGES_KEY, len(uploaded))
            for job in uploaded:
                pipe.hset(_job_key(job.job_id), mapping={
                    "status": "processing",
                    "request_id": request_id,
                    "processing_entered_at": str(now),
                })
                pipe.expire(_job_key(job.job_id), queue_ttl_sec)
            try:
                pipe.execute()
            except Exception as e:
                _log_err(f"Feeder commit failed for request {request_id}: {e}")
                # Re-queue uploaded jobs so they are retried on the next tick.
                requeue = redis_client.pipeline()
                for job in uploaded:
                    requeue.zadd(QUEUE_INCOMING_KEY, {job.job_id: time.time()})
                try:
                    requeue.execute()
                except Exception as re2:
                    _log_err(f"Feeder re-queue after commit failure also failed: {re2}")

    except Exception as e:
        if not shutdown.is_set():
            _log_err(f"Feeder thread error: {e}")


# ---------------------------------------------------------------------------
# Harvester thread
# ---------------------------------------------------------------------------

def _thread_harvester(
    redis_client: redis.Redis,
    minio_client: Minio,
    pero_client: PeroClient,
    request_timeout_sec: int,
    max_retries: int,
    status_ttl_sec: int,
    queue_ttl_sec: int,
    sweep_batch_size: int,
) -> None:
    try:
        _sweep_orphaned_final(redis_client, minio_client, sweep_batch_size)

        request_entries = redis_client.zrange(ACTIVE_REQUESTS_KEY, 0, -1, withscores=True)
        now = time.time()

        for request_id, submitted_at in request_entries:
            req_jobs_key = _req_jobs_key(request_id)
            remaining_job_ids: set[str] = redis_client.smembers(req_jobs_key)

            # All jobs already claimed by a concurrent harvester — clean up the request.
            if not remaining_job_ids:
                redis_client.zrem(ACTIVE_REQUESTS_KEY, request_id)
                continue

            # Request timeout: retry/fail all remaining jobs.
            if now - submitted_at > request_timeout_sec:
                _log_err(
                    f"Request {request_id} timed out "
                    f"({len(remaining_job_ids)} image(s) unresolved)."
                )
                resolved = _resolve_all(
                    redis_client, req_jobs_key, remaining_job_ids,
                    "PERO request timed out",
                    max_retries, status_ttl_sec, queue_ttl_sec,
                )
                if resolved:
                    redis_client.decrby(INFLIGHT_IMAGES_KEY, resolved)
                redis_client.zrem(ACTIVE_REQUESTS_KEY, request_id)
                continue

            # Poll PERO for this request's status.
            try:
                result = pero_client.get_request_status(request_id)
            except Exception as e:
                _log_err(f"Harvester status fetch failed for request {request_id}: {e}")
                continue

            # Request-level failure: retry/fail all remaining jobs.
            if result.status == "failure":
                _log_err(f"Request {request_id} failed: {result.message}")
                resolved = _resolve_all(
                    redis_client, req_jobs_key, remaining_job_ids,
                    result.message or "PERO request-level failure",
                    max_retries, status_ttl_sec, queue_ttl_sec,
                )
                if resolved:
                    redis_client.decrby(INFLIGHT_IMAGES_KEY, resolved)
                redis_client.zrem(ACTIVE_REQUESTS_KEY, request_id)
                continue

            # Per-image resolution.
            statuses = result.request_status or {}
            resolved = 0

            for job_id in remaining_job_ids:
                job = _load_job(redis_client, job_id)

                if job is None:
                    # Hash expired — client is dead; clean up silently.
                    if redis_client.srem(req_jobs_key, job_id) == 0:
                        continue
                    resolved += 1
                    continue

                state = _get_pero_image_state(statuses, job)

                if state == "PROCESSED":
                    # Claim the job atomically before doing expensive I/O.
                    if redis_client.srem(req_jobs_key, job_id) == 0:
                        continue
                    resolved += 1
                    _finalize_job(
                        redis_client, minio_client, pero_client,
                        request_id, job,
                        max_retries, status_ttl_sec, queue_ttl_sec,
                    )

                elif state and state not in ("WAITING", "PROCESSING"):
                    if redis_client.srem(req_jobs_key, job_id) == 0:
                        continue
                    resolved += 1
                    _retry_or_fail(
                        redis_client, job_id, f"PERO image state: {state}",
                        max_retries, status_ttl_sec, queue_ttl_sec,
                    )
                # WAITING / PROCESSING / unknown: check again next tick.

            if resolved:
                redis_client.decrby(INFLIGHT_IMAGES_KEY, resolved)

            if redis_client.scard(req_jobs_key) == 0:
                redis_client.zrem(ACTIVE_REQUESTS_KEY, request_id)

    except Exception as e:
        if not shutdown.is_set():
            _log_err(f"Harvester thread error: {e}")


def _resolve_all(
    redis_client: redis.Redis,
    req_jobs_key: str,
    job_ids: set[str],
    reason: str,
    max_retries: int,
    status_ttl_sec: int,
    queue_ttl_sec: int,
) -> int:
    """Retry or fail every job in job_ids; return count of claimed entries."""
    resolved = 0
    for job_id in job_ids:
        if redis_client.srem(req_jobs_key, job_id) == 0:
            continue
        resolved += 1
        if redis_client.exists(_job_key(job_id)):
            _retry_or_fail(redis_client, job_id, reason, max_retries, status_ttl_sec, queue_ttl_sec)
    return resolved


def _finalize_job(
    redis_client: redis.Redis,
    minio_client: Minio,
    pero_client: PeroClient,
    request_id: str,
    job: IncomingQueueJob,
    max_retries: int,
    status_ttl_sec: int,
    queue_ttl_sec: int,
) -> None:
    """Download results from PERO, upload to MinIO, signal client via final queue."""
    txt_path = job.get_local_txt_path(PERO_TEMP_PATH)
    alto_path = job.get_local_alto_path(PERO_TEMP_PATH)
    try:
        pero_client.download_results(request_id, job.img_name, "txt", txt_path)
        pero_client.download_results(request_id, job.img_name, "alto", alto_path)
        minio_client.fput_object(BUCKET, job.txt_object_key, txt_path)
        minio_client.fput_object(BUCKET, job.alto_object_key, alto_path)
        pipe = redis_client.pipeline()
        pipe.hset(_job_key(job.job_id), mapping={
            "status": "done",
            "minio_txt_key": job.txt_object_key,
            "minio_alto_key": job.alto_object_key,
        })
        pipe.expire(_job_key(job.job_id), status_ttl_sec)
        pipe.zadd(QUEUE_FINAL_KEY, {job.job_id: time.time()})
        pipe.execute()
    except Exception as e:
        _log_err(f"Finalize failed for {job.job_id}: {e}")
        _retry_or_fail(
            redis_client, job.job_id, f"Finalize failed: {e}",
            max_retries, status_ttl_sec, queue_ttl_sec,
        )
    finally:
        _safe_remove(txt_path)
        _safe_remove(alto_path)


# ---------------------------------------------------------------------------
# Spawner / scheduler loops
# ---------------------------------------------------------------------------

def _spawner_loop(
    name: str,
    interval_sec: float,
    max_active: int,
    target_fn,
    should_spawn_fn=None,
) -> None:
    active: list[threading.Thread] = []
    while not shutdown.is_set():
        active = [t for t in active if t.is_alive()]
        if len(active) < max_active and (should_spawn_fn is None or should_spawn_fn()):
            t = threading.Thread(target=target_fn, name=f"{name}-worker", daemon=True)
            t.start()
            active.append(t)
        time.sleep(interval_sec)
    for t in active:
        t.join(timeout=3)
    _log_out(f"{name} spawner stopped.")


def _cleanup_scheduler(minio_client: Minio, cleanup_age: int, cron_expr: str) -> None:
    try:
        cron = croniter(cron_expr, datetime.now())
    except Exception as e:
        _log_err(f"Invalid cleanup cron '{cron_expr}': {e}")
        return
    while not shutdown.is_set():
        next_run = cron.get_next(datetime)
        while not shutdown.is_set() and datetime.now() < next_run:
            time.sleep(0.5)
        if shutdown.is_set():
            break
        removed = _cleanup_stranded_minio(minio_client, cleanup_age)
        _log_out(f"Cleanup: removed {removed} stranded object(s).")
    _log_out("Cleanup scheduler stopped.")


# ---------------------------------------------------------------------------
# Redis / MinIO builders
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


# ---------------------------------------------------------------------------
# Argument parser
# ---------------------------------------------------------------------------

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
    parser.add_argument("--status-ttl-sec", type=int, default=3600,
                        help="TTL for job hash after resolution")
    parser.add_argument("--queue-ttl-sec", type=int, default=7200,
                        help="TTL for job hash while in processing / retrying")
    parser.add_argument("--target-inflight-images", type=int, default=20,
                        help="Target number of images kept in-flight in PERO at all times")
    parser.add_argument("--request-timeout-sec", type=int, default=600,
                        help="Retry/fail a PERO request if it does not fully resolve within this many seconds")
    parser.add_argument("--max-retries", type=int, default=3,
                        help="How many times to retry a failed job before permanently failing it")
    parser.add_argument("--feeder-spawn-every-sec", type=float, default=2.0)
    parser.add_argument("--harvester-spawn-every-sec", type=float, default=5.0)
    parser.add_argument("--max-active-feeders", type=int, default=2)
    parser.add_argument("--max-active-harvesters", type=int, default=2)
    parser.add_argument("--feeder-batch-size", type=int, default=8,
                        help="Max images pulled from incoming per feeder run")
    parser.add_argument("--sweep-batch-size", type=int, default=50,
                        help="Max final-queue entries swept for orphans per harvester run")
    parser.add_argument("--cleanup-age", type=int, default=86400,
                        help="Remove MinIO objects older than this many seconds (default: 24 h)")
    parser.add_argument("--cleanup-cron", default="*/10 * * * *")
    return parser


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    os.makedirs(PERO_TEMP_PATH, exist_ok=True)
    args = _build_arg_parser().parse_args()

    try:
        redis_client = _build_redis_client(args)
        redis_client.ping()
    except Exception as e:
        _log_err(f"Redis connection failed: {e}")
        sys.exit(1)

    minio_client = _build_minio_client(args)
    _recalculate_inflight(redis_client)

    feeder_pero_client = PeroClient(args.server_url, args.api_key)
    harvester_pero_client = PeroClient(args.server_url, args.api_key)

    def feeder_task() -> None:
        _thread_feeder(
            redis_client, minio_client, feeder_pero_client,
            args.target_inflight_images, args.feeder_batch_size,
            args.max_retries, args.status_ttl_sec, args.queue_ttl_sec,
        )

    def harvester_task() -> None:
        _thread_harvester(
            redis_client, minio_client, harvester_pero_client,
            args.request_timeout_sec, args.max_retries,
            args.status_ttl_sec, args.queue_ttl_sec, args.sweep_batch_size,
        )

    def feeder_can_spawn() -> bool:
        return max(0, int(redis_client.get(INFLIGHT_IMAGES_KEY) or 0)) < args.target_inflight_images

    threads = [
        threading.Thread(
            target=_spawner_loop,
            args=("feeder", args.feeder_spawn_every_sec, args.max_active_feeders,
                  feeder_task, feeder_can_spawn),
            daemon=True,
            name="feeder-spawner",
        ),
        threading.Thread(
            target=_spawner_loop,
            args=("harvester", args.harvester_spawn_every_sec, args.max_active_harvesters,
                  harvester_task, None),
            daemon=True,
            name="harvester-spawner",
        ),
        threading.Thread(
            target=_cleanup_scheduler,
            args=(minio_client, args.cleanup_age, args.cleanup_cron),
            daemon=True,
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
        feeder_pero_client.close()
        harvester_pero_client.close()
        _log_out("Worker stopped.")


if __name__ == "__main__":
    main()
