#!/usr/bin/env bash
# Installs the previous release, seeds state through the app, installs the new build over it
# and verifies nothing was lost or duplicated.
# Usage: upgrade-test.sh OLD_APK OLD_TEST_APK NEW_APK NEW_TEST_APK
# Each phase runs the test APK built with that app version, since tests run inside the app's process.
set -euo pipefail
old_apk=$1 old_test_apk=$2 new_apk=$3 new_test_apk=$4
runner=app.synchealth.hc.test/androidx.test.runner.AndroidJUnitRunner

run_phase() {
  local out
  out=$(adb shell am instrument -w -e upgradePhase "$1" -e class "app.synchealth.hc.UpgradeTest#$1" "$runner")
  echo "$out"
  if ! grep -q "OK (1 test)" <<<"$out"; then
    echo "::error::Upgrade test phase '$1' failed"
    exit 1
  fi
}

adb install -r "$old_apk"
adb install -r -t "$old_test_apk"
run_phase seedBeforeUpgrade

# Same package and signing key: an in-place update that keeps app data.
adb install -r "$new_apk"
adb install -r -t "$new_test_apk"
run_phase verifyAfterUpgrade
