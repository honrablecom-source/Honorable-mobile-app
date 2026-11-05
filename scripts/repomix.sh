#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
repomix_binary="$project_root/.tools/output-efficiency/node_modules/.bin/repomix"
if [[ ! -x "$repomix_binary" ]]; then
  echo 'Repomix is missing. See docs/OUTPUT_EFFICIENCY.md for the pinned local installation.' >&2
  exit 127
fi
cd "$project_root"
exec "$repomix_binary" --config repomix.config.json "$@"
