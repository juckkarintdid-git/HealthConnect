# Security review — OWASP Mobile Top 10 (2024) — PR #1

Scope: juckkarintdid-git/HealthConnect PR #1 (scale BLE → Health Connect). Date: 2026-10-23.
Status: M2, M4, M8 and release signing fixed in PR #1 (approved by YOD 2026-10-23).

| # | Category | Result | Finding | Proposed fix |
|---|---|---|---|---|
| M1 | Improper Credential Usage | Pass (now) | No credentials yet. Future Zepp token is the risk. | When Zepp is added: store token encrypted with Android Keystore, never log it. |
| M2 | Inadequate Supply Chain Security | Partial | Gradle distribution not checksum-pinned; CI token has default permissions; Health Connect client is an alpha version. | Add `distributionSha256Sum`; set workflow `permissions: contents: read`; move to stable connect-client when CI can verify it. |
| M3 | Insecure Auth/Authorization | Pass | HC permissions checked before every write; rationale alias protected by START_VIEW_PERMISSION_USAGE. | — |
| M4 | Insufficient Input/Output Validation | **Fail** | Any nearby BLE device advertising 0x181B data can inject fake weights (spoofing). Parser already validates length/ranges. | Trust-on-first-use: bind to the first scale's MAC, ignore others, "Forget scale" button. |
| M5 | Insecure Communication | Pass | No network access at all (no INTERNET permission). | Zepp step will need HTTPS only + no cleartext. |
| M6 | Inadequate Privacy Controls | Pass | Data stays on device; no logging of health values; only needed HC write permissions. | — |
| M7 | Insufficient Binary Protections | Accepted risk | No R8/obfuscation; app holds no secrets. | Leave off for now (less risk of runtime breakage); revisit when Zepp token exists. |
| M8 | Security Misconfiguration | **Fail** | `allowBackup="true"` → profile/weight prefs go to Google backup and device transfer. `BootReceiver` exported without action check. | `allowBackup=false` + data_extraction_rules excluding everything; BootReceiver ignores non-boot actions. |
| M9 | Insecure Data Storage | Partial | Profile (height, birth year, sex), last weight, scale MAC in app-private SharedPreferences, plaintext. Sandboxed on non-rooted phones. | Fixed by M8 (no backup). Encrypting prefs judged not worth it (Jetpack security-crypto is deprecated). |
| M10 | Insufficient Cryptography | N/A | No crypto used. | — |

## Non-security issues found
1. **Signing**: CI signs with a debug key generated per run, so each new APK has a different signature and cannot update the installed app (must uninstall → loses settings). Fix: release keystore stored as GitHub secrets (YOD creates it once); workflow falls back to debug key if secrets are absent.
2. Not yet tested on a real phone.
3. Samsung battery management ("Sleeping apps") may stop background scanning; set app battery to Unrestricted.
4. Samsung Health may not display every HC body type (e.g. bone/water mass); weight and body fat are expected to show — to verify on device.
