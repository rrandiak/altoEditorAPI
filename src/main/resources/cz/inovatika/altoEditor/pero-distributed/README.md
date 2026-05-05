# pero-distributed

Distributed PERO OCR relay: a Java application submits one image per invocation of `client.py`, a long-running `worker.py` batches jobs into PERO requests and signals completion back through Redis. Intermediate files are staged in MinIO.

## Design goals

- **GPU never idles**: the worker maintains a configurable target number of images in-flight in PERO at all times (`--target-inflight-images`, default 20)
- **Resilient to PERO failures**: failed images and requests are automatically retried up to `--max-retries` times before being permanently failed
- **No stuck jobs**: every failure path, including request timeouts and worker crashes, eventually resolves the job through the final queue

## Architecture

```
Java app
  │
  └─► client.py ──► MinIO (upload image)
                ──► Redis pero:queue:incoming
                            │
                    worker.py feeder thread
                        │  claims jobs from incoming
                        │  downloads images from MinIO
                        │  creates PERO request + uploads images
                        │  records: pero:active_requests
                        │           pero:req:<id>:jobs
                        │           pero:inflight_images
                        │
                    worker.py harvester thread
                        │  polls each active PERO request
                        │  per-image: PROCESSED → download + upload to MinIO
                        │             FAILED    → retry or permanent fail
                        │  request timeout      → retry or permanent fail
                        │  signals via pero:queue:final
                        │
  ◄── client.py ◄─── Redis pero:queue:final
        │  reads status from pero:job:<id>
        └─► MinIO (download txt + ALTO)
            + cleanup (Redis hash, final entry, MinIO objects)
```

### Redis key schema

| Key | Type | Content |
|-----|------|---------|
| `pero:queue:incoming` | ZSET | `job_id → enqueue_ts` — jobs waiting for the feeder |
| `pero:queue:final` | ZSET | `job_id → resolve_ts` — completed or permanently-failed jobs |
| `pero:job:<job_id>` | HASH | `ext, engine, status, retry_count, error, request_id, …` |
| `pero:active_requests` | ZSET | `request_id → submitted_ts` — PERO requests currently in flight |
| `pero:req:<request_id>:jobs` | SET | unresolved `job_id`s within a PERO request |
| `pero:inflight_images` | STRING | integer — total images currently submitted to PERO |

### Inflight counter

`pero:inflight_images` is incremented atomically when the feeder commits a batch and decremented when the harvester resolves each image (any outcome). On worker startup the counter is recalculated from the active request job sets to recover from crashes.

### Retry flow

Every failure (download error, PERO request failure, per-image FAILED state, request timeout, finalize error) calls `_retry_or_fail`:

- If `retry_count < max_retries`: increment counter, re-add to `pero:queue:incoming`
- Otherwise: mark `status=failed`, add to `pero:queue:final`

The client always sees a final signal regardless of outcome.

### Stuck-job guarantees

| Scenario | Resolution |
|----------|-----------|
| Feeder crashes after claiming job from incoming | Job is re-queued on the next `_retry_or_fail` call; if feeder dies before that, the job TTL expires and the client is signalled via `_sweep_orphaned_final` |
| PERO request hangs indefinitely | `--request-timeout-sec` fires; all remaining jobs are retried/failed |
| Worker restarted mid-request | `_recalculate_inflight` restores the counter; harvester resumes polling existing requests |
| Client killed by Java timeout | Hash TTL (`--job-ttl-sec`) expires; `_sweep_orphaned_final` removes the final entry and its MinIO objects |

## Components

| File | Role |
|------|------|
| `client.py` | Single-shot relay client invoked per image by the Java app |
| `worker.py` | Long-running daemon: feeder + harvester + cleanup scheduler |
| `pero_client.py` | HTTP wrapper for the PERO OCR API |
| `models.py` | Pydantic models for job payloads and PERO API structures |
| `constants.py` | Redis key names, MinIO bucket, engine ID range |
| `convert.py` | TIFF → JPEG conversion (Pillow) |

## Requirements

Python 3.10+

```
pip install -r requirements.txt
```

## Usage

### Worker (start once, keep running)

```bash
python worker.py \
  --server-url   http://pero-ocr-host:8080 \
  --api-key      <pero-api-key> \
  --redis-url    redis://localhost:6379/0 \
  --minio-url    localhost:9000 \
  --minio-access-key <access-key> \
  --minio-secret-key <secret-key>
```

Key worker options:

| Option | Default | Description |
|--------|---------|-------------|
| `--target-inflight-images` | 20 | Target images kept in-flight in PERO at all times |
| `--request-timeout-sec` | 600 | Retry/fail a PERO request if it doesn't resolve within this time |
| `--max-retries` | 3 | Retry attempts per job before permanent failure |
| `--feeder-batch-size` | 8 | Max images pulled from incoming per feeder run |
| `--feeder-spawn-every-sec` | 2.0 | How often to consider spawning a new feeder thread |
| `--harvester-spawn-every-sec` | 5.0 | How often to consider spawning a new harvester thread |
| `--max-active-feeders` | 2 | Max concurrent feeder threads |
| `--max-active-harvesters` | 2 | Max concurrent harvester threads |
| `--status-ttl-sec` | 3600 | TTL for job hash after resolution |
| `--queue-ttl-sec` | 7200 | TTL for job hash while processing or retrying |
| `--sweep-batch-size` | 50 | Max final-queue entries swept for orphans per harvester run |
| `--cleanup-age` | 86400 | Remove MinIO objects older than this many seconds (24 h) |
| `--cleanup-cron` | `*/10 * * * *` | Cron schedule for stranded MinIO object cleanup |
| `--redis-username` / `--redis-password` | — | Redis auth (optional) |
| `--minio-secure` | false | Use HTTPS for MinIO |

> **Important**: `--cleanup-age` must be greater than the longest time a job can realistically wait in the incoming queue before the feeder picks it up. If the incoming backlog is large, increase this accordingly. The default 24 h is safe for most workloads.

### Client (invoked per image by the Java app)

```bash
python client.py \
  --image  /path/to/input.jpg \
  --txt    /path/to/output.txt \
  --alto   /path/to/output.alto \
  --redis-url    redis://localhost:6379/0 \
  --minio-url    localhost:9000 \
  --minio-access-key <access-key> \
  --minio-secret-key <secret-key>
```

Key client options:

| Option | Default | Description |
|--------|---------|-------------|
| `--engine` | 1 | PERO OCR engine (1–7) |
| `--poll-interval` | 2.0 | Seconds between queue polls |
| `--job-ttl-sec` | 3600 | TTL on the Redis job hash; stranded entries expire after this |
| `--minio-secure` | false | Use HTTPS for MinIO |
| `--redis-username` / `--redis-password` | — | Redis auth (optional) |

The client exits 0 on success, non-zero on any error. The Java application is responsible for enforcing a total execution timeout. On success the client cleans up its own Redis entries and MinIO objects.

### Supported image formats

| Format | Handling |
|--------|----------|
| `.jpg` / `.jpeg` / `.jp2` | Passed through as-is |
| `.tif` / `.tiff` | Converted to JPEG (quality 50) before upload |

## MinIO object layout

All objects are stored under a per-job prefix:

```
pero-jobs/
  <job_id>/
    input.<ext>     ← uploaded by client
    result.txt      ← uploaded by worker on success
    result.alto     ← uploaded by worker on success
```

## Job status values

| Status | Set by | Meaning |
|--------|--------|---------|
| `processing` | feeder | Job submitted to a PERO request |
| `retrying` | harvester | Failed and re-queued to incoming |
| `done` | harvester | Results uploaded to MinIO |
| `failed` | harvester | Permanently failed after max retries |

## Exit codes (client)

| Code | Meaning |
|------|---------|
| 0 | Success |
| -2 | Input image not found |
| -3 | Unsupported image format |
| -4 | Redis connection failed |
| -5 | MinIO connection failed |
| -6 | MinIO upload failed |
| -7 | Result download failed |
| -9 | Job finished with non-done status (check stderr for error) |
