package app.synchealth.hc.scale

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import androidx.core.content.IntentCompat
import app.synchealth.hc.hc.HealthConnectWriter
import app.synchealth.hc.model.BodyMeasurement
import app.synchealth.hc.model.Source
import app.synchealth.hc.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ScaleScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val results = IntentCompat.getParcelableArrayListExtra(
            intent, BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java,
        ) ?: return
        val uuid = ParcelUuid.fromString(MiScale2Parser.SERVICE_UUID)
        val readings = results.mapNotNull { r ->
            r.scanRecord?.getServiceData(uuid)?.let(MiScale2Parser::parse)?.let { r.device.address to it }
        }
        if (readings.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                lock.withLock {
                    val settings = SettingsStore(context.applicationContext)
                    for ((address, reading) in readings) {
                        if (!ScaleBinding.accept(settings, address)) continue
                        handle(context.applicationContext, reading)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, reading: MiScale2Parser.Reading) {
        val settings = SettingsStore(context)
        val now = Instant.now()
        val time = ReadingDeduper.timeFor(reading, settings.lastReading, now) ?: return

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
    }

    private fun HealthConnectWriter.Result.describe() = when (this) {
        HealthConnectWriter.Result.Written -> "saved to Health Connect"
        is HealthConnectWriter.Result.SkippedLowerPriority -> reason
        is HealthConnectWriter.Result.Unavailable -> reason
    }

    companion object {
        private val lock = Mutex()
    }
}
