#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

legacy_umbrella_name="cosmo-dev"
legacy_absolute_root="/Users/mac/${legacy_umbrella_name}"
legacy_home_root="~/${legacy_umbrella_name}"
legacy_genesis_path="${legacy_umbrella_name}/genesis"
legacy_sibling_repo="../repo"
legacy_ci_workdir="working-directory: genesis"
legacy_cd_genesis="cd genesis"

patterns=(
  "${legacy_absolute_root}"
  "${legacy_home_root}"
  "${legacy_genesis_path}"
  "${legacy_sibling_repo}"
  "${legacy_ci_workdir}"
  "${legacy_cd_genesis}"
)

rg_args=(
  --line-number
  --hidden
  --glob '!**/.git/**'
  --glob '!**/build/**'
  --glob '!**/.gradle/**'
  --glob '!**/.kotlin/**'
  --glob '!**/.cxx/**'
  --glob '!**/.venv*/**'
  --glob '!**/node_modules/**'
  --glob '!**/*.tflite'
  --glob '!**/*.litertlm'
  --glob '!**/*.bin'
  --glob '!**/*.onnx'
  --glob '!scripts/verify-relocation-safety.sh'
)

failed=0
for pattern in "${patterns[@]}"; do
  matches="$(rg "${rg_args[@]}" --fixed-strings "${pattern}" . || true)"
  if [[ -n "${matches}" ]]; then
    echo "Found relocation-unsafe reference: ${pattern}" >&2
    echo "${matches}" >&2
    failed=1
  fi
done

if [[ "${failed}" -ne 0 ]]; then
  exit 66
fi

echo "Relocation safety check passed."
