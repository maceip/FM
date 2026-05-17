#!/usr/bin/env python3
"""Validate a GatekeeperDebugReceiver audio fixture logcat capture."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

BAD_PATTERNS = (
    "FATAL EXCEPTION",
    "UnsatisfiedLinkError",
    "soda_native_load_failed",
    "Process crashed",
    "Application Not Responding",
    "ANR in ",
    "signal 4 (SIGILL)",
)

TRANSCRIPT_RE = re.compile(r"audio inject soda transcript: name=(\S+) isFinal=true text=(.*)$")
GATEKEEPER_RE = re.compile(r"audio inject soda gatekeeper: name=(\S+) result=.*detectedSkills=\{([^}]*)\}")
AMBIENT_RE = re.compile(r"audio inject ambient: name=(\S+) result=(.*)$")


def fixture_name(fixture: dict) -> str:
    return Path(fixture["file"]).name


def parse_skills(raw: str) -> set[str]:
    if not raw.strip():
        return set()
    skills: set[str] = set()
    for item in raw.split(","):
        key = item.strip().split("=", 1)[0].strip()
        if key:
            skills.add(key)
    return skills


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logcat", type=Path)
    parser.add_argument("--index", type=Path, default=Path(__file__).with_name("gatekeeper-utterances.json"))
    parser.add_argument("--target", choices=("ambient", "soda", "both"), default="both")
    args = parser.parse_args()

    text = args.logcat.read_text(encoding="utf-8", errors="replace")
    index = json.loads(args.index.read_text(encoding="utf-8"))
    fixtures = index["audio_fixtures"]

    failures: list[str] = []
    for pattern in BAD_PATTERNS:
        if pattern in text:
            failures.append(f"log contains fatal marker: {pattern}")

    transcripts: dict[str, list[str]] = {}
    gatekeeper_skills: dict[str, set[str]] = {}
    ambient_results: dict[str, str] = {}

    for line in text.splitlines():
        transcript = TRANSCRIPT_RE.search(line)
        if transcript:
            name, body = transcript.group(1), transcript.group(2).strip()
            if body:
                transcripts.setdefault(name, []).append(body)
            continue

        gatekeeper = GATEKEEPER_RE.search(line)
        if gatekeeper:
            name, raw_skills = gatekeeper.group(1), gatekeeper.group(2)
            gatekeeper_skills.setdefault(name, set()).update(parse_skills(raw_skills))
            continue

        ambient = AMBIENT_RE.search(line)
        if ambient:
            ambient_results[ambient.group(1)] = ambient.group(2)

    if args.target in {"soda", "both"}:
        for fixture in fixtures:
            name = fixture_name(fixture)
            expected_skill = fixture.get("expected_skill_via_soda_then_regex")
            skills = gatekeeper_skills.get(name, set())

            if expected_skill is None:
                if skills:
                    failures.append(f"{name}: expected no SODA skill, saw {sorted(skills)}")
                continue

            if not transcripts.get(name):
                failures.append(f"{name}: missing non-empty final SODA transcript")
            if expected_skill not in skills:
                failures.append(f"{name}: missing expected skill {expected_skill}; saw {sorted(skills)}")

    if args.target in {"ambient", "both"}:
        for fixture in fixtures:
            name = fixture_name(fixture)
            if name not in ambient_results:
                failures.append(f"{name}: missing ambient gatekeeper result")

    if failures:
        print("Audio fixture validation failed:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1

    print(
        "Audio fixture validation passed: "
        f"target={args.target} transcripts={sum(len(v) for v in transcripts.values())} "
        f"soda_skilled={len(gatekeeper_skills)} ambient_results={len(ambient_results)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
