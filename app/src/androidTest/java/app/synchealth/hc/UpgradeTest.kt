package app.synchealth.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.synchealth.hc.hc.HealthConnectWriter
import app.synchealth.hc.scale.ScaleIngestor
import app.synchealth.hc.scale.Sex
import app.synchealth.hc.settings.SettingsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Two phases run by .github/scripts/upgrade-test.sh: seed against the previous release, then
 * verify against the new build installed over it. Skipped in normal test runs; the script turns
 * them on with the instrumentation argument `upgradePhase`.
 *
 * Only uses entry points that must stay compatible across versions: SharedPreferences keys,
 * SettingsStore, ScaleIngestor.ingest and Health Connect itself.
 */
@RunWith(AndroidJUnit4::class)
class UpgradeTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scale = "C8:47:8C:00:00:02"

    // Separate prefs file owned by the test; survives the upgrade like the app's own data.
    private val testPrefs = context.getSharedPreferences("upgrade_test", Context.MODE_PRIVATE)

    @Before
    fun onlyWhenDrivenByScript() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgradePhase") != null)
    }

    @Test
    fun seedBeforeUpgrade() = runBlocking {
        grantPermissions()
        val seedTime = LocalDateTime.now().minusHours(1).withNano(0)
        testPrefs.edit().putLong("seed_epoch", seedTime.atZone(ZoneId.systemDefault()).toEpochSecond()).commit()
        val client = HealthConnectClient.getOrCreate(context)
        BODY_TYPES.forEach { client.deleteRecords(it, TimeRangeFilter.after(Instant.EPOCH)) }
        listOf("settings", "written_index").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        val store = SettingsStore(context)
        store.saveProfile(170f, 1990, Sex.FEMALE)
        store.scaleScanEnabled = true
        assertNotNull(ScaleIngestor.ingest(context, scale, advert(seedTime)))
    }

    @Test
    fun verifyAfterUpgrade() = runBlocking {
        grantPermissions()
        val seedTime = LocalDateTime.ofInstant(
            Instant.ofEpochSecond(testPrefs.getLong("seed_epoch", 0)), ZoneId.systemDefault(),
        )
        val store = SettingsStore(context)
        assertNotNull("profile lost on upgrade", store.profile)
        assertEquals(true, store.scaleScanEnabled)
        assertEquals("trusted scale lost on upgrade", scale, store.boundScaleAddress)
        assertNotNull("last reading lost on upgrade", store.lastReading)

        // The same measurement arriving again after the update must not duplicate.
        ScaleIngestor.ingest(context, scale, advert(seedTime))
        val weights = HealthConnectClient.getOrCreate(context)
            .readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(Instant.EPOCH))).records
        assertEquals("duplicate or missing weight after upgrade", 1, weights.size)
        assertNull(ScaleIngestor.ingest(context, "AA:BB:CC:DD:EE:FF", advert(seedTime)))
    }

    private fun grantPermissions() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        HealthConnectWriter.BODY_PERMISSIONS.forEach { automation.grantRuntimePermission(context.packageName, it) }
        // Read permissions exist only in debug builds; an older version may not declare them.
        BODY_TYPES.map { HealthPermission.getReadPermission(it) }.forEach {
            runCatching { automation.grantRuntimePermission(context.packageName, it) }
        }
    }

    /** Stabilized Mi Scale 2 advert, 70 kg with impedance, at [t] on the scale clock. */
    private fun advert(t: LocalDateTime): ByteArray {
        val raw = 70 * 200
        val imp = 520
        return byteArrayOf(
            0x02, 0x22,
            (t.year and 0xFF).toByte(), (t.year shr 8).toByte(),
            t.monthValue.toByte(), t.dayOfMonth.toByte(), t.hour.toByte(), t.minute.toByte(), t.second.toByte(),
            (imp and 0xFF).toByte(), (imp shr 8).toByte(),
            (raw and 0xFF).toByte(), (raw shr 8).toByte(),
        )
    }

    private companion object {
        val BODY_TYPES = listOf(WeightRecord::class, BodyFatRecord::class, LeanBodyMassRecord::class)
    }
}
