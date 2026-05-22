#!/usr/bin/env bash
exec "$(cd "$(dirname "$0")/.." && pwd)/scripts/download-models.sh" --only llm_gemma_4_e2b_litertlm "$@"
