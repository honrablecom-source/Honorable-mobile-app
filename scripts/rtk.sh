#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
rtk_binary="$project_root/.tools/rtk-oss-v1.0.1/rtk"
if [[ ! -x "$rtk_binary" ]]; then
  echo 'RTK is missing. Follow docs/OUTPUT_EFFICIENCY.md to install the pinned Linux OSS release.' >&2
  exit 127
fi
exec "$rtk_binary" "$@"
