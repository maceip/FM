#!/usr/bin/env bash
# emu-ci-run.sh — wired integration for emu-provision (emu.devkeys.net)
# Usage (local or CI):
#   EMU_API_TOKEN=xxx ./scripts/emu-ci-run.sh app/build/outputs/apk/debug/app-debug.apk "com.google.research.air.cosmo/.ui.mainscreen.MainActivity" 90
# On success: downloads logs + screen.mp4 to artifacts/emu-<jobid>/
# Fails CI on job failed, crash/ANR in logs, or timeout.
# Optimized for Github Actions (no jq dep; uses python for JSON; artifacts via $GITHUB_STEP_SUMMARY).
set -euo pipefail

APK_PATH="${1:?APK path required}"
ACTIVITY="${2:-}"
RUN_SECONDS="${3:-60}"
BASE_URL="https://emu.devkeys.net"
TOKEN="${EMU_API_TOKEN:?EMU_API_TOKEN secret required (set in repo secrets or env)}"
ARTIFACT_DIR="${ARTIFACT_DIR:-artifacts/emu-run}"
POLL_TIMEOUT_S="${POLL_TIMEOUT_S:-300}"   # 5 min max queue+run
BAD_PATTERNS="${BAD_PATTERNS:-FATAL|ANR|CRASH|Exception|test.*fail|AssertionError}"
GITHUB_STEP_SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"

mkdir -p "$ARTIFACT_DIR"

echo "==> emu-ci-run: APK=$APK_PATH activity=$ACTIVITY run_seconds=$RUN_SECONDS"

# 1. Health check (no auth)
HEALTH=$(curl -sS --max-time 10 "$BASE_URL/healthz" || echo '{"ok":false}')
EMULATOR_READY=$(python3 -c "
import json,sys
h=json.loads(sys.argv[1])
print(h.get('emulator_ready', False))
" "$HEALTH" 2>/dev/null || echo false)
if [ "$EMULATOR_READY" != "true" ]; then
  echo "WARNING: emulator not ready (queue or cold start). Proceeding anyway."
fi

# 2. Submit job (robust to slow uploads: longer timeout + connect timeout + retry on 28)
echo "==> POST /run (APK may be large; allowing up to 300s upload)"
for attempt in 1 2 3; do
  JOB_JSON=$(curl -sS --connect-timeout 30 --max-time 300 \
    -H "Authorization: Bearer $TOKEN" \
    -F "apk=@$APK_PATH" \
    -F "run_seconds=$RUN_SECONDS" \
    ${ACTIVITY:+-F "activity=$ACTIVITY"} \
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
    *) sleep 5 ;;
  esac
done

# 4. Download artifacts
JOB_DIR="$ARTIFACT_DIR/job-$JOB_ID"
mkdir -p "$JOB_DIR"
echo "==> Downloading logs + screen (if present)"
curl -sS -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/jobs/$JOB_ID/logs" > "$JOB_DIR/logcat.txt" || true
curl -sS -H "Authorization: Bearer $TOKEN" \
  -o "$JOB_DIR/screen.mp4" \
  "$BASE_URL/jobs/$JOB_ID/screen.mp4" 2>/dev/null || true

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
  echo "- Screen: $(ls -lh "$JOB_DIR/screen.mp4" 2>/dev/null | awk '{print $5}' || echo 'none')"
} >> "$GITHUB_STEP_SUMMARY" 2>/dev/null || true

exit 0
