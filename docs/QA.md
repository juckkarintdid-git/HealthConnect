# QA gate

## Automatic (GitHub Actions, free on this public repo)

| When | Check | Workflow |
|---|---|---|
| Every PR / push to main | Version guard: versionCode never goes down, and goes up when versionName changes | Build APK |
| Every PR / push to main | Unit tests (parser, body composition, dedupe, priority) | Build APK |
| Every PR / push to main | Signed APK builds | Build APK |
| Every PR / push to main | Instrumented tests against real Health Connect on Android 14, 15, 16 | Emulator tests |
| Every PR / push to main | Upgrade test: install previous version, seed data, install new build over it, verify settings, trusted scale and records survive without duplicates | Emulator tests |
| Every Monday 02:00 UTC | All emulator tests again, to catch Android / Health Connect changes without code changes | Emulator tests |
| Weekly | Dependabot PRs for Gradle libraries and GitHub Actions; each must pass the gates above | Dependabot |
| Tag `v*` | Version must match tag and increase over the last release; publish signed APK to GitHub Releases | Release |

When a new Android version ships, add its API level to the `api-level` matrix in
`.github/workflows/emulator.yml`.

## Required: protect `main` (one-time, repo owner)
Settings → Branches → Add branch ruleset (or classic rule) for `main`:
- Require a pull request before merging
- Require status checks to pass: `build`, `instrumented (API 34)`, `instrumented (API 35)`,
  `instrumented (API 36)`, `upgrade from previous release`
- Block force pushes

## Manual on the phone (Samsung S25 Ultra) before tagging a release
Emulators have no real Bluetooth and no Samsung Health, so check these by hand:

- [ ] Install the new APK over the previous one (no uninstall); settings are still there
- [ ] Step on the scale: reading appears in the app within ~10 s
- [ ] Same reading shows in Samsung Health (weight, body fat)
- [ ] Step on again within a minute: no duplicate in Samsung Health
- [ ] Reboot the phone, step on the scale: still recorded (scan re-registered)
- [ ] App battery setting is "Unrestricted"; after a night idle, a morning reading is recorded
- [ ] After an Android system update: repeat the scale and Samsung Health checks

## Releasing
1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts` in a PR; merge when green.
2. Run the manual checklist with the PR's APK.
3. Tag: `git tag v0.2.0 && git push origin v0.2.0` → Release workflow publishes the APK.
