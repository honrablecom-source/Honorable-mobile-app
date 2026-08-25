#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
test_lab_venv="$script_dir/test-lab/.venv"
if [[ -x "$test_lab_venv/bin/python" ]]; then
  export PATH="$test_lab_venv/bin:$PATH"
fi
codespaces_jdk21=/usr/local/sdkman/candidates/java/21.0.10-ms
if [[ -x "$codespaces_jdk21/bin/java" ]]; then
  export JAVA_HOME="$codespaces_jdk21"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ ${1:-} == indexTestMedia ]]; then
  "$script_dir/test-lab/download-model.sh"
fi
if [[ ${1:-} == enrichTestMedia ]]; then
  "$script_dir/test-lab/start-ollama.sh" || echo "WARNING: VLM unavailable; TinyCLIP indexing will continue."
fi
case ${1:-} in
  setup-python)
    exec "$script_dir/test-lab/setup-python.sh"
    ;;
  eval-add)
    exec python3 "$script_dir/test-lab/evaluation_labels.py" add
    ;;
  eval-list)
    exec python3 "$script_dir/test-lab/evaluation_labels.py" list
    ;;
  evaluate)
    shift
    exec "$script_dir/gradlew" evaluateSearch --console=plain "$@"
    ;;
  evaluate-degraded)
    shift
    exec "$script_dir/gradlew" :test-lab:evaluateDegradedSearch --console=plain "$@"
    ;;
  search)
    shift
    query=${1:?Usage: ./test-lab.sh search "description" [--debug]}
    shift
    gradle_args=()
    while (($#)); do case $1 in --debug) gradle_args+=(-PlabDebug=true);shift;;--top) [[ $# -ge 2 ]]||{ echo "--top requires a number" >&2;exit 2;};gradle_args+=("-PtopK=$2");shift 2;;*) gradle_args+=("$1");shift;;esac;done
    exec "$script_dir/gradlew" searchTestMedia "-Pquery=$query" "${gradle_args[@]}"
    ;;
  interactive)
    shift
    exec "$script_dir/gradlew" :test-lab:run --console=plain --args=interactive "$@"
    ;;
  list)
    shift
    exec "$script_dir/gradlew" :test-lab:listTestMedia --quiet --console=plain "$@"
    ;;
esac
exec "$script_dir/gradlew" "$@"
