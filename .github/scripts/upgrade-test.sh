#!/usr/bin/env bash
# Installs the previous release, seeds state through the app, installs the new build over it
# and verifies nothing was lost or duplicated.
# Usage: upgrade-test.sh OLD_APK OLD_TEST_APK NEW_APK NEW_TEST_APK
# Each phase runs the test APK built with that app version, since tests run inside the app's process.
set -euo pipefail
old_apk=$1 old_test_apk=$2 new_apk=$3 new_test_apk=$4
pkg=app.synchealth.hc
runner=$pkg.test/androidx.test.runner.AndroidJUnitRunner
health_perms="WRITE_WEIGHT WRITE_BODY_FAT WRITE_LEAN_BODY_MASS WRITE_BONE_MASS WRITE_BODY_WATER_MASS
  WRITE_BASAL_METABOLIC_RATE READ_WEIGHT READ_BODY_FAT READ_LEAN_BODY_MASS"

# Right after boot Health Connect can ignore grants made from inside the test, so grant from the
# shell and wait until the package manager reports them before seeding.
grant_health() {
  for p in $health_perms; do
    adb shell pm grant "$pkg" "android.permission.health.$p" 2>/dev/null || true
  done
  for _ in $(seq 1 30); do
    if adb shell dumpsys package "$pkg" | grep "android.permission.health.WRITE_WEIGHT: granted=true" >/dev/null; then
      return 0
    fi
    sleep 2
  done
  echo "::error::Health Connect permissions were not granted"
  exit 1
}

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
grant_health
run_phase seedBeforeUpgrade

# Same package and signing key: an in-place update that keeps app data.
adb install -r "$new_apk"
adb install -r -t "$new_test_apk"
run_phase verifyAfterUpgrade
