#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SODA_DIR="${ROOT_DIR}/app/src/main/assets/soda"

if [[ ! -d "${SODA_DIR}" ]]; then
  echo "SODA assets directory not found: ${SODA_DIR}" >&2
  exit 66
fi

verified=0
while IFS=$'\t' read -r filename expected_size expected_sha; do
  target="${SODA_DIR}/${filename}"
  if [[ ! -f "${target}" ]]; then
    echo "required SODA pack is missing: ${filename}" >&2
    exit 66
  fi
  actual_sha="$(shasum -a 256 "${target}" | awk '{print $1}')"
  if [[ "${actual_sha}" != "${expected_sha}" ]]; then
    echo "SODA checksum mismatch for ${filename}: expected ${expected_sha}, got ${actual_sha}" >&2
    exit 66
  fi
  actual_size="$(wc -c < "${target}" | tr -d ' ')"
  if [[ "${actual_size}" != "${expected_size}" ]]; then
    echo "SODA size mismatch for ${filename}: expected ${expected_size}, got ${actual_size}" >&2
    exit 66
  fi
  verified=$((verified + 1))
done <<'EOF'
lp_cpu.zip	56458465	fac23ca956f473c5025621784a1657a1663a16b0754886b975f3cde3f1345f04
lp_lga_tpu.zip	145607304	b10205a4fe668123c17c3f1405e8acc6d072894574c875db05af6372e9993d2c
lp_zuma_tpu.zip	146456436	c808fd151178adfffd99906f9290935ba189898e9700e47e8fa37a1701effd41
EOF

checksums_tmp="$(mktemp)"
(
  cd "${SODA_DIR}"
  for filename in lp_cpu.zip lp_lga_tpu.zip lp_zuma_tpu.zip; do
    shasum -a 256 "./${filename}"
  done
) > "${checksums_tmp}"
mv "${checksums_tmp}" "${SODA_DIR}/SHA256SUMS"

echo "Verified ${verified} bundled SODA language packs."
