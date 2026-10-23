package app.synchealth.hc

import app.synchealth.hc.scale.LastReading
import app.synchealth.hc.scale.MiScale2Parser
import app.synchealth.hc.scale.ReadingDeduper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class ReadingDeduperTest {
    private val zone = ZoneOffset.ofHours(7)
    private val now = Instant.parse("2025-10-23T00:30:20Z")

    @Test
    fun usesScaleClockWhenPlausible() {
        val r = MiScale2Parser.Reading(75.0, null, LocalDateTime.of(2025, 10, 23, 7, 30, 15))
        assertEquals(Instant.parse("2025-10-23T00:30:15Z"), ReadingDeduper.timeFor(r, null, now, zone))
    }

    @Test
    fun fallsBackToNowWhenScaleClockIsWrong() {
        val r = MiScale2Parser.Reading(75.0, null, LocalDateTime.of(2020, 1, 1, 0, 0))
        assertEquals(now, ReadingDeduper.timeFor(r, null, now, zone))
    }

    @Test
    fun scaleClockAheadOfPhoneUsesPhoneTime() {
        // Scale says 07:30:50 local, phone is at 07:30:20 local.
        val r = MiScale2Parser.Reading(75.0, null, LocalDateTime.of(2025, 10, 23, 7, 30, 50))
        assertEquals(now, ReadingDeduper.timeFor(r, null, now, zone))
    }

    @Test
    fun repeatedAdvertIsDropped() {
        val last = LastReading(now.minusSeconds(5), now.minusSeconds(5), 75.0, 480)
        assertNull(ReadingDeduper.timeFor(MiScale2Parser.Reading(75.0, 480, null), last, now, zone))
    }

    @Test
    fun impedanceFollowUpKeepsFirstTimestamp() {
        val first = now.minusSeconds(4)
        val last = LastReading(first, first, 75.0, null)
        assertEquals(first, ReadingDeduper.timeFor(MiScale2Parser.Reading(75.0, 480, null), last, now, zone))
    }

    @Test
    fun newSessionAfterGap() {
        val last = LastReading(now.minusSeconds(600), now.minusSeconds(600), 75.0, 480)
        assertEquals(now, ReadingDeduper.timeFor(MiScale2Parser.Reading(75.0, 480, null), last, now, zone))
    }
}
