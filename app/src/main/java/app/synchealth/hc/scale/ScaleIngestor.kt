package app.synchealth.hc.scale

import android.content.Context
import app.synchealth.hc.hc.HealthConnectWriter
import app.synchealth.hc.model.BodyMeasurement
import app.synchealth.hc.model.Source
import app.synchealth.hc.settings.SettingsStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Turns one raw scale advert into Health Connect records. Shared by the BLE receiver and tests. */
object ScaleIngestor {
    private val lock = Mutex()

    /** Returns the write result, or null when the advert was ignored (unparseable, untrusted, duplicate). */
    suspend fun ingest(context: Context, address: String, serviceData: ByteArray): HealthConnectWriter.Result? =
        lock.withLock {
            val reading = MiScale2Parser.parse(serviceData) ?: return@withLock null
            val settings = SettingsStore(context)
            if (!ScaleBinding.accept(settings, address)) return@withLock null
            handle(context, settings, reading)
        }

    private suspend fun handle(
        context: Context,
        settings: SettingsStore,
        reading: MiScale2Parser.Reading,
    ): HealthConnectWriter.Result? {
        val now = Instant.now()
        val time = ReadingDeduper.timeFor(reading, settings.lastReading, now) ?: return null

        val composition = reading.impedanceOhm?.let { imp ->
            settings.profile?.let { BodyCompositionCalculator.calculate(reading.weightKg, imp, it) }
        }
        val measurement = BodyMeasurement(time, Source.SCALE_BLE, reading.weightKg, reading.impedanceOhm, composition)
        val result = runCatching { HealthConnectWriter(context).write(measurement) }

        settings.lastReading = LastReading(time, now, reading.weightKg, reading.impedanceOhm)
        val at = DateTimeFormatter.ofPattern("d MMM HH:mm").withZone(ZoneId.systemDefault()).format(time)
        settings.lastStatus = "%.2f kg at %s%s: %s".format(
            reading.weightKg, at,
            composition?.let { ", fat %.1f%%".format(it.bodyFatPercent) } ?: "",
            result.fold({ it.describe() }, { "error: ${it.message}" }),
        )
        return result.getOrNull()
    }

    private fun HealthConnectWriter.Result.describe() = when (this) {
        HealthConnectWriter.Result.Written -> "saved to Health Connect"
        is HealthConnectWriter.Result.SkippedLowerPriority -> reason
        is HealthConnectWriter.Result.Unavailable -> reason
    }
}
