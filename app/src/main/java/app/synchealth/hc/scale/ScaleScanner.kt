package app.synchealth.hc.scale

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat

/**
 * Registers a system-managed BLE scan that wakes [ScaleScanReceiver] whenever the scale
 * advertises, even when the app is not running. No foreground service needed.
 */
object ScaleScanner {

    fun hasPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner ?: return false
        val filter = ScanFilter.Builder()
            .setServiceData(ParcelUuid.fromString(MiScale2Parser.SERVICE_UUID), byteArrayOf(), byteArrayOf())
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        return scanner.startScan(listOf(filter), settings, pendingIntent(context)) == 0
    }

    @SuppressLint("MissingPermission")
    fun stop(context: Context) {
        if (!hasPermission(context)) return
        context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
            ?.stopScan(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ScaleScanReceiver::class.java).setAction(ACTION_SCAN_RESULT),
        // Mutable: the system adds the scan results as extras.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    const val ACTION_SCAN_RESULT = "app.synchealth.hc.SCALE_SCAN_RESULT"
}
