#!/usr/bin/env bash
# Fails when versionCode would not let Android install this build over PREV_REF.
# Usage: check-version.sh PREV_REF [EXPECTED_VERSION_NAME]
set -euo pipefail
prev_ref=$1 expected_name=${2:-}
read -r cur_code cur_name < <(bash .github/scripts/version-of.sh)
read -r prev_code prev_name < <(bash .github/scripts/version-of.sh "$prev_ref")
echo "current: $cur_code ($cur_name), previous $prev_ref: $prev_code ($prev_name)"

if [ -n "$expected_name" ] && [ "$cur_name" != "$expected_name" ]; then
  echo "::error::versionName $cur_name does not match release tag $expected_name"; exit 1
fi
if [ "$cur_name" != "$prev_name" ] && [ "$cur_code" -le "$prev_code" ]; then
  echo "::error::versionName changed but versionCode $cur_code is not greater than $prev_code"; exit 1
fi
if [ "$cur_code" -lt "$prev_code" ]; then
  echo "::error::versionCode went down ($prev_code -> $cur_code); phones will refuse the update"; exit 1
fi
