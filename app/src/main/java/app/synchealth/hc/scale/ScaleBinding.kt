package app.synchealth.hc.scale

import app.synchealth.hc.settings.SettingsStore

/**
 * Any nearby device can advertise 0x181B service data, so only one scale is trusted:
 * the first one that sends a valid stabilized reading. "Forget scale" in the UI resets it.
 */
object ScaleBinding {
    fun accept(settings: SettingsStore, address: String): Boolean {
        val bound = settings.boundScaleAddress
        if (bound == null) {
            settings.boundScaleAddress = address
            return true
        }
        return bound.equals(address, ignoreCase = true)
    }
}
