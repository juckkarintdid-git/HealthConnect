package app.synchealth.hc.scale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.synchealth.hc.settings.SettingsStore

/** PendingIntent scans do not survive a reboot or app update, so register again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for BOOT_COMPLETED; ignore explicit intents other apps might send.
        if (intent.action !in HANDLED_ACTIONS) return
        if (SettingsStore(context).scaleScanEnabled) ScaleScanner.start(context)
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
