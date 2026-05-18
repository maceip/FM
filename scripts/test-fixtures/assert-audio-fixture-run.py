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
    "raw_audio_unavailable",
    "inherent_unavailable",
)

TRANSCRIPT_RE = re.compile(r"audio inject soda transcript: name=(\S+) isFinal=true text=(.*)$")
GATEKEEPER_RE = re.compile(r"audio inject soda gatekeeper: name=(\S+) result=.*detectedSkills=\{([^}]*)\}")
AMBIENT_RE = re.compile(r"audio inject ambient: name=(\S+) result=(.*)$")
BRACED_FIELD_RE = re.compile(r"(?P<field>\w+)=\{(?P<body>[^}]*)\}")
FLOAT_RE = re.compile(r"(?P<key>[A-Za-z0-9_]+)=(?P<value>-?\d+(?:\.\d+)?(?:[Ee][+-]?\d+)?)")


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


def extract_braced_field(result: str, field: str) -> str:
    for match in BRACED_FIELD_RE.finditer(result):
        if match.group("field") == field:
            return match.group("body")
    return ""


def parse_float_map(raw: str) -> dict[str, float]:
    return {match.group("key"): float(match.group("value")) for match in FLOAT_RE.finditer(raw)}


def parse_inherent_heads(signals: dict[str, float]) -> dict[int, float]:
    heads: dict[int, float] = {}
    for key, value in signals.items():
        prefix = "inherent_head_"
        if key.startswith(prefix):
            heads[int(key.removeprefix(prefix))] = value
    return heads


def ambient_quality_report(fixtures: list[dict], ambient_results: dict[str, str]) -> dict:
    rows: list[dict] = []
    positive_total = 0
    positive_scored = 0
    positive_top1 = 0
    negative_total = 0
    negative_clean = 0

    for fixture in fixtures:
        name = fixture_name(fixture)
        expected_index = fixture.get("expected_audio_gatekeeper_top_index")
        result = ambient_results.get(name, "")
        skills = parse_skills(extract_braced_field(result, "detectedSkills"))
        signals = parse_float_map(extract_braced_field(result, "screenSignals"))
        heads = parse_inherent_heads(signals)

        top_index = None
        top_score = None
        if heads:
            top_index = max(heads, key=heads.get)
            top_score = heads[top_index]

        row = {
            "fixture": name,
            "expected_top_index": expected_index,
            "detected_skills": sorted(skills),
            "has_inherent_scores": bool(heads),
            "actual_top_index": top_index,
            "actual_top_score": top_score,
        }

        if expected_index is None:
            negative_total += 1
            clean = not skills
            if clean:
                negative_clean += 1
            row["negative_clean"] = clean
        else:
            positive_total += 1
            expected_score = heads.get(expected_index)
            other_scores = [score for index, score in heads.items() if index != expected_index]
            max_other = max(other_scores) if other_scores else None
            top1 = top_index == expected_index if top_index is not None else False
            if heads:
                positive_scored += 1
            if top1:
                positive_top1 += 1
            row.update(
                {
                    "expected_score": expected_score,
                    "max_other_score": max_other,
                    "expected_margin": (
                        expected_score - max_other
                        if expected_score is not None and max_other is not None
                        else None
                    ),
                    "top1": top1,
                }
            )
        rows.append(row)

    return {
        "positive_total": positive_total,
        "positive_scored": positive_scored,
        "positive_top1": positive_top1,
        "negative_total": negative_total,
        "negative_clean": negative_clean,
        "rows": rows,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logcat", type=Path)
    parser.add_argument("--index", type=Path, default=Path(__file__).with_name("gatekeeper-utterances.json"))
    parser.add_argument("--target", choices=("ambient", "soda", "both"), default="both")
    parser.add_argument("--ambient-quality-report", type=Path)
    parser.add_argument("--enforce-ambient-quality", action="store_true")
    parser.add_argument("--min-ambient-top1", type=float, default=0.8)
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

        quality = ambient_quality_report(fixtures, ambient_results)
        if args.ambient_quality_report:
            args.ambient_quality_report.write_text(
                json.dumps(quality, indent=2, sort_keys=True) + "\n",
                encoding="utf-8"
            )
        if quality["positive_total"]:
            if quality["positive_scored"]:
                top1_text = f"top1={quality['positive_top1']}/{quality['positive_scored']}"
            else:
                top1_text = "top1=n/a"
            print(
                "Inherent ambient quality: "
                f"{top1_text} "
                f"scored={quality['positive_scored']}/{quality['positive_total']} "
                f"negative_clean={quality['negative_clean']}/{quality['negative_total']}"
            )
        if args.enforce_ambient_quality and quality["positive_total"]:
            if quality["positive_scored"] != quality["positive_total"]:
                failures.append(
                    "ambient quality below gate: "
                    f"scored={quality['positive_scored']}/{quality['positive_total']}"
                )
            top1_rate = quality["positive_top1"] / quality["positive_total"]
            if top1_rate < args.min_ambient_top1:
                failures.append(
                    "ambient quality below gate: "
                    f"top1={top1_rate:.3f} min={args.min_ambient_top1:.3f}"
                )
            if quality["negative_clean"] != quality["negative_total"]:
                failures.append(
                    "ambient quality below gate: "
                    f"negative_clean={quality['negative_clean']}/{quality['negative_total']}"
                )

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
