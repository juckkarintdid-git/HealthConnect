package app.synchealth.hc

import app.synchealth.hc.model.Source
import app.synchealth.hc.priority.PriorityResolver
import app.synchealth.hc.priority.PriorityResolver.Existing
import app.synchealth.hc.priority.RecordIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityResolverTest {
    private val order = listOf(Source.SCALE_BLE, Source.ZEPP_CLOUD, Source.HUAWEI_EXPORT)

    @Test
    fun writesWhenNothingOverlaps() {
        val d = PriorityResolver.resolve(Source.ZEPP_CLOUD, emptyList(), order)
        assertTrue(d.write)
        assertTrue(d.deleteClientRecordIds.isEmpty())
    }

    @Test
    fun skipsWhenHigherPriorityExists() {
        val d = PriorityResolver.resolve(Source.ZEPP_CLOUD, listOf(Existing("scale_ble:1", Source.SCALE_BLE)), order)
        assertFalse(d.write)
    }

    @Test
    fun replacesLowerPriority() {
        val d = PriorityResolver.resolve(Source.SCALE_BLE, listOf(Existing("zepp_cloud:1", Source.ZEPP_CLOUD)), order)
        assertTrue(d.write)
        assertEquals(listOf("zepp_cloud:1"), d.deleteClientRecordIds)
    }

    @Test
    fun sameSourceIsNotAConflict() {
        val d = PriorityResolver.resolve(Source.SCALE_BLE, listOf(Existing("scale_ble:1", Source.SCALE_BLE)), order)
        assertTrue(d.write)
        assertTrue(d.deleteClientRecordIds.isEmpty())
    }

    @Test
    fun recordIdsRoundTrip() {
        val base = RecordIds.base(Source.ZEPP_CLOUD, 123)
        assertEquals(Source.ZEPP_CLOUD, RecordIds.sourceOf(RecordIds.record(base, "weight")))
    }
}
