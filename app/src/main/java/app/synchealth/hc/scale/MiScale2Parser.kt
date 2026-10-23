package app.synchealth.hc.scale

import java.time.LocalDateTime

/**
 * Decodes the 13-byte payload the Mi Body Composition Scale 2 (XMTZC05HM) broadcasts as
 * BLE service data under UUID 0x181B. Layout follows openScale's MiScale2 driver.
 */
object MiScale2Parser {
    const val SERVICE_UUID = "0000181b-0000-1000-8000-00805f9b34fb"

    data class Reading(
        val weightKg: Double,
        val impedanceOhm: Int?,
        /** Scale clock (local time). Null when the scale reports its date as invalid. */
        val scaleTime: LocalDateTime?,
    )

    /** Returns a reading only for a stabilized measurement with the user still on the scale. */
    fun parse(data: ByteArray): Reading? {
        if (data.size != 13) return null
        val ctrl0 = data[0].toInt()
        val ctrl1 = data[1].toInt()
        val isLbs = ctrl0 bit 0
        val hasImpedance = ctrl1 bit 1
        val isStabilized = ctrl1 bit 5
        val isCatty = ctrl1 bit 6
        val isWeightRemoved = ctrl1 bit 7
        if (!isStabilized || isWeightRemoved) return null

        val rawWeight = data.u16(11)
        val weightKg = when {
            isLbs -> rawWeight / 100.0 * 0.45359237
            isCatty -> rawWeight / 100.0 * 0.5
            else -> rawWeight / 200.0
        }
        if (weightKg <= 0.0 || weightKg > 250.0) return null

        val impedance = data.u16(9).takeIf { hasImpedance && it in 1..2999 }

        val scaleTime = runCatching {
            LocalDateTime.of(data.u16(2), data.u8(4), data.u8(5), data.u8(6), data.u8(7), data.u8(8))
        }.getOrNull()

        return Reading(weightKg, impedance, scaleTime)
    }

    private infix fun Int.bit(n: Int) = (this shr n) and 1 == 1
    private fun ByteArray.u8(i: Int) = this[i].toInt() and 0xFF
    private fun ByteArray.u16(i: Int) = u8(i) or (u8(i + 1) shl 8)
}
