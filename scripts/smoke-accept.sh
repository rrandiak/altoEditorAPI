#!/usr/bin/env bash
#
# smoke-accept.sh — end-to-end smoke test for the parallelized ACCEPT_VERSIONS batch
# (Phase 1 of the modernization work) against a *dev* Kramerius instance.
#
# What it does
#   1. (optional) builds the app jar and boots it with your dev credentials
#   2. RETRIEVE_HIERARCHY for a test PID  -> mirrors the hierarchy and pulls the
#      existing ALTO from Kramerius into the local store as ACTIVE versions
#   3. ACCEPT_VERSIONS for that hierarchy -> re-uploads that ALTO to Kramerius via
#      the new bounded-parallel path, then refreshes ancestor page counts once
#   4. polls both batches, prints elapsed time + processed/estimated counts
#
# You are prompted for all sensitive values at runtime; nothing is written to disk.
# Secrets are passed to the app via env vars (never as process args / never committed).
#
# Requirements: bash, curl, jq, Java 21, docker (for the throwaway Postgres container;
# not needed with --no-app), a Maven settings.xml with a GitHub Packages PAT
# (read:packages) for the akubra/kramerius deps.
#
# Usage:
#   scripts/smoke-accept.sh                 # build + boot the app, then run the flow
#   scripts/smoke-accept.sh --no-app        # app already running; just drive the API
#   scripts/smoke-accept.sh --keep-app      # leave the app running after the test
#
# Tuning / throughput sweep:
#   ALTO_EDITOR_WORKER_THREADS=N            # accept parallelism (pool = N-1); default 8
#   ALTO_EDITOR_MAX_CONNECTIONS=N           # Kramerius HTTP connection pool; default 32
#   SWEEP="7 16 32 48" scripts/smoke-accept.sh
#       reboots the app at each workerThreads value, re-runs ACCEPT against the
#       already-seeded store, and prints a pages/sec table to find Kramerius' knee.
#
set -euo pipefail

# --------------------------------------------------------------------------- #
# Config (non-sensitive; override via env before running)
# --------------------------------------------------------------------------- #
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"
APP_URL="${APP_URL:-http://localhost:8080}"
INSTANCE_KEY="${INSTANCE_KEY:-k7-mzk}"        # key under application.kramerius-instances
STORE_PATH="${ALTO_EDITOR_STORE_PATH:-/tmp/altoEditorSmokeStore}"
INDEX_PATH="${ALTO_EDITOR_INDEX_DIRECTORY:-/tmp/altoEditorSmokeIndex}"  # Hibernate Search dir (isolated per run)
POLL_INTERVAL="${POLL_INTERVAL:-3}"           # seconds between batch status polls
POLL_TIMEOUT="${POLL_TIMEOUT:-1800}"          # max seconds to wait for a batch

# Concurrency knobs (passed through to the app; overridable per sweep value).
WORKER_THREADS="${ALTO_EDITOR_WORKER_THREADS:-8}"
MAX_CONNECTIONS="${ALTO_EDITOR_MAX_CONNECTIONS:-32}"
SWEEP="${SWEEP:-}"                            # space-separated workerThreads values to compare

# Postgres: the app uses PostgreSQL-specific SQL (e.g. `ON CONFLICT`) that H2 can't
# parse, so the smoke test runs against a throwaway Postgres container matching prod.
PG_IMAGE="${PG_IMAGE:-postgres:16-alpine}"
PG_CONTAINER="${PG_CONTAINER:-altoeditor-smoke-pg}"
PG_PORT="${PG_PORT:-55432}"                   # host port (offset to avoid a local 5432)
PG_DB="${PG_DB:-altoeditor}"
PG_USER="${PG_USER:-altoeditor}"
PG_PASSWORD="${PG_PASSWORD:-altoeditor}"

START_APP=1
KEEP_APP=0
for arg in "$@"; do
  case "$arg" in
    --no-app)   START_APP=0 ;;
    --keep-app) KEEP_APP=1 ;;
    -h|--help)  grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

die()  { echo "ERROR: $*" >&2; exit 1; }
info() { echo -e "\033[1;34m==>\033[0m $*"; }
ok()   { echo -e "\033[1;32m ok\033[0m $*"; }

command -v curl >/dev/null || die "curl not found"
command -v jq   >/dev/null || die "jq not found"
if [ "$START_APP" -eq 1 ]; then
  command -v docker >/dev/null || die "docker not found (needed for the Postgres container; use --no-app to bring your own app+db)"
fi

# --------------------------------------------------------------------------- #
# Prompt for sensitive / environment-specific values
# --------------------------------------------------------------------------- #
prompt()        { local v; read -rp "$1" v; echo "$v"; }
prompt_secret() { local v; read -rsp "$1" v; echo >&2; echo "$v"; }

info "Enter dev environment details (leave app fields blank if using --no-app)"

DEV_KRAMERIUS_URL="${DEV_KRAMERIUS_URL:-}"
TEST_PID="${TEST_PID:-}"
CURATOR_TOKEN="${CURATOR_TOKEN:-}"
CLIENT_ID="${ALTO_EDITOR_SERVICE_CLIENT_ID:-}"
CLIENT_SECRET="${ALTO_EDITOR_SERVICE_SECRET:-}"

[ -n "$DEV_KRAMERIUS_URL" ] || DEV_KRAMERIUS_URL="$(prompt 'Dev Kramerius base URL (e.g. https://k7-dev.mzk.cz/): ')"
[ -n "$TEST_PID" ]          || TEST_PID="$(prompt 'Test hierarchy PID (uuid:...): ')"
[ -n "$CURATOR_TOKEN" ]     || CURATOR_TOKEN="$(prompt_secret 'CURATOR bearer token (from dev Kramerius login): ')"

[ -n "$DEV_KRAMERIUS_URL" ] || die "dev Kramerius URL is required"
[ -n "$TEST_PID" ]          || die "test PID is required"
[ -n "$CURATOR_TOKEN" ]     || die "CURATOR token is required"
case "$DEV_KRAMERIUS_URL" in
  *api.kramerius.mzk.cz*) die "refusing to run against production ($DEV_KRAMERIUS_URL)" ;;
esac

if [ "$START_APP" -eq 1 ]; then
  [ -n "$CLIENT_ID" ]     || CLIENT_ID="$(prompt_secret 'Kramerius service client id (write-capable): ')"
  [ -n "$CLIENT_SECRET" ] || CLIENT_SECRET="$(prompt_secret 'Kramerius service secret: ')"
  [ -n "$CLIENT_ID" ]     || die "service client id is required to boot the app"
  [ -n "$CLIENT_SECRET" ] || die "service secret is required to boot the app"
fi

AUTH_HDR="Authorization: Bearer ${CURATOR_TOKEN}"

# --------------------------------------------------------------------------- #
# App lifecycle (build once; boot/reboot to apply a given concurrency setting)
# --------------------------------------------------------------------------- #
APP_PID=""
PG_STARTED=0
JAR=""
cleanup() {
  if [ -n "$APP_PID" ] && [ "$KEEP_APP" -eq 0 ]; then
    info "Stopping app (pid $APP_PID)"
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
  if [ "$PG_STARTED" -eq 1 ] && [ "$KEEP_APP" -eq 0 ]; then
    info "Removing Postgres container ($PG_CONTAINER)"
    docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

wait_app_ready() {
  info "Waiting for $APP_URL/api/system/info"
  for i in $(seq 1 60); do
    if curl -fsS "$APP_URL/api/system/info" >/dev/null 2>&1; then ok "app is up"; return 0; fi
    [ "$i" -eq 60 ] && die "app did not become ready (see /tmp/smoke-accept-app.log)"
    sleep 2
  done
}

# start_app [workerThreads] [maxConnections] — boot the jar (pinning accept
# concurrency) and block until the health endpoint responds.
start_app() {
  local wt="${1:-$WORKER_THREADS}" mc="${2:-$MAX_CONNECTIONS}"
  info "Booting app against $DEV_KRAMERIUS_URL (workerThreads=$wt maxConnections=$mc, db: Postgres@$PG_PORT)"
  mkdir -p "$STORE_PATH" "$INDEX_PATH"
  # Secrets go via env (not visible in `ps`); the dev URL overrides the configured
  # instance via a Spring command-line property.
  ALTO_EDITOR_SERVICE_CLIENT_ID="$CLIENT_ID" \
  ALTO_EDITOR_SERVICE_SECRET="$CLIENT_SECRET" \
  ALTO_EDITOR_STORE_PATH="$STORE_PATH" \
  ALTO_EDITOR_INDEX_DIRECTORY="$INDEX_PATH" \
  ALTO_EDITOR_JDBC_DRIVER="org.postgresql.Driver" \
  ALTO_EDITOR_JDBC_URL="jdbc:postgresql://localhost:${PG_PORT}/${PG_DB}" \
  ALTO_EDITOR_JDBC_USERNAME="$PG_USER" \
  ALTO_EDITOR_JDBC_PASSWORD="$PG_PASSWORD" \
  ALTO_EDITOR_WORKER_THREADS="$wt" \
  ALTO_EDITOR_MAX_CONNECTIONS="$mc" \
  "$JAVA_HOME/bin/java" -Daltoeditor.home="$ROOT_DIR" -jar "$JAR" \
    --application.kramerius-instances.${INSTANCE_KEY}.url="$DEV_KRAMERIUS_URL" \
    >/tmp/smoke-accept-app.log 2>&1 &
  APP_PID=$!
  ok "app pid $APP_PID (logs: /tmp/smoke-accept-app.log)"
  wait_app_ready
}

stop_app() {
  [ -n "$APP_PID" ] || return 0
  info "Stopping app (pid $APP_PID)"
  kill "$APP_PID" 2>/dev/null || true
  wait "$APP_PID" 2>/dev/null || true
  APP_PID=""
}

if [ "$START_APP" -eq 1 ]; then
  export JAVA_HOME
  info "Building jar (mvn package -DskipTests)"
  mvn -q package -DskipTests >/tmp/smoke-accept-build.log 2>&1 || {
    tail -30 /tmp/smoke-accept-build.log; die "build failed (see /tmp/smoke-accept-build.log)";
  }
  JAR="$(ls -t target/*.jar | grep -v '\.original$' | head -1)"
  [ -n "$JAR" ] || die "no jar produced in target/"
  ok "built $JAR"

  info "Starting Postgres container ($PG_IMAGE) on host port $PG_PORT"
  docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
  docker run -d --name "$PG_CONTAINER" \
    -e POSTGRES_DB="$PG_DB" \
    -e POSTGRES_USER="$PG_USER" \
    -e POSTGRES_PASSWORD="$PG_PASSWORD" \
    -p "${PG_PORT}:5432" \
    "$PG_IMAGE" >/dev/null || die "failed to start Postgres container"
  PG_STARTED=1
  info "Waiting for Postgres to accept connections"
  for i in $(seq 1 30); do
    if docker exec "$PG_CONTAINER" pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1; then
      ok "Postgres is ready"; break
    fi
    [ "$i" -eq 30 ] && die "Postgres did not become ready"
    sleep 1
  done

  # Fresh DB (new container) must be paired with a fresh store + search index, or the
  # persistent Hibernate Search index returns stale version hits from earlier runs.
  info "Resetting store + search index for a clean run ($STORE_PATH, $INDEX_PATH)"
  rm -rf "$STORE_PATH" "$INDEX_PATH"

  start_app
else
  [ -z "$SWEEP" ] || die "SWEEP requires the script to manage the app (drop --no-app)"
  wait_app_ready
fi

# --------------------------------------------------------------------------- #
# Ensure the CURATOR user has a local profile (first login creates it in the DB).
# Without this the app logs "User not found in database" and batch ownership
# (createdBy) is unresolved.
# --------------------------------------------------------------------------- #
info "Ensuring local user profile exists (POST /api/users/me)"
ME_RESP="$(curl -fsS -X POST -H "$AUTH_HDR" "$APP_URL/api/users/me")" \
  || die "failed to create/ensure current user profile"
ok "user profile: $(jq -rc '{id, username, roles}' <<<"$ME_RESP" 2>/dev/null || echo "$ME_RESP")"

# --------------------------------------------------------------------------- #
# Helpers to trigger + poll batches
# --------------------------------------------------------------------------- #
# poll_batch <id> <label>  -> waits until DONE, dies on FAILED/timeout
# poll_batch <id> <label>  -> waits until DONE, dies on FAILED/timeout.
# On success sets LAST_PROC / LAST_EST for the caller.
LAST_PROC=0
LAST_EST=0
poll_batch() {
  local id="$1" label="$2" waited=0 state est proc
  while :; do
    local body
    body="$(curl -fsS -H "$AUTH_HDR" \
      "$APP_URL/api/batches?size=100&sort=createdAt,desc" \
      | jq -c --argjson id "$id" '.content[] | select(.id==$id)')"
    [ -n "$body" ] || die "$label: batch $id not found"
    state="$(jq -r '.state' <<<"$body")"
    est="$(jq -r '.estimatedItemCount // 0' <<<"$body")"
    proc="$(jq -r '.processedItemCount // 0' <<<"$body")"
    printf '\r    %s: state=%-8s %s/%s (%ss)   ' "$label" "$state" "$proc" "$est" "$waited"
    case "$state" in
      DONE)   echo; LAST_PROC="$proc"; LAST_EST="$est"; ok "$label done ($proc/$est)"; return 0 ;;
      FAILED) echo; jq -r '.log // "no log"' <<<"$body"; die "$label FAILED" ;;
    esac
    sleep "$POLL_INTERVAL"; waited=$((waited + POLL_INTERVAL))
    [ "$waited" -ge "$POLL_TIMEOUT" ] && { echo; die "$label timed out after ${POLL_TIMEOUT}s"; }
  done
}

# run_accept -> trigger one ACCEPT_VERSIONS, poll to DONE, set ACCEPT_ID/ELAPSED/PROC.
ACCEPT_USER_ID=""
run_accept() {
  local resp body start_ts
  body="$(jq -nc --arg pid "$TEST_PID" --argjson uid "$ACCEPT_USER_ID" \
    '{hierarchyPid:$pid, states:["ACTIVE"], users:[$uid]}')"
  start_ts="$(date +%s)"
  resp="$(curl -fsS -X POST -H "$AUTH_HDR" -H 'Content-Type: application/json' \
    -d "$body" "$APP_URL/api/alto-versions/accept")"
  ACCEPT_ID="$(jq -r '.id' <<<"$resp")"
  [ "$ACCEPT_ID" != "null" ] || die "accept batch not created: $resp"
  ok "accept batch id=$ACCEPT_ID"
  poll_batch "$ACCEPT_ID" "accept"
  ELAPSED=$(( $(date +%s) - start_ts ))
  PROC="$LAST_PROC"
}

# pages_per_sec <pages> <seconds>
pages_per_sec() {
  awk -v p="$1" -v s="$2" 'BEGIN { printf "%.1f", (s > 0 ? p / s : p) }'
}

# --------------------------------------------------------------------------- #
# 1) RETRIEVE_HIERARCHY  (seed: pull existing ALTO into the store as versions)
# --------------------------------------------------------------------------- #
info "1/2 RETRIEVE_HIERARCHY for $TEST_PID"
RETRIEVE_RESP="$(curl -fsS -X POST -H "$AUTH_HDR" \
  "$APP_URL/api/hierarchy/${TEST_PID}/fetch-from-kramerius?instance=${INSTANCE_KEY}")"
RETRIEVE_ID="$(jq -r '.id' <<<"$RETRIEVE_RESP")"
[ "$RETRIEVE_ID" != "null" ] || die "retrieve batch not created: $RETRIEVE_RESP"
ok "retrieve batch id=$RETRIEVE_ID"
poll_batch "$RETRIEVE_ID" "retrieve"

# Retrieve stores its versions under the Kramerius system user whose username is the
# instance key. Scope the accept to that user so we push one version per page (mirrors
# real usage: accept a specific engine/user's output, not every ACTIVE version).
info "Resolving retrieve-owner user id (username=$INSTANCE_KEY)"
USERS_RESP="$(curl -fsS -H "$AUTH_HDR" "$APP_URL/api/users?size=500")"
ACCEPT_USER_ID="$(jq -r --arg u "$INSTANCE_KEY" 'first(.content[] | select(.username==$u) | .id) // empty' <<<"$USERS_RESP")"
[ -n "$ACCEPT_USER_ID" ] || die "could not find retrieve-owner user '$INSTANCE_KEY' via /api/users"
ok "accept scoped to user id=$ACCEPT_USER_ID ($INSTANCE_KEY)"

# --------------------------------------------------------------------------- #
# 2) ACCEPT_VERSIONS  (the code under test: parallel upload back to Kramerius)
#    Single run by default; with SWEEP set, re-run at each concurrency level.
# --------------------------------------------------------------------------- #
if [ -n "$SWEEP" ]; then
  info "2/2 ACCEPT_VERSIONS throughput sweep over workerThreads: $SWEEP"
  SWEEP_ROWS=""
  for wt in $SWEEP; do
    # Keep the connection pool from capping the worker pool.
    mc=$(( wt > MAX_CONNECTIONS ? wt : MAX_CONNECTIONS ))
    stop_app
    start_app "$wt" "$mc"
    run_accept
    rate="$(pages_per_sec "$PROC" "$ELAPSED")"
    ok "workerThreads=$wt -> $PROC pages in ${ELAPSED}s = ${rate} pages/s"
    SWEEP_ROWS="${SWEEP_ROWS}${wt}\t${mc}\t${PROC}\t${ELAPSED}\t${rate}\n"
  done
  echo
  ok "SWEEP RESULTS ($TEST_PID)"
  { printf 'workerThreads\tmaxConn\tpages\tseconds\tpages/sec\n'
    printf '%b' "$SWEEP_ROWS"; } | column -t -s $'\t'
  echo "    Pick the highest workerThreads where pages/sec still climbs and the app log"
  echo "    (/tmp/smoke-accept-app.log) stays free of 'Transient 5xx ... retrying' spam."
else
  info "2/2 ACCEPT_VERSIONS for hierarchy $TEST_PID (user=$INSTANCE_KEY, states=[ACTIVE], workerThreads=$WORKER_THREADS)"
  run_accept
  rate="$(pages_per_sec "$PROC" "$ELAPSED")"
  echo
  ok "SMOKE TEST PASSED — accept batch $ACCEPT_ID: $PROC pages in ${ELAPSED}s (${rate} pages/s)"
  echo "    Verify in dev Kramerius that ALTO/TEXT_OCR datastreams for pages under"
  echo "    $TEST_PID were (re)written, and that ancestor page counts look correct."
  echo "    Tip: SWEEP=\"7 16 32 48\" $0   # compare throughput across concurrency levels"
fi
