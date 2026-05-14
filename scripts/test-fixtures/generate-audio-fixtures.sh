#!/usr/bin/env bash
# Generate 16 kHz mono 16-bit PCM WAVs from a list of test phrases via macOS
# `say` + `afconvert`. Each phrase produces one file named after a slugified prefix.
#
# Output: scripts/test-fixtures/audio/*.wav
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="$SCRIPT_DIR/audio"
mkdir -p "$OUT_DIR"

# slug | voice | phrase
phrases=(
  "timer_ten_minutes|Samantha|Set a timer for ten minutes."
  "define_polymorphism|Samantha|Define polymorphism."
  "add_oat_milk|Samantha|Add oat milk to my list."
  "remind_call_dentist|Samantha|Remind me to call the dentist tomorrow."
  "meaning_foreshadowing|Samantha|What is the meaning of foreshadowing?"
  "schedule_meeting_avery|Samantha|Schedule a meeting with Avery on Tuesday at three."
  "send_email_client|Samantha|Send an email to the client about the renewal."
  "play_music|Samantha|Play music."
  "key_insight_meeting|Samantha|Key insight from the meeting was customer churn dropped."
  "navigate_amphitheatre|Samantha|Navigate to sixteen hundred Amphitheatre Parkway."
)

if ! command -v say >/dev/null 2>&1 || ! command -v afconvert >/dev/null 2>&1; then
  echo "say + afconvert required (macOS)" >&2
  exit 64
fi

for entry in "${phrases[@]}"; do
  IFS='|' read -r slug voice phrase <<<"$entry"
  aiff="$OUT_DIR/$slug.aiff"
  wav="$OUT_DIR/$slug.wav"
  say -v "$voice" -o "$aiff" -- "$phrase"
  # 16 kHz mono 16-bit PCM little-endian, RIFF WAV container
  afconvert -f WAVE -d LEI16@16000 -c 1 "$aiff" "$wav"
  rm -f "$aiff"
  size=$(wc -c <"$wav" | tr -d ' ')
  printf '  %-30s %10d bytes  %s\n' "$slug.wav" "$size" "$phrase"
done

# Also emit a synthetic 1 kHz tone burst as a JNI-only smoke fixture (no STT
# expected — purely exercises the TFLite + Darwinn audio gatekeeper path on
# arbitrary PCM).
python3 - "$OUT_DIR/sine_1khz_3s.wav" <<'PY'
import math, struct, sys, wave
out = sys.argv[1]
sr = 16_000
duration_s = 3.0
freq = 1_000.0
amp = 0.4
n = int(sr * duration_s)
with wave.open(out, "wb") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(sr)
    for i in range(n):
        s = int(amp * 32767 * math.sin(2 * math.pi * freq * i / sr))
        w.writeframesraw(struct.pack("<h", s))
PY

echo
echo "Audio fixtures written to: $OUT_DIR"
ls -1 "$OUT_DIR"
