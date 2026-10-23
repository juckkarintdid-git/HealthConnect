#!/usr/bin/env bash
# Prints "<versionCode> <versionName>" from app/build.gradle.kts at a git ref (default: working tree).
set -euo pipefail
src=$( [ -n "${1:-}" ] && git show "$1:app/build.gradle.kts" || cat app/build.gradle.kts )
code=$(sed -n 's/^ *versionCode = \([0-9]*\).*/\1/p' <<<"$src")
name=$(sed -n 's/^ *versionName = "\(.*\)".*/\1/p' <<<"$src")
echo "$code $name"
