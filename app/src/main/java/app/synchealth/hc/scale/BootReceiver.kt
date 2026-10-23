package app.synchealth.hc.scale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.synchealth.hc.settings.SettingsStore

/** PendingIntent scans do not survive a reboot or app update, so register again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (SettingsStore(context).scaleScanEnabled) ScaleScanner.start(context)
    }
}
