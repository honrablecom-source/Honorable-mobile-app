#!/usr/bin/env bash
set -euo pipefail

artifact="${1:?usage: verify-release.sh <release-apk>}"
test -f "$artifact"
listing="$(unzip -Z1 "$artifact")"

if grep -Eq '(^|/)(test-media|test-lab|benchmark)(/|$)' <<<"$listing"; then
  echo "Release contains development/test assets" >&2
  exit 1
fi
if unzip -p "$artifact" classes.dex | strings | grep -Eq 'codespaces\.com|DATABASE_URL=|RESEND_API_KEY='; then
  echo "Release contains a forbidden development endpoint or credential assignment" >&2
  exit 1
fi
echo "Release artifact security checks passed"
