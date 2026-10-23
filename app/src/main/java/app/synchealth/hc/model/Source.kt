package app.synchealth.hc.model

/** Where a measurement came from. [id] is persisted (settings, clientRecordId) so never rename it. */
enum class Source(val id: String, val label: String) {
    SCALE_BLE("scale_ble", "Mi Scale (Bluetooth)"),
    ZEPP_CLOUD("zepp_cloud", "Zepp Life cloud"),
    HUAWEI_EXPORT("huawei_export", "Huawei Health export");

    companion object {
        fun fromId(id: String): Source? = entries.firstOrNull { it.id == id }
    }
}
