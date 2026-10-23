package app.synchealth.hc.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import app.synchealth.hc.model.BodyMeasurement
import app.synchealth.hc.model.DataCategory
import app.synchealth.hc.priority.PriorityResolver
import app.synchealth.hc.priority.RecordIds
import app.synchealth.hc.settings.SettingsStore
import java.time.Duration
import java.time.ZoneId
import kotlin.reflect.KClass

class HealthConnectWriter(private val context: Context) {

    sealed interface Result {
        data object Written : Result
        data class SkippedLowerPriority(val reason: String) : Result
        data class Unavailable(val reason: String) : Result
    }

    suspend fun write(m: BodyMeasurement): Result {
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            return Result.Unavailable("Health Connect is not available")
        }
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (!granted.containsAll(BODY_PERMISSIONS)) return Result.Unavailable("Health Connect permission missing")

        val settings = SettingsStore(context)
        val index = WrittenIndex(context)
        val baseId = RecordIds.base(m.source, m.time.epochSecond)

        val overlapping = index.inWindow(DataCategory.BODY, m.time, BODY_WINDOW)
            .filter { it != baseId }
            .mapNotNull { id -> RecordIds.sourceOf(id)?.let { PriorityResolver.Existing(id, it) } }
        val decision = PriorityResolver.resolve(m.source, overlapping, settings.priority(DataCategory.BODY))
        if (!decision.write) {
            return Result.SkippedLowerPriority("A higher-priority source already has this measurement")
        }

        if (decision.deleteClientRecordIds.isNotEmpty()) {
            for ((kind, type) in BODY_TYPES) {
                client.deleteRecords(
                    recordType = type,
                    recordIdsList = emptyList(),
                    clientRecordIdsList = decision.deleteClientRecordIds.map { RecordIds.record(it, kind) },
                )
            }
            index.remove(DataCategory.BODY, decision.deleteClientRecordIds)
        }

        client.insertRecords(toRecords(m, baseId))
        index.add(DataCategory.BODY, baseId)
        return Result.Written
    }

    private fun toRecords(m: BodyMeasurement, baseId: String): List<Record> {
        val offset = ZoneId.systemDefault().rules.getOffset(m.time)
        // Version 2 once impedance arrives, so the upsert replaces the weight-only version.
        val version = if (m.composition != null) 2L else 1L
        fun meta(kind: String) = Metadata.autoRecorded(
            device = SCALE_DEVICE,
            clientRecordId = RecordIds.record(baseId, kind),
            clientRecordVersion = version,
        )

        val records = mutableListOf<Record>(
            WeightRecord(time = m.time, zoneOffset = offset, weight = Mass.kilograms(m.weightKg), metadata = meta("weight")),
        )
        m.composition?.let { c ->
            records += BodyFatRecord(m.time, offset, Percentage(c.bodyFatPercent), meta("fat"))
            records += LeanBodyMassRecord(m.time, offset, Mass.kilograms(c.leanBodyMassKg), meta("lean"))
            records += BoneMassRecord(m.time, offset, Mass.kilograms(c.boneMassKg), meta("bone"))
            records += BodyWaterMassRecord(m.time, offset, Mass.kilograms(c.bodyWaterMassKg), meta("water"))
            records += BasalMetabolicRateRecord(m.time, offset, Power.kilocaloriesPerDay(c.bmrKcalPerDay), meta("bmr"))
        }
        return records
    }

    companion object {
        private val BODY_WINDOW: Duration = Duration.ofMinutes(10)

        // Every body source so far is a reading taken by the Mi scale, so records are marked as auto-recorded by it.
        private val SCALE_DEVICE = Device(type = Device.TYPE_SCALE, manufacturer = "Xiaomi", model = "Mi Body Composition Scale 2")

        private val BODY_TYPES: Map<String, KClass<out Record>> = mapOf(
            "weight" to WeightRecord::class,
            "fat" to BodyFatRecord::class,
            "lean" to LeanBodyMassRecord::class,
            "bone" to BoneMassRecord::class,
            "water" to BodyWaterMassRecord::class,
            "bmr" to BasalMetabolicRateRecord::class,
        )

        val BODY_PERMISSIONS: Set<String> = BODY_TYPES.values.map { HealthPermission.getWritePermission(it) }.toSet()
    }
}
