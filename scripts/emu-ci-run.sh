#!/usr/bin/env bash
# emu-ci-run.sh — wired integration for emu-provision (emu.devkeys.net)
# Fast path (local or CI):
#   EMU_TOKEN=xxx ./scripts/emu-ci-run.sh
# Backward-compatible positional form:
#   EMU_API_TOKEN=xxx ./scripts/emu-ci-run.sh app/build/outputs/apk/debug/app-debug.apk "com.google.research.air.cosmo/.ui.mainscreen.MainActivity" 90
# On success: downloads logs + screen.mp4 to artifacts/emu-<jobid>/
# Fails CI on job failed, crash/ANR in logs, or timeout.
# Optimized for Github Actions (no jq dep; uses python for JSON; artifacts via $GITHUB_STEP_SUMMARY).
set -euo pipefail

DEFAULT_APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
DEFAULT_ACTIVITY="com.google.research.air.cosmo/.ui.mainscreen.MainActivity"
APK_PATH="${EMU_APK_PATH:-${DEFAULT_APK_PATH}}"
ACTIVITY="${EMU_ACTIVITY:-${DEFAULT_ACTIVITY}}"
RUN_SECONDS="${EMU_RUN_SECONDS:-30}"
BASE_URL="${EMU_BASE_URL:-https://emu.devkeys.net}"
TOKEN="${EMU_API_TOKEN:-${EMU_TOKEN:-}}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/emu-run}"
POLL_TIMEOUT_S="${POLL_TIMEOUT_S:-300}"   # 5 min max queue+run
POLL_INTERVAL_S="${POLL_INTERVAL_S:-5}"
BAD_PATTERNS="${BAD_PATTERNS:-FATAL|ANR|CRASH|Exception|test.*fail|AssertionError}"
GITHUB_STEP_SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"
DOWNLOAD_SCREEN="${EMU_DOWNLOAD_SCREEN:-1}"
HEALTH_ONLY=0

usage() {
  cat <<EOF
usage: scripts/emu-ci-run.sh [options] [apk_path [activity [run_seconds]]]

Runs a debug APK on ${BASE_URL}, then downloads logcat and screen recording.
Defaults are optimized for fast smoke checks:
  apk_path:    ${DEFAULT_APK_PATH}
  activity:    ${DEFAULT_ACTIVITY}
  run_seconds: 30

Options:
  --apk PATH             APK to upload (default: ${DEFAULT_APK_PATH})
  --activity NAME        Activity to launch (default: ${DEFAULT_ACTIVITY})
  --seconds N            Run window, 5-180 seconds (default: 30)
  --artifact-dir PATH    Output directory (default: artifacts/emu-run)
  --poll-timeout N       Queue+run timeout in seconds (default: 300)
  --poll-interval N      Poll interval in seconds (default: 5)
  --base-url URL         Emu service URL (default: https://emu.devkeys.net)
  --no-screen            Skip screen.mp4 download
  --health               Check service health only
  -h, --help             Show this help

Environment:
  EMU_TOKEN or EMU_API_TOKEN is required for runs.

Examples:
  EMU_TOKEN=xxx ./scripts/emu-ci-run.sh
  EMU_TOKEN=xxx ./scripts/emu-ci-run.sh --seconds 90
  EMU_TOKEN=xxx ./scripts/emu-ci-run.sh --apk app/build/outputs/apk/debug/app-debug.apk --activity "${DEFAULT_ACTIVITY}"
  ./scripts/emu-ci-run.sh --health
EOF
}

POSITIONAL=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --apk)
      APK_PATH="${2:-}"
      shift 2
      ;;
    --activity)
      ACTIVITY="${2:-}"
      shift 2
      ;;
    --seconds | --run-seconds)
      RUN_SECONDS="${2:-}"
      shift 2
      ;;
    --artifact-dir)
      ARTIFACT_DIR="${2:-}"
      shift 2
      ;;
    --poll-timeout)
      POLL_TIMEOUT_S="${2:-}"
      shift 2
      ;;
    --poll-interval)
      POLL_INTERVAL_S="${2:-}"
      shift 2
      ;;
    --base-url)
      BASE_URL="${2:-}"
      shift 2
      ;;
    --no-screen)
      DOWNLOAD_SCREEN=0
      shift
      ;;
    --health)
      HEALTH_ONLY=1
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    --)
      shift
      while [[ $# -gt 0 ]]; do
        POSITIONAL+=("$1")
        shift
      done
      ;;
    -*)
      echo "ERROR: unknown option: $1" >&2
      usage >&2
      exit 64
      ;;
    *)
      POSITIONAL+=("$1")
      shift
      ;;
  esac
done

if (( ${#POSITIONAL[@]} > 3 )); then
  echo "ERROR: too many positional arguments." >&2
  usage >&2
  exit 64
fi
if (( ${#POSITIONAL[@]} >= 1 )); then
  APK_PATH="${POSITIONAL[0]}"
fi
if (( ${#POSITIONAL[@]} >= 2 )); then
  ACTIVITY="${POSITIONAL[1]}"
fi
if (( ${#POSITIONAL[@]} >= 3 )); then
  RUN_SECONDS="${POSITIONAL[2]}"
fi

if ! [[ "${RUN_SECONDS}" =~ ^[0-9]+$ ]] || (( RUN_SECONDS < 5 || RUN_SECONDS > 180 )); then
  echo "ERROR: --seconds must be an integer between 5 and 180; got '${RUN_SECONDS}'." >&2
  echo "Example: EMU_TOKEN=xxx ./scripts/emu-ci-run.sh --seconds 30" >&2
  exit 64
fi
if ! [[ "${POLL_TIMEOUT_S}" =~ ^[0-9]+$ ]] || (( POLL_TIMEOUT_S < 1 )); then
  echo "ERROR: --poll-timeout must be a positive integer; got '${POLL_TIMEOUT_S}'." >&2
  exit 64
fi
if ! [[ "${POLL_INTERVAL_S}" =~ ^[0-9]+$ ]] || (( POLL_INTERVAL_S < 1 )); then
  echo "ERROR: --poll-interval must be a positive integer; got '${POLL_INTERVAL_S}'." >&2
  exit 64
fi

mkdir -p "$ARTIFACT_DIR"

echo "==> emu-ci-run: base_url=$BASE_URL apk=$APK_PATH activity=$ACTIVITY run_seconds=$RUN_SECONDS"

# 1. Health check (no auth)
HEALTH=$(curl -sS --max-time 10 "$BASE_URL/healthz" || echo '{"ok":false}')
echo "health=$HEALTH"
EMULATOR_READY=$(python3 -c "
import json,sys
h=json.loads(sys.argv[1])
print(str(h.get('emulator_ready', False)).lower())
" "$HEALTH" 2>/dev/null || echo false)
if [ "$EMULATOR_READY" != "true" ]; then
  echo "WARNING: emulator not ready (queue or cold start). Proceeding anyway."
fi
if [[ "${HEALTH_ONLY}" -eq 1 ]]; then
  exit 0
fi
if [[ -z "${TOKEN}" ]]; then
  echo "ERROR: EMU_TOKEN or EMU_API_TOKEN is required." >&2
  echo "Example: EMU_TOKEN=xxx ./scripts/emu-ci-run.sh --seconds 30" >&2
  exit 64
fi
if [[ ! -f "${APK_PATH}" ]]; then
  echo "ERROR: APK not found: ${APK_PATH}" >&2
  echo "Build it first: ./gradlew :app:assembleDebug" >&2
  exit 66
fi

# 2. Submit job (robust to slow uploads: longer timeout + connect timeout + retry on 28)
echo "==> POST /run (APK may be large; allowing up to 300s upload)"
curl_form_args=(-F "apk=@${APK_PATH}" -F "run_seconds=${RUN_SECONDS}")
if [[ -n "${ACTIVITY}" ]]; then
  curl_form_args+=(-F "activity=${ACTIVITY}")
fi
for attempt in 1 2 3; do
  JOB_JSON=$(curl -sS --connect-timeout 30 --max-time 300 \
    -H "Authorization: Bearer $TOKEN" \
    "${curl_form_args[@]}" \
    "$BASE_URL/run" 2>&1) && break || {
      echo "  attempt $attempt failed (timeout or error); retrying..."
      sleep 5
    }
done

JOB_ID=$(python3 -c "
import json,sys
j=json.loads(sys.argv[1])
print(j.get('job_id',''))
" "$JOB_JSON" 2>/dev/null || echo "")

if [ -z "$JOB_ID" ]; then
  echo "ERROR: no job_id in response: $JOB_JSON"
  exit 1
fi
echo "JOB_ID=$JOB_ID"
echo "job_url=$BASE_URL/jobs/$JOB_ID" >> "$GITHUB_STEP_SUMMARY" 2>/dev/null || true

# 3. Poll until completed|failed
echo "==> Polling job (timeout ${POLL_TIMEOUT_S}s)"
START=$(date +%s)
STATUS="queued"
while true; do
  NOW=$(date +%s)
  if (( NOW - START > POLL_TIMEOUT_S )); then
    echo "ERROR: poll timeout after ${POLL_TIMEOUT_S}s"
    exit 1
  fi
  JOB=$(curl -sS --max-time 15 -H "Authorization: Bearer $TOKEN" "$BASE_URL/jobs/$JOB_ID")
  STATUS=$(python3 -c "
import json,sys
j=json.loads(sys.argv[1])
print(j.get('status','unknown'))
" "$JOB" 2>/dev/null || echo unknown)
  echo "  status=$STATUS ($(date +%T))"
  case "$STATUS" in
    completed|failed) break ;;
    *) sleep "$POLL_INTERVAL_S" ;;
  esac
done

# 4. Download artifacts
JOB_DIR="$ARTIFACT_DIR/job-$JOB_ID"
mkdir -p "$JOB_DIR"
echo "==> Downloading logs + screen (if present)"
curl -sS -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/jobs/$JOB_ID/logs" > "$JOB_DIR/logcat.txt" || true
if [[ "${DOWNLOAD_SCREEN}" != "0" ]]; then
  curl -sS -H "Authorization: Bearer $TOKEN" \
    -o "$JOB_DIR/screen.mp4" \
    "$BASE_URL/jobs/$JOB_ID/screen.mp4" 2>/dev/null || true
fi

# 5. Analyze logs for bad patterns
if [ -s "$JOB_DIR/logcat.txt" ]; then
  if grep -Eiq "$BAD_PATTERNS" "$JOB_DIR/logcat.txt"; then
    echo "ERROR: bad patterns detected in logcat:"
    grep -Eio "$BAD_PATTERNS" "$JOB_DIR/logcat.txt" | sort | uniq -c | head -5
    echo "::error::Crash/ANR/test failure detected in emu run (see artifacts)"
    exit 1
  fi
fi

# 6. Final status
if [ "$STATUS" = "failed" ]; then
  echo "ERROR: job failed (see $JOB_DIR/logcat.txt)"
  cat "$JOB_DIR/logcat.txt" | tail -30
  exit 1
fi

echo "SUCCESS: emu run completed (job $JOB_ID, status=$STATUS)"
echo "artifacts: $JOB_DIR"
ls -l "$JOB_DIR"

# For CI summary
{
  echo "### emu.devkeys.net Run"
  echo "- Job: [$JOB_ID]($BASE_URL/jobs/$JOB_ID)"
  echo "- Status: $STATUS"
  echo "- Logcat: $(ls -lh "$JOB_DIR/logcat.txt" | awk '{print $5}')"
  if [[ -f "$JOB_DIR/screen.mp4" ]]; then
    echo "- Screen: $(ls -lh "$JOB_DIR/screen.mp4" | awk '{print $5}')"
  else
    echo "- Screen: none"
  fi
} >> "$GITHUB_STEP_SUMMARY" 2>/dev/null || true

exit 0
