#!/usr/bin/env bash
set -euo pipefail
port_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
cd "$port_dir"
if [[ ! -x build/ri_native ]]; then
  cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
  cmake --build build -j2
fi
if [[ "${1:-}" == "--web" ]]; then
  shift
  exec python3 web-test.py "$@"
fi
if [[ "${1:-}" != "--self-test" && -z "${DISPLAY:-}" ]]; then
  echo "No desktop display detected; starting the browser test port instead."
  exec python3 web-test.py
fi
exec ./build/ri_native "$@"
