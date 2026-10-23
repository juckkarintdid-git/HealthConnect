package app.synchealth.hc

import app.synchealth.hc.scale.MiScale2Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class MiScale2ParserTest {

    private fun payload(ctrl0: Int, ctrl1: Int, impedance: Int, rawWeight: Int) = byteArrayOf(
        ctrl0.toByte(), ctrl1.toByte(),
        0xE9.toByte(), 0x07, // 2025
        10, 23, 7, 30, 15, // Oct 23 07:30:15
        (impedance and 0xFF).toByte(), (impedance shr 8).toByte(),
        (rawWeight and 0xFF).toByte(), (rawWeight shr 8).toByte(),
    )

    @Test
    fun stabilizedKgWithImpedance() {
        // ctrl1: stabilized (bit5) + impedance (bit1)
        val r = MiScale2Parser.parse(payload(0x02, 0x22, 480, 15000))
        assertNotNull(r)
        assertEquals(75.0, r!!.weightKg, 0.001)
        assertEquals(480, r.impedanceOhm)
        assertEquals(LocalDateTime.of(2025, 10, 23, 7, 30, 15), r.scaleTime)
    }

    @Test
    fun stabilizedWithoutImpedanceFlag() {
        val r = MiScale2Parser.parse(payload(0x02, 0x20, 480, 15000))
        assertNull(r!!.impedanceOhm)
    }

    @Test
    fun lbsUnit() {
        val r = MiScale2Parser.parse(payload(0x03, 0x20, 0, 16535))
        assertEquals(75.0, r!!.weightKg, 0.01)
    }

    @Test
    fun ignoresUnstableAndRemoved() {
        assertNull(MiScale2Parser.parse(payload(0x02, 0x02, 480, 15000)))
        assertNull(MiScale2Parser.parse(payload(0x02, 0xA2, 480, 15000)))
    }

    @Test
    fun ignoresWrongLength() {
        assertNull(MiScale2Parser.parse(ByteArray(10)))
    }
}
