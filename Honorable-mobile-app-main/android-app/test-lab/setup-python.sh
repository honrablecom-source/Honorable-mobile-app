#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
python3 -m venv "$script_dir/.venv"
"$script_dir/.venv/bin/python" -m pip install --requirement "$script_dir/requirements-dev.txt"
"$script_dir/.venv/bin/python" -c 'import numpy, onnxruntime, PIL, tokenizers'
echo "Honorable test-lab Python environment is ready."
