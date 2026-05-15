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
RUN_SECONDS="${EMU_RUN_SECONDS:-90}"
BASE_URL="${EMU_BASE_URL:-https://emu.devkeys.net}"
TOKEN="${EMU_API_TOKEN:-${EMU_TOKEN:-}}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/emu-run}"
POLL_TIMEOUT_S="${POLL_TIMEOUT_S:-300}"   # 5 min max queue+run
POLL_INTERVAL_S="${POLL_INTERVAL_S:-5}"
BAD_PATTERNS="${BAD_PATTERNS:-FATAL|ANR|CRASH|Exception|test.*fail|AssertionError}"
GITHUB_STEP_SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"
DOWNLOAD_SCREEN="${EMU_DOWNLOAD_SCREEN:-1}"
HEALTH_ONLY=0
MODE="run"
FETCH_JOB_ID=""
AUTH_PREFLIGHT="${EMU_AUTH_PREFLIGHT:-1}"

usage() {
  cat <<EOF
usage: scripts/emu-ci-run.sh [options] [apk_path [activity [run_seconds]]]

Runs a debug APK on ${BASE_URL}, then downloads logcat and screen recording.
Defaults are optimized for fast smoke checks:
  apk_path:    ${DEFAULT_APK_PATH}
  activity:    ${DEFAULT_ACTIVITY}
  run_seconds: 90

Options:
  --apk PATH             APK to upload (default: ${DEFAULT_APK_PATH})
  --activity NAME        Activity to launch (default: ${DEFAULT_ACTIVITY})
  --seconds N            Run window, 5-180 seconds (default: 90)
  --artifact-dir PATH    Output directory (default: artifacts/emu-run)
  --poll-timeout N       Queue+run timeout in seconds (default: 300)
  --poll-interval N      Poll interval in seconds (default: 5)
  --base-url URL         Emu service URL (default: https://emu.devkeys.net)
  --no-screen            Skip screen.mp4 download
  --health               Check service health only
  --list-jobs            Print the authenticated /jobs response and exit
  --job-id ID            Download artifacts for an existing job without rerunning
  --skip-auth-preflight  Skip the pre-upload /jobs authentication check
  -h, --help             Show this help

Environment:
  EMU_TOKEN or EMU_API_TOKEN is required for runs.

Examples:
  EMU_TOKEN=xxx ./scripts/emu-ci-run.sh
  EMU_TOKEN=xxx ./scripts/emu-ci-run.sh --seconds 90
  EMU_TOKEN=xxx ./scripts/emu-ci-run.sh --list-jobs
  EMU_TOKEN=xxx ./scripts/emu-ci-run.sh --job-id <job-id>
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
    --list-jobs)
      MODE="list-jobs"
      shift
      ;;
    --job-id)
      MODE="fetch-job"
      FETCH_JOB_ID="${2:-}"
      shift 2
      ;;
    --skip-auth-preflight)
      AUTH_PREFLIGHT=0
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
  echo "Example: EMU_TOKEN=xxx ./scripts/emu-ci-run.sh --seconds 90" >&2
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
if [[ "${MODE}" == "fetch-job" && -z "${FETCH_JOB_ID}" ]]; then
  echo "ERROR: --job-id requires a job id." >&2
  echo "Example: EMU_TOKEN=xxx ./scripts/emu-ci-run.sh --job-id <job-id>" >&2
  exit 64
fi

mkdir -p "$ARTIFACT_DIR"

echo "==> emu-ci-run: base_url=$BASE_URL apk=$APK_PATH activity=$ACTIVITY run_seconds=$RUN_SECONDS"

AUTH_HEADER=(-H "Authorization: Bearer $TOKEN")

require_token() {
  if [[ -z "${TOKEN}" ]]; then
    echo "ERROR: EMU_TOKEN or EMU_API_TOKEN is required." >&2
    echo "Example: EMU_TOKEN=xxx ./scripts/emu-ci-run.sh --seconds 90" >&2
    exit 64
  fi
}

json_field() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

path, field = sys.argv[1], sys.argv[2]
try:
    with open(path, "r", encoding="utf-8") as handle:
        value = json.load(handle)
except Exception:
    print("")
    raise SystemExit(0)
for part in field.split("."):
    if not isinstance(value, dict):
        print("")
        raise SystemExit(0)
    value = value.get(part)
print("" if value is None else value)
PY
}

print_json_file() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    print(json.dumps(json.load(handle), indent=2, sort_keys=True))
PY
}

preview_file() {
  python3 - "$1" <<'PY'
import sys

with open(sys.argv[1], "r", encoding="utf-8", errors="replace") as handle:
    print(handle.read().strip()[:500])
PY
}

request_json() {
  local path="$1"
  local output="$2"
  local http
  http=$(curl -sS --max-time 15 -o "$output" -w "%{http_code}" "${AUTH_HEADER[@]}" "$BASE_URL$path") || {
    echo "ERROR: request failed: $path" >&2
    exit 1
  }
  if [[ "$http" == "401" ]]; then
    echo "ERROR: emu authentication failed for $path: $(preview_file "$output")" >&2
    exit 77
  fi
  if [[ ! "$http" =~ ^2 ]]; then
    echo "ERROR: emu request failed for $path (http $http): $(preview_file "$output")" >&2
    exit 1
  fi
}

auth_preflight() {
  [[ "${AUTH_PREFLIGHT}" == "0" ]] && return 0
  local preflight_json="$ARTIFACT_DIR/auth-preflight.json"
  echo "==> Auth preflight: GET /jobs"
  request_json "/jobs" "$preflight_json"
}

download_artifacts() {
  local job_id="$1"
  JOB_DIR="$ARTIFACT_DIR/job-$job_id"
  mkdir -p "$JOB_DIR"
  request_json "/jobs/$job_id" "$JOB_DIR/job.json"
  STATUS="$(json_field "$JOB_DIR/job.json" "status")"
  echo "==> Downloading logs + screen (if present)"
  curl -sS --max-time 30 "${AUTH_HEADER[@]}" \
    "$BASE_URL/jobs/$job_id/logs" > "$JOB_DIR/logcat.txt" || true
  if [[ "${DOWNLOAD_SCREEN}" != "0" ]]; then
    curl -sS --max-time 60 "${AUTH_HEADER[@]}" \
      -o "$JOB_DIR/screen.mp4" \
      "$BASE_URL/jobs/$job_id/screen.mp4" 2>/dev/null || true
  fi
}

write_summary() {
  local job_id="$1"
  local status="$2"
  local job_dir="$3"
  python3 - "$job_dir/summary.json" "$BASE_URL" "$job_id" "$status" "$job_dir" "$APK_PATH" "$ACTIVITY" "$RUN_SECONDS" <<'PY'
import json
import os
import sys

summary_path, base_url, job_id, status, job_dir, apk_path, activity, run_seconds = sys.argv[1:]
summary = {
    "job_id": job_id,
    "job_url": f"{base_url}/jobs/{job_id}",
    "status": status,
    "artifact_dir": job_dir,
    "logcat": f"{job_dir}/logcat.txt",
    "screen": f"{job_dir}/screen.mp4" if os.path.exists(f"{job_dir}/screen.mp4") else None,
    "apk": apk_path,
    "activity": activity,
    "run_seconds": int(run_seconds),
}
with open(summary_path, "w", encoding="utf-8") as handle:
    json.dump(summary, handle, indent=2, sort_keys=True)
    handle.write("\n")
PY
}

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

if [[ "${MODE}" == "list-jobs" ]]; then
  require_token
  jobs_json="$ARTIFACT_DIR/jobs.json"
  request_json "/jobs" "$jobs_json"
  print_json_file "$jobs_json"
  echo "jobs_json=$jobs_json"
  exit 0
fi

if [[ "${MODE}" == "fetch-job" ]]; then
  require_token
  download_artifacts "$FETCH_JOB_ID"
  write_summary "$FETCH_JOB_ID" "$STATUS" "$JOB_DIR"
  echo "JOB_ID=$FETCH_JOB_ID"
  echo "job_url=$BASE_URL/jobs/$FETCH_JOB_ID"
  echo "status=$STATUS"
  echo "artifacts=$JOB_DIR"
  echo "summary=$JOB_DIR/summary.json"
  exit 0
fi

require_token
auth_preflight
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
download_artifacts "$JOB_ID"
write_summary "$JOB_ID" "$STATUS" "$JOB_DIR"

# 5. Analyze logs for bad patterns
if [ -s "$JOB_DIR/logcat.txt" ]; then
  BAD_PATTERN_SUMMARY=$(python3 - "$JOB_DIR/logcat.txt" "$BAD_PATTERNS" <<'PY'
import collections
import re
import sys

path, pattern = sys.argv[1], sys.argv[2]
text = open(path, "r", encoding="utf-8", errors="replace").read()
matches = re.findall(pattern, text, flags=re.IGNORECASE)
for match, count in collections.Counter(matches).most_common(5):
    print(f"{count:7d} {match}")
PY
)
  if [[ -n "$BAD_PATTERN_SUMMARY" ]]; then
    echo "ERROR: bad patterns detected in logcat:"
    echo "$BAD_PATTERN_SUMMARY"
    echo "::error::Crash/ANR/test failure detected in emu run (see artifacts)"
    exit 1
  fi
fi

# 6. Final status
if [ "$STATUS" = "failed" ]; then
  echo "ERROR: job failed (see $JOB_DIR/logcat.txt)"
  python3 - "$JOB_DIR/logcat.txt" <<'PY'
import sys

with open(sys.argv[1], "r", encoding="utf-8", errors="replace") as handle:
    lines = handle.readlines()
print("".join(lines[-30:]), end="")
PY
  exit 1
fi

echo "SUCCESS: emu run completed (job $JOB_ID, status=$STATUS)"
echo "artifacts: $JOB_DIR"
echo "summary: $JOB_DIR/summary.json"
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
