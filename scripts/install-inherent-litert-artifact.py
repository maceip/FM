#!/usr/bin/env python3
"""Install the LiteRT artifact produced by ~/inherent into neural Android assets."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INHERENT_LITERT_DIR = Path.home() / "inherent" / "artifacts" / "model-groups" / "001" / "export" / "litert"
ASSETS_MODELS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "models"
MODEL_MANIFEST_PATH = REPO_ROOT / "app" / "src" / "main" / "assets" / "model-manifest.json"

MODEL_ID = "gatekeeper_inherent_v0"
METADATA_ID = "gatekeeper_inherent_v0_metadata"
MODEL_FILENAME = "inherent.tflite"
METADATA_FILENAME = "inherent.metadata.json"


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def manifest_entry(model_id: str, filename: str, path: Path, media_type: str) -> dict[str, Any]:
    return {
        "id": model_id,
        "filename": filename,
        "delivery": "bundled",
        "sha256": file_sha256(path),
        "sizeBytes": path.stat().st_size,
        "mediaType": media_type,
        "required": True,
    }


def validate_inherent_metadata(metadata: dict[str, Any]) -> None:
    if metadata.get("input_tensor") != "mel_spectrogram":
        raise SystemExit("error: inherent metadata input_tensor must be mel_spectrogram")
    if metadata.get("output_tensor") != "intent_output":
        raise SystemExit("error: inherent metadata output_tensor must be intent_output")
    head_order = metadata.get("head_order")
    threshold_keys = metadata.get("threshold_keys_in_order")
    default_thresholds = metadata.get("default_thresholds")
    if not isinstance(head_order, list) or len(head_order) != 13:
        raise SystemExit("error: inherent metadata head_order must contain 13 heads")
    if not isinstance(threshold_keys, list) or len(threshold_keys) != 13:
        raise SystemExit("error: inherent metadata threshold_keys_in_order must contain 13 keys")
    if not isinstance(default_thresholds, dict):
        raise SystemExit("error: inherent metadata default_thresholds must be an object")
    missing = [key for key in threshold_keys if key not in default_thresholds]
    if missing:
        raise SystemExit(f"error: inherent metadata missing thresholds for: {', '.join(missing)}")


def upsert_manifest_entries(model_path: Path, metadata_path: Path) -> None:
    manifest = load_json(MODEL_MANIFEST_PATH)
    new_entries = {
        MODEL_ID: manifest_entry(MODEL_ID, MODEL_FILENAME, model_path, "application/vnd.tensorflow.lite"),
        METADATA_ID: manifest_entry(METADATA_ID, METADATA_FILENAME, metadata_path, "application/json"),
    }
    existing = [entry for entry in manifest["models"] if entry.get("id") not in new_entries]
    manifest["models"] = existing + [new_entries[MODEL_ID], new_entries[METADATA_ID]]
    with MODEL_MANIFEST_PATH.open("w", encoding="utf-8") as handle:
        json.dump(manifest, handle, indent=2)
        handle.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=DEFAULT_INHERENT_LITERT_DIR)
    args = parser.parse_args()

    source_dir = args.source_dir.expanduser()
    source_model = source_dir / MODEL_FILENAME
    source_metadata = source_dir / METADATA_FILENAME
    if not source_model.is_file():
        raise SystemExit(f"error: source model not found: {source_model}")
    if not source_metadata.is_file():
        raise SystemExit(f"error: source metadata not found: {source_metadata}")
    validate_inherent_metadata(load_json(source_metadata))

    ASSETS_MODELS_DIR.mkdir(parents=True, exist_ok=True)
    target_model = ASSETS_MODELS_DIR / MODEL_FILENAME
    target_metadata = ASSETS_MODELS_DIR / METADATA_FILENAME
    shutil.copyfile(source_model, target_model)
    shutil.copyfile(source_metadata, target_metadata)
    upsert_manifest_entries(target_model, target_metadata)

    print(f"installed inherent LiteRT model: {target_model}")
    print(f"model bytes={target_model.stat().st_size} sha256={file_sha256(target_model)}")
    print(f"installed inherent metadata: {target_metadata}")
    print(f"metadata bytes={target_metadata.stat().st_size} sha256={file_sha256(target_metadata)}")


if __name__ == "__main__":
    main()
