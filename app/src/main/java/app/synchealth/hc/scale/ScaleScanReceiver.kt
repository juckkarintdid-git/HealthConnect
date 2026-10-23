package app.synchealth.hc.scale

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScaleScanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val results = IntentCompat.getParcelableArrayListExtra(
            intent, BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java,
        ) ?: return
        val uuid = ParcelUuid.fromString(MiScale2Parser.SERVICE_UUID)
        val adverts = results.mapNotNull { r ->
            r.scanRecord?.getServiceData(uuid)?.let { r.device.address to it }
        }
        if (adverts.isEmpty()) return

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for ((address, data) in adverts) ScaleIngestor.ingest(app, address, data)
            } finally {
                pending.finish()
            }
        }
    }
}
