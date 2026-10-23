# HealthConnect app — design notes (draft)

Goal: free Android app (Kotlin) on Samsung S25 Ultra that collects data from YOD's devices
into Health Connect every 1–10 min, so Samsung Health shows everything. User picks the
primary source per data type when sources overlap.

## Sources
| Source | Method | Data | Notes |
|---|---|---|---|
| Mi Body Composition Scale 2 (BLE) | Scan BLE adverts, service 0x181B (weight + impedance) | weight, body fat %, lean mass, BMR, water, bone, visceral fat (computed) | Instant; needs height/age/sex profile |
| Zepp Life cloud | Unofficial Huami/Zepp API (account token), poll every 5 min | weight + body comp history | May break if Zepp changes API |
| Huawei Watch GT5 | Manual import of Huawei Health data export ZIP (user decision 2026-10-23); user keeps Huawei Health app | steps, HR, sleep, SpO2, workouts | Huawei Health app data is sandboxed |

## Pipeline
Source -> Normalizer (units, Asia/Bangkok tz) -> PriorityResolver -> HealthConnectWriter

- Scheduling: WorkManager periodic (min 15 min) + foreground service w/ 5-min alarm for 1–10 min cadence; BLE scan as foreground service when enabled.
- Dedupe: Metadata.clientRecordId = hash(source, type, startTime); clientRecordVersion for upserts.
- Priority: per data type, ordered list of sources (Room table). Resolver drops records from
  lower-priority sources when a higher one covers the same time window. Also document HC's
  built-in "Data sources and priority" for third-party writers (e.g. phone steps vs Gadgetbridge).
- State: Room DB (sync cursors, source config, priority), DataStore (profile, settings).
- Credentials: Zepp token in EncryptedSharedPreferences / Keystore.

## Milestones
1. Scaffold: Compose UI, HC permissions, settings + priority screen.
2. Scale BLE source + body composition calc -> HC.
3. Zepp cloud source (login, backfill, 5-min poll).
4. Huawei export importer: pick ZIP via SAF, parse JSON, write history to HC (idempotent via clientRecordId).
5. Status screen: last sync per source, record counts, errors.
