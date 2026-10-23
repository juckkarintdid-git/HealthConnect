# HealthConnect Sync

Free Android app that collects health data from devices that don't talk to Samsung Health
and writes it into **Health Connect**, which Samsung Health reads.

| Source | Status | How |
|---|---|---|
| Mi Body Composition Scale 2 | ✅ v0.1 | Listens for the scale's Bluetooth adverts in the background, computes body composition, writes to Health Connect within seconds |
| Zepp Life cloud | planned | Poll Zepp account every few minutes for history |
| Huawei Watch GT5 | planned | Import the data export ZIP from Huawei Health |

Written to Health Connect: weight, body fat %, lean body mass, bone mass, body water mass, BMR.

## Install
1. Download the APK from **Releases** (or, for unreleased builds, the `HealthConnect-apk` artifact of the latest **Actions → Build APK** run).
2. Copy the APK to the phone and install it (allow "Install unknown apps" for your file manager).
3. In the app: **Grant access** to Health Connect, fill in the profile, turn on the scale switch.
4. In Samsung Health: **Settings → Health Connect**, allow Samsung Health to read weight and body composition.

## Source priority
When two sources record the same measurement within 10 minutes, the app keeps only the
higher-priority one (reorder in the app). For data written by other apps, use Health Connect's
own setting: **Settings → Health Connect → Data and access → (type) → Data sources and priority**.

## Release signing
CI signs the APK with the release key when these repository secrets exist:
`SIGNING_KEYSTORE_BASE64` (base64 of the .jks), `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`,
`SIGNING_KEY_PASSWORD`. Without them it falls back to a per-run debug key, and those APKs cannot
update an installed copy. Keep a backup of the keystore: losing it means reinstalling from scratch.

## QA
Every change runs unit tests, emulator tests on Android 14–16 and an upgrade-over-previous-version
test; the emulator suite also runs weekly. See [docs/QA.md](docs/QA.md) for the gate, the manual
phone checklist and how to release.

## Security
Reviewed against OWASP Mobile Top 10 (2024): see [docs/SECURITY.md](docs/SECURITY.md).

## Build
`./gradlew assembleRelease` (JDK 17, Android SDK 35). CI builds on every push and PR.

See [docs/DESIGN.md](docs/DESIGN.md) for the architecture.

## License
GPL-3.0, see [LICENSE](LICENSE). The body composition formulas are adapted from
[openScale](https://github.com/oliexdev/openScale) (GPL-3.0).
