#!/usr/bin/env bash
exec "$(cd "$(dirname "$0")/.." && pwd)/scripts/download-models.sh" "$@"
