#!/usr/bin/env python3
"""Verify that an inherent LiteRT export is safe for the Android runtime."""

from __future__ import annotations

import argparse
from pathlib import Path

from inherent_litert_contract import ContractError, validate_artifact


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("model", type=Path, help="Path to inherent.tflite")
    parser.add_argument("metadata", type=Path, help="Path to inherent.metadata.json")
    args = parser.parse_args()

    try:
        contract = validate_artifact(args.model.expanduser(), args.metadata.expanduser())
    except ContractError as error:
        raise SystemExit(f"error: {error}") from error

    print(
        "inherent LiteRT contract ok: "
        f"input={contract['input_shape']} output={contract['output_shape']} "
        f"input_name={contract['input_name']} output_name={contract['output_name']}"
    )


if __name__ == "__main__":
    main()
