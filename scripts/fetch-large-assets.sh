#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${ROOT_DIR}/app/src/debug/assets"
SOURCE_DIR="${1:-}"

if [[ -z "${SOURCE_DIR}" || ! -d "${SOURCE_DIR}" ]]; then
  echo "usage: scripts/fetch-large-assets.sh /path/to/cosmo_recovered_large_assets" >&2
  exit 64
fi

mkdir -p "${TARGET_DIR}"
rsync -a --delete "${SOURCE_DIR}/" "${TARGET_DIR}/"
find "${TARGET_DIR}" -type f -print0 | sort -z | xargs -0 shasum -a 256 > "${TARGET_DIR}/SHA256SUMS"
