#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_dir"

fail=0
check_pattern() {
  local label=$1 pattern=$2
  local matches
  matches="$(git grep -IlE "$pattern" -- 2>/dev/null || true)"
  if [[ -n "$matches" ]]; then
    echo "SECURITY CHECK FAILED: $label detected in tracked files:" >&2
    echo "$matches" >&2
    fail=1
  fi
}

check_pattern "private key" 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY'
check_pattern "AWS access key" 'AKIA[0-9A-Z]{16}'
check_pattern "Google API key" 'AIza[0-9A-Za-z_-]{35}'
check_pattern "GitHub token" 'gh[pousr]_[A-Za-z0-9_]{30,}'
check_pattern "OpenAI-style secret" 'sk-[A-Za-z0-9_-]{20,}'

if perl -0777 -ne 'exit(/buildTypes\s*\{.*?release\s*\{.*?signingConfig\s+signingConfigs\.debug/s ? 0 : 1)' mobile-react-native/android/app/build.gradle; then
  echo "SECURITY CHECK FAILED: React Native release uses the debug signing key." >&2
  fail=1
fi
if grep -qE 'isMinifyEnabled = false|minifyEnabled false' android-app/app/build.gradle.kts mobile-react-native/android/app/build.gradle; then
  echo "SECURITY CHECK FAILED: release minification is disabled." >&2
  fail=1
fi
if ((fail)); then exit 1; fi
echo "Security checks passed: no recognized tracked secrets; release hardening invariants present."
