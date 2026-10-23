package app.synchealth.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.synchealth.hc.hc.HealthConnectWriter
import app.synchealth.hc.model.BodyMeasurement
import app.synchealth.hc.model.DataCategory
import app.synchealth.hc.model.Source
import app.synchealth.hc.scale.ScaleIngestor
import app.synchealth.hc.scale.Sex
import app.synchealth.hc.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.reflect.KClass

/**
 * End-to-end on an emulator: raw Mi Scale 2 advert bytes → ScaleIngestor → real Health Connect.
 * Bluetooth and Samsung Health are out of scope (not available on emulators).
 */
@RunWith(AndroidJUnit4::class)
class ScaleToHealthConnectTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var client: HealthConnectClient

    private val scale = "C8:47:8C:00:00:01"
    private val spoofer = "AA:BB:CC:DD:EE:FF"

    @Before
    fun setUp() = runBlocking {
        assumeTrue(
            "Health Connect not available on this image",
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE,
        )
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val perms = HealthConnectWriter.BODY_PERMISSIONS + listOf(WeightRecord::class, BodyFatRecord::class, LeanBodyMassRecord::class)
            .map { HealthPermission.getReadPermission(it) }
        perms.forEach { automation.grantRuntimePermission(context.packageName, it) }

        client = HealthConnectClient.getOrCreate(context)
        listOf(WeightRecord::class, BodyFatRecord::class, LeanBodyMassRecord::class).forEach {
            client.deleteRecords(it, TimeRangeFilter.after(Instant.EPOCH))
        }
        listOf("settings", "written_index").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        SettingsStore(context).saveProfile(175f, 1995, Sex.MALE)
    }

    @Test
    fun weightThenImpedanceBecomesOneMeasurementWithBodyComposition() = runBlocking {
        val now = LocalDateTime.now()
        // Typical burst: stabilized weight, the same advert again, then the impedance follow-up.
        ScaleIngestor.ingest(context, scale, advert(now, kg = 75.0, impedance = null))
        assertNull(ScaleIngestor.ingest(context, scale, advert(now, kg = 75.0, impedance = null)))
        ScaleIngestor.ingest(context, scale, advert(now, kg = 75.0, impedance = 500))

        val weights = read(WeightRecord::class)
        assertEquals(1, weights.size)
        assertEquals(75.0, weights.single().weight.inKilograms, 0.01)
        assertEquals(expectedInstant(now), weights.single().time)

        val fat = read(BodyFatRecord::class)
        assertEquals(1, fat.size)
        assertEquals(22.5, fat.single().percentage.value, 1.0)
        assertEquals(1, read(LeanBodyMassRecord::class).size)
    }

    @Test
    fun ignoresAdvertsFromAnUntrustedDevice() = runBlocking {
        val now = LocalDateTime.now()
        ScaleIngestor.ingest(context, scale, advert(now, kg = 75.0, impedance = 500))
        assertNull(ScaleIngestor.ingest(context, spoofer, advert(now.plusMinutes(30), kg = 99.0, impedance = 500)))

        assertEquals(listOf(75.0), read(WeightRecord::class).map { it.weight.inKilograms })
    }

    @Test
    fun lowerPrioritySourceIsSkippedAndHigherReplacesIt() = runBlocking {
        val now = LocalDateTime.now()
        ScaleIngestor.ingest(context, scale, advert(now, kg = 75.0, impedance = null))

        // Zepp ranks below the scale by default: its overlapping copy is dropped.
        // A minute earlier: Health Connect rejects records timestamped in the future.
        val zepp = BodyMeasurement(expectedInstant(now).minusSeconds(60), Source.ZEPP_CLOUD, 75.1)
        val skipped = HealthConnectWriter(context).write(zepp)
        assertTrue(skipped is HealthConnectWriter.Result.SkippedLowerPriority)
        assertEquals(1, read(WeightRecord::class).size)

        // User moves Zepp to the top: the scale record is replaced.
        SettingsStore(context).setPriority(DataCategory.BODY, listOf(Source.ZEPP_CLOUD, Source.SCALE_BLE))
        assertEquals(HealthConnectWriter.Result.Written, HealthConnectWriter(context).write(zepp))
        assertEquals(listOf(75.1), read(WeightRecord::class).map { it.weight.inKilograms })
    }

    private suspend fun <T : Record> read(type: KClass<T>): List<T> =
        client.readRecords(ReadRecordsRequest(type, TimeRangeFilter.after(Instant.EPOCH))).records

    private fun expectedInstant(t: LocalDateTime) =
        t.truncatedTo(ChronoUnit.SECONDS).atZone(ZoneId.systemDefault()).toInstant()

    /** Mi Body Composition Scale 2 service-data payload (0x181B), stabilized, kg. */
    private fun advert(t: LocalDateTime, kg: Double, impedance: Int?): ByteArray {
        val raw = Math.round(kg * 200).toInt()
        val imp = impedance ?: 0
        return byteArrayOf(
            0x02, (if (impedance != null) 0x22 else 0x20).toByte(),
            (t.year and 0xFF).toByte(), (t.year shr 8).toByte(),
            t.monthValue.toByte(), t.dayOfMonth.toByte(), t.hour.toByte(), t.minute.toByte(), t.second.toByte(),
            (imp and 0xFF).toByte(), (imp shr 8).toByte(),
            (raw and 0xFF).toByte(), (raw shr 8).toByte(),
        )
    }
}
