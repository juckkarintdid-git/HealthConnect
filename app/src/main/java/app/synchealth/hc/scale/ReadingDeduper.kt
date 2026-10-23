package app.synchealth.hc.scale

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/** The last reading we stored, used to collapse the burst of identical adverts a scale sends. */
data class LastReading(val time: Instant, val receivedAt: Instant, val weightKg: Double, val impedanceOhm: Int?)

object ReadingDeduper {
    private val SAME_SESSION = Duration.ofMinutes(3)
    private val MAX_CLOCK_DRIFT = Duration.ofDays(1)

    /**
     * Returns the timestamp to store [reading] under, or null when it adds nothing new.
     * A follow-up advert that only adds impedance keeps the first timestamp so it upserts the
     * same Health Connect records.
     */
    fun timeFor(
        reading: MiScale2Parser.Reading,
        last: LastReading?,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Instant? {
        if (last != null &&
            Duration.between(last.receivedAt, now) < SAME_SESSION &&
            abs(last.weightKg - reading.weightKg) < 0.05
        ) {
            val addsImpedance = last.impedanceOhm == null && reading.impedanceOhm != null
            return if (addsImpedance) last.time else null
        }
        return scaleInstant(reading.scaleTime, now, zone) ?: now
    }

    private fun scaleInstant(scaleTime: LocalDateTime?, now: Instant, zone: ZoneId): Instant? {
        val t = scaleTime?.atZone(zone)?.toInstant() ?: return null
        if (Duration.between(t, now).abs() >= MAX_CLOCK_DRIFT) return null
        // Health Connect rejects future timestamps, so a scale clock running ahead uses the phone's time.
        return if (t.isAfter(now)) now else t
    }
}
