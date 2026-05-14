#!/usr/bin/env python3
"""Derives the app-private LiteRT-LM storage id from a logical model id."""

from __future__ import annotations

import hashlib
import re
import sys

DEFAULT_MODEL_STORAGE_ID = "default-litertlm"
MAX_MODEL_STORAGE_ID_CHARS = 96
MODEL_ID_HASH_CHARS = 16
UNSAFE_MODEL_ID_CHARS = re.compile(r"[^A-Za-z0-9._-]+")


def model_storage_id(model_id: str) -> str:
    raw = model_id.strip()
    normalized = UNSAFE_MODEL_ID_CHARS.sub("-", raw).strip("-_.") or DEFAULT_MODEL_STORAGE_ID
    hash_suffix = hashlib.sha256(raw.encode("utf-8")).hexdigest()[:MODEL_ID_HASH_CHARS]
    max_slug_chars = MAX_MODEL_STORAGE_ID_CHARS - MODEL_ID_HASH_CHARS - 1
    slug = normalized[:max_slug_chars].strip("-_.") or DEFAULT_MODEL_STORAGE_ID
    return f"{slug}-{hash_suffix}"


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: model-storage-id.py <model-id>", file=sys.stderr)
        return 64
    print(model_storage_id(sys.argv[1]), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
