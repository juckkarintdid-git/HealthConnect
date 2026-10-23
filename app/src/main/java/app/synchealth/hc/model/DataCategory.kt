package app.synchealth.hc.model

/** Groups of data that share one source-priority list. */
enum class DataCategory(val id: String, val label: String, val defaultSources: List<Source>) {
    BODY("body", "Weight & body composition", listOf(Source.SCALE_BLE, Source.ZEPP_CLOUD, Source.HUAWEI_EXPORT)),
    ACTIVITY("activity", "Steps, heart rate & workouts", listOf(Source.HUAWEI_EXPORT)),
    SLEEP("sleep", "Sleep", listOf(Source.HUAWEI_EXPORT)),
}
