#!/usr/bin/env python3
"""Validate and summarize Cosmo audio corpus sources."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
CONFIG_DIR = REPO_ROOT / "config" / "audio-corpus"
DATASETS_PATH = CONFIG_DIR / "datasets.json"
LABEL_MAP_PATH = CONFIG_DIR / "label-map.json"
HAND_LABEL_SCHEMA_PATH = CONFIG_DIR / "hand-label-schema.json"
GATEKEEPER_TARGETS_PATH = CONFIG_DIR / "gatekeeper-targets.json"


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def fail(message: str) -> None:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(1)


def validate_registry(
    registry: dict[str, Any],
    label_map: dict[str, Any],
    hand_schema: dict[str, Any],
    gatekeeper_targets: dict[str, Any],
) -> None:
    datasets = registry.get("datasets", [])
    if not datasets:
        fail("datasets.json must contain at least one dataset")

    ids = [dataset.get("id") for dataset in datasets]
    duplicate_ids = sorted({dataset_id for dataset_id in ids if ids.count(dataset_id) > 1})
    if duplicate_ids:
        fail(f"duplicate dataset ids: {', '.join(duplicate_ids)}")

    profiles = registry.get("profiles", {})
    if registry.get("default_profile") not in profiles:
        fail("default_profile must name an existing profile")

    known_heads = {
        head
        for group in label_map.get("heads", {}).values()
        for head in group.keys()
    }
    if not known_heads:
        fail("label-map.json must define normalized heads")

    for dataset in datasets:
        dataset_id = dataset.get("id", "<missing>")
        for field in ("id", "name", "source_type", "role", "license_category", "product_use", "priority", "status"):
            if field not in dataset:
                fail(f"{dataset_id} is missing required field {field}")

        for mapped_head in dataset.get("weak_maps_to", []):
            if mapped_head not in known_heads:
                fail(f"{dataset_id} weak_maps_to unknown head {mapped_head}")

        if dataset["id"] == "first_party_hand_recorded":
            if dataset.get("license_category") != "first_party":
                fail("first_party_hand_recorded must use first_party license_category")
            required_fields = set(hand_schema.get("required_fields", []))
            missing = {"consent_id", "speaker_id", "device_type", "review_state", "split"} - required_fields
            if missing:
                fail(f"hand label schema is missing first-party provenance fields: {', '.join(sorted(missing))}")

    for profile_id, profile in profiles.items():
        allowed_categories = set(profile.get("allowed_license_categories", []))
        if not allowed_categories:
            fail(f"profile {profile_id} must allow at least one license category")

    target_sets = gatekeeper_targets.get("target_sets", {})
    for target_id in ("audio13", "text19"):
        labels = target_sets.get(target_id, {}).get("labels")
        if not isinstance(labels, list) or not labels:
            fail(f"gatekeeper target set {target_id} must define labels")
        if len(labels) != len(set(labels)):
            fail(f"gatekeeper target set {target_id} contains duplicate labels")
    if len(target_sets["audio13"]["labels"]) != 13:
        fail("audio13 target set must contain 13 labels")
    if len(target_sets["text19"]["labels"]) != 19:
        fail("text19 target set must contain 19 labels")


def datasets_for_profile(registry: dict[str, Any], profile_id: str) -> list[dict[str, Any]]:
    profile = registry["profiles"].get(profile_id)
    if profile is None:
        fail(f"unknown profile {profile_id}")

    allowed_categories = set(profile["allowed_license_categories"])
    include_ambiguous = bool(profile.get("include_ambiguous", False))
    include_noncommercial = bool(profile.get("include_noncommercial", False))

    selected = []
    for dataset in registry["datasets"]:
        category = dataset["license_category"]
        if category in allowed_categories:
            selected.append(dataset)
            continue
        if include_ambiguous and category in {"ambiguous_review", "mixed_review"}:
            selected.append(dataset)
            continue
        if include_noncommercial and category == "noncommercial":
            selected.append(dataset)

    return selected


def print_summary(registry: dict[str, Any], selected: list[dict[str, Any]], profile_id: str) -> None:
    print(f"profile: {profile_id}")
    print(f"selected_datasets: {len(selected)}")

    by_role: dict[str, list[str]] = {}
    for dataset in selected:
        for role in dataset["role"]:
            by_role.setdefault(role, []).append(dataset["id"])

    for role in sorted(by_role):
        ids = ", ".join(sorted(by_role[role]))
        print(f"{role}: {ids}")

    excluded = [dataset["id"] for dataset in registry["datasets"] if dataset not in selected]
    if excluded:
        print(f"excluded: {', '.join(sorted(excluded))}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--profile",
        default=None,
        help="Corpus profile to summarize. Defaults to datasets.json default_profile.",
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate config files without printing a dataset summary.",
    )
    args = parser.parse_args()

    registry = load_json(DATASETS_PATH)
    label_map = load_json(LABEL_MAP_PATH)
    hand_schema = load_json(HAND_LABEL_SCHEMA_PATH)
    gatekeeper_targets = load_json(GATEKEEPER_TARGETS_PATH)
    validate_registry(registry, label_map, hand_schema, gatekeeper_targets)

    profile_id = args.profile or registry["default_profile"]
    selected = datasets_for_profile(registry, profile_id)

    if args.validate_only:
        print("audio corpus config valid")
        return

    print_summary(registry, selected, profile_id)


if __name__ == "__main__":
    main()
