#!/usr/bin/env bash
#
# smoke-tuzka.sh — end-to-end smoke test for the native tuzka-as-a-service OCR
# engine (the TUZKA OcrEngine) against a *dev* Kramerius (for page images) and a
# taas instance (for OCR).
#
# What it does
#   1. (optional) builds the app jar and boots it with the `tuzka` engine
#      configured (baseUrl + api key) and a throwaway Postgres
#   2. RETRIEVE_HIERARCHY for a test PID  -> mirrors the hierarchy locally so the
#      page DigitalObjects exist and page images are fetchable from Kramerius
#   3. GENERATE (whole hierarchy or a single page) with engine=tuzka -> for each
#      page: submit the image to taas, await the WS completion event, download the
#      ALTO, and store it as a PENDING version
#   4. polls the batch, then counts the PENDING tuzka versions that were created
#
# This exercises the exact wire behaviour I could not verify without a live taas:
# WS auth (?api_key=), the done/failed event shape, and the /result/{fmt}/download
# response. Watch /tmp/smoke-tuzka-app.log for "taas" lines if a page hangs.
#
# You are prompted for all sensitive values at runtime; secrets go to the app via
# env vars (never as process args, never written to disk).
#
# Requirements: bash, curl, jq, Java 21, docker (throwaway Postgres; not needed with
# --no-app), a Maven settings.xml with a GitHub Packages PAT (read:packages).
#
# Usage:
#   scripts/smoke-tuzka.sh                 # build + boot the app, then run the flow
#   scripts/smoke-tuzka.sh --no-app        # app already running; just drive the API
#   scripts/smoke-tuzka.sh --keep-app      # leave the app running after the test
#
# Config (override via env before running):
#   MODE=hierarchy|single                  # generate whole subtree (default) or one page
#   SCOPE=ALL|NO_PENDING|NO_PENDING_NOR_ACTIVE   # hierarchy generate scope; default ALL
#   ENGINE=tuzka                           # engine key to test; default tuzka
#   ALTO_EDITOR_TUZKA_URL / ALTO_EDITOR_TUZKA_KEY   # taas base URL + user api key
#
set -euo pipefail

# --------------------------------------------------------------------------- #
# Config (non-sensitive; override via env before running)
# --------------------------------------------------------------------------- #
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"
APP_URL="${APP_URL:-http://localhost:8080}"
INSTANCE_KEY="${INSTANCE_KEY:-k7-mzk}"        # key under application.kramerius-instances
ENGINE="${ENGINE:-tuzka}"                     # key under application.engines (type: TUZKA)
MODE="${MODE:-hierarchy}"                     # hierarchy | single
SCOPE="${SCOPE:-ALL}"                         # generate scope (hierarchy mode)
STORE_PATH="${ALTO_EDITOR_STORE_PATH:-/tmp/altoEditorTuzkaStore}"
INDEX_PATH="${ALTO_EDITOR_INDEX_DIRECTORY:-/tmp/altoEditorTuzkaIndex}"
POLL_INTERVAL="${POLL_INTERVAL:-3}"
POLL_TIMEOUT="${POLL_TIMEOUT:-1800}"

# Postgres: the app uses PostgreSQL-specific SQL (e.g. `ON CONFLICT`) H2 can't parse.
PG_IMAGE="${PG_IMAGE:-postgres:16-alpine}"
PG_CONTAINER="${PG_CONTAINER:-altoeditor-tuzka-pg}"
PG_PORT="${PG_PORT:-55433}"
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

case "$MODE" in hierarchy|single) ;; *) echo "MODE must be hierarchy|single" >&2; exit 2 ;; esac

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

info "Enter environment details (leave app/taas fields blank if using --no-app)"

DEV_KRAMERIUS_URL="${DEV_KRAMERIUS_URL:-}"
TEST_PID="${TEST_PID:-}"
CURATOR_TOKEN="${CURATOR_TOKEN:-}"
CLIENT_ID="${ALTO_EDITOR_SERVICE_CLIENT_ID:-}"
CLIENT_SECRET="${ALTO_EDITOR_SERVICE_SECRET:-}"
TUZKA_URL="${ALTO_EDITOR_TUZKA_URL:-}"
TUZKA_KEY="${ALTO_EDITOR_TUZKA_KEY:-}"

[ -n "$DEV_KRAMERIUS_URL" ] || DEV_KRAMERIUS_URL="$(prompt 'Dev Kramerius base URL (e.g. https://k7-dev.mzk.cz/): ')"
[ -n "$TEST_PID" ]          || TEST_PID="$(prompt "Test PID (uuid:...; ${MODE} root/page): ")"
[ -n "$CURATOR_TOKEN" ]     || CURATOR_TOKEN="$(prompt_secret 'CURATOR bearer token (from dev Kramerius login): ')"

[ -n "$DEV_KRAMERIUS_URL" ] || die "dev Kramerius URL is required"
[ -n "$TEST_PID" ]          || die "test PID is required"
[ -n "$CURATOR_TOKEN" ]     || die "CURATOR token is required"
case "$DEV_KRAMERIUS_URL" in
  *api.kramerius.mzk.cz*) die "refusing to run against production ($DEV_KRAMERIUS_URL)" ;;
esac

if [ "$START_APP" -eq 1 ]; then
  [ -n "$CLIENT_ID" ]     || CLIENT_ID="$(prompt_secret 'Kramerius service client id (read images): ')"
  [ -n "$CLIENT_SECRET" ] || CLIENT_SECRET="$(prompt_secret 'Kramerius service secret: ')"
  [ -n "$TUZKA_URL" ]     || TUZKA_URL="$(prompt 'taas base URL (e.g. https://taas-dev.mzk.cz): ')"
  [ -n "$TUZKA_KEY" ]     || TUZKA_KEY="$(prompt_secret 'taas user API key: ')"
  [ -n "$CLIENT_ID" ]     || die "service client id is required to boot the app"
  [ -n "$CLIENT_SECRET" ] || die "service secret is required to boot the app"
  [ -n "$TUZKA_URL" ]     || die "taas base URL is required to boot the app"
  [ -n "$TUZKA_KEY" ]     || die "taas api key is required to boot the app"
fi

AUTH_HDR="Authorization: Bearer ${CURATOR_TOKEN}"

# --------------------------------------------------------------------------- #
# App lifecycle
# --------------------------------------------------------------------------- #
APP_PID=""
PG_STARTED=0
JAR=""
cleanup() {
  if [ -n "$APP_PID" ] && [ "$KEEP_APP" -eq 0 ]; then
    info "Stopping app (pid $APP_PID)"; kill "$APP_PID" 2>/dev/null || true; wait "$APP_PID" 2>/dev/null || true
  fi
  if [ "$PG_STARTED" -eq 1 ] && [ "$KEEP_APP" -eq 0 ]; then
    info "Removing Postgres container ($PG_CONTAINER)"; docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

wait_app_ready() {
  info "Waiting for $APP_URL/api/system/info"
  for i in $(seq 1 60); do
    # If we launched the app, make sure OUR process is still alive — don't accept a
    # health response from some other instance while ours has already died.
    if [ -n "$APP_PID" ] && ! kill -0 "$APP_PID" 2>/dev/null; then
      tail -40 /tmp/smoke-tuzka-app.log 2>/dev/null; tail -40 logs/allLogs.log 2>/dev/null
      die "app process $APP_PID exited during startup (see /tmp/smoke-tuzka-app.log and ./logs/allLogs.log)"
    fi
    if curl -fsS "$APP_URL/api/system/info" >/dev/null 2>&1; then ok "app is up"; return 0; fi
    [ "$i" -eq 60 ] && die "app did not become ready (see /tmp/smoke-tuzka-app.log and ./logs/allLogs.log)"
    sleep 2
  done
}

start_app() {
  info "Booting app (Kramerius=$DEV_KRAMERIUS_URL, taas=$TUZKA_URL, engine=$ENGINE, db: Postgres@$PG_PORT)"
  mkdir -p "$STORE_PATH" "$INDEX_PATH"
  ALTO_EDITOR_SERVICE_CLIENT_ID="$CLIENT_ID" \
  ALTO_EDITOR_SERVICE_SECRET="$CLIENT_SECRET" \
  ALTO_EDITOR_TUZKA_URL="$TUZKA_URL" \
  ALTO_EDITOR_TUZKA_KEY="$TUZKA_KEY" \
  ALTO_EDITOR_STORE_PATH="$STORE_PATH" \
  ALTO_EDITOR_INDEX_DIRECTORY="$INDEX_PATH" \
  ALTO_EDITOR_JDBC_DRIVER="org.postgresql.Driver" \
  ALTO_EDITOR_JDBC_URL="jdbc:postgresql://localhost:${PG_PORT}/${PG_DB}" \
  ALTO_EDITOR_JDBC_USERNAME="$PG_USER" \
  ALTO_EDITOR_JDBC_PASSWORD="$PG_PASSWORD" \
  "$JAVA_HOME/bin/java" -Daltoeditor.home="$ROOT_DIR" -jar "$JAR" \
    --application.kramerius-instances.${INSTANCE_KEY}.url="$DEV_KRAMERIUS_URL" \
    >/tmp/smoke-tuzka-app.log 2>&1 &
  APP_PID=$!
  ok "app pid $APP_PID (logs: /tmp/smoke-tuzka-app.log)"
  wait_app_ready
}

if [ "$START_APP" -eq 1 ]; then
  # Refuse to run if something is already serving on APP_URL — otherwise the health
  # check and the whole test would hit that stale instance (a classic source of a
  # confusing 405 on POST /api/users/me from an older build), not the app we build here.
  if curl -fsS "$APP_URL/api/system/info" >/dev/null 2>&1; then
    die "something is already serving $APP_URL — stop it first, or use --no-app to drive it deliberately"
  fi

  export JAVA_HOME
  info "Building jar (mvn package -DskipTests)"
  mvn -q package -DskipTests >/tmp/smoke-tuzka-build.log 2>&1 || {
    tail -30 /tmp/smoke-tuzka-build.log; die "build failed (see /tmp/smoke-tuzka-build.log)";
  }
  JAR="$(ls -t target/*.jar | grep -v '\.original$' | head -1)"
  [ -n "$JAR" ] || die "no jar produced in target/"
  ok "built $JAR"

  info "Starting Postgres container ($PG_IMAGE) on host port $PG_PORT"
  docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
  docker run -d --name "$PG_CONTAINER" \
    -e POSTGRES_DB="$PG_DB" -e POSTGRES_USER="$PG_USER" -e POSTGRES_PASSWORD="$PG_PASSWORD" \
    -p "${PG_PORT}:5432" "$PG_IMAGE" >/dev/null || die "failed to start Postgres container"
  PG_STARTED=1
  info "Waiting for Postgres to accept connections"
  for i in $(seq 1 30); do
    if docker exec "$PG_CONTAINER" pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1; then ok "Postgres is ready"; break; fi
    [ "$i" -eq 30 ] && die "Postgres did not become ready"
    sleep 1
  done

  info "Resetting store + index for a clean run ($STORE_PATH, $INDEX_PATH)"
  rm -rf "$STORE_PATH" "$INDEX_PATH"
  start_app
else
  wait_app_ready
fi

# --------------------------------------------------------------------------- #
# Ensure the CURATOR user has a local profile (needed for batch ownership).
# --------------------------------------------------------------------------- #
info "Ensuring local user profile exists (POST /api/users/me)"
ME_RESP="$(curl -fsS -X POST -H "$AUTH_HDR" "$APP_URL/api/users/me")" || die "failed to ensure current user profile"
ok "user profile: $(jq -rc '{id, username, roles}' <<<"$ME_RESP" 2>/dev/null || echo "$ME_RESP")"

# --------------------------------------------------------------------------- #
# poll_batch <id> <label> — waits until DONE, dies on FAILED/timeout.
# --------------------------------------------------------------------------- #
LAST_PROC=0; LAST_EST=0
poll_batch() {
  local id="$1" label="$2" waited=0 state est proc body
  while :; do
    body="$(curl -fsS -H "$AUTH_HDR" "$APP_URL/api/batches/${id}")" || die "$label: batch $id not found"
    state="$(jq -r '.state' <<<"$body")"
    est="$(jq -r '.estimatedItemCount // 0' <<<"$body")"
    proc="$(jq -r '.processedItemCount // 0' <<<"$body")"
    printf '\r    %s: state=%-8s %s/%s (%ss)   ' "$label" "$state" "$proc" "$est" "$waited"
    case "$state" in
      DONE)   echo; LAST_PROC="$proc"; LAST_EST="$est"; ok "$label done ($proc/$est)"; return 0 ;;
      FAILED) echo; jq -r '.log // "no log"' <<<"$body"; die "$label FAILED (see /tmp/smoke-tuzka-app.log)" ;;
    esac
    sleep "$POLL_INTERVAL"; waited=$((waited + POLL_INTERVAL))
    [ "$waited" -ge "$POLL_TIMEOUT" ] && { echo; die "$label timed out after ${POLL_TIMEOUT}s"; }
  done
}

# --------------------------------------------------------------------------- #
# 1) RETRIEVE_HIERARCHY — seed local DigitalObjects so pages/images are available.
# --------------------------------------------------------------------------- #
info "1/2 RETRIEVE_HIERARCHY for $TEST_PID"
RETRIEVE_RESP="$(curl -fsS -X POST -H "$AUTH_HDR" \
  "$APP_URL/api/hierarchy/${TEST_PID}/fetch-from-kramerius?instance=${INSTANCE_KEY}")"
RETRIEVE_ID="$(jq -r '.id' <<<"$RETRIEVE_RESP")"
[ "$RETRIEVE_ID" != "null" ] || die "retrieve batch not created: $RETRIEVE_RESP"
ok "retrieve batch id=$RETRIEVE_ID"
poll_batch "$RETRIEVE_ID" "retrieve"

# --------------------------------------------------------------------------- #
# 2) GENERATE with the tuzka engine (the code under test).
# --------------------------------------------------------------------------- #
start_ts="$(date +%s)"
if [ "$MODE" = "single" ]; then
  info "2/2 GENERATE_SINGLE for page $TEST_PID via engine=$ENGINE"
  GEN_RESP="$(curl -fsS -X POST -H "$AUTH_HDR" \
    "$APP_URL/api/alto-versions/${TEST_PID}/generate/${ENGINE}?instance=${INSTANCE_KEY}")"
else
  info "2/2 GENERATE_FOR_HIERARCHY for $TEST_PID via engine=$ENGINE (scope=$SCOPE)"
  GEN_RESP="$(curl -fsS -X POST -H "$AUTH_HDR" \
    "$APP_URL/api/hierarchy/${TEST_PID}/generate-alto/${ENGINE}?instance=${INSTANCE_KEY}&scope=${SCOPE}")"
fi
GEN_ID="$(jq -r '.id' <<<"$GEN_RESP")"
[ "$GEN_ID" != "null" ] || die "generate batch not created: $GEN_RESP"
ok "generate batch id=$GEN_ID"
poll_batch "$GEN_ID" "generate"
ELAPSED=$(( $(date +%s) - start_ts ))

# --------------------------------------------------------------------------- #
# Verify: PENDING versions owned by the tuzka engine user under this hierarchy.
# --------------------------------------------------------------------------- #
TUZKA_UID="$(curl -fsS -H "$AUTH_HDR" "$APP_URL/api/users?size=500" \
  | jq -r --arg u "$ENGINE" 'first(.content[] | select(.username==$u) | .id) // empty')"
PENDING_TOTAL="?"
if [ -n "$TUZKA_UID" ]; then
  PENDING_TOTAL="$(curl -fsS -H "$AUTH_HDR" \
    "$APP_URL/api/alto-versions/search?hierarchyPid=${TEST_PID}&states=PENDING&users=${TUZKA_UID}&limit=1" \
    | jq -r '.total // "?"')"
fi

echo
ok "SMOKE TEST PASSED — generate batch $GEN_ID: $LAST_PROC/$LAST_EST pages in ${ELAPSED}s"
echo "    PENDING tuzka versions under $TEST_PID: $PENDING_TOTAL"
echo "    Inspect a result: GET $APP_URL/api/alto-versions/search?hierarchyPid=$TEST_PID&states=PENDING&users=$TUZKA_UID"
echo "    taas wire activity: grep -i taas /tmp/smoke-tuzka-app.log"
