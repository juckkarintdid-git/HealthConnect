package app.synchealth.hc.model

import java.time.Instant

data class BodyComposition(
    val bodyFatPercent: Double,
    val leanBodyMassKg: Double,
    val boneMassKg: Double,
    val bodyWaterMassKg: Double,
    val bmrKcalPerDay: Double,
)

data class BodyMeasurement(
    val time: Instant,
    val source: Source,
    val weightKg: Double,
    val impedanceOhm: Int? = null,
    val composition: BodyComposition? = null,
)
