package com.example.traceher

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat

class BleForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "traceher_service"
        private const val TAG = "TraceHerService"
    }

    private var scanner: BluetoothLeScanner? = null
    private var targetAddress: String? = null

    override fun onCreate() {
        super.onCreate()

        Log.e(TAG, "onCreate()")

        createNotificationChannel()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("TraceHer")
            .setContentText("Monitoring emergency device...")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()

        startForeground(1, notification)

        Log.e(TAG, "Foreground Started")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.e(TAG, "onStartCommand()")

        val prefs = getSharedPreferences(
            "FlutterSharedPreferences",
            Context.MODE_PRIVATE
        )

        val deviceAddress = prefs.getString(
            "flutter.saved_ble_device",
            null
        )

        Log.e(TAG, "Saved BLE Address = $deviceAddress")

        if (deviceAddress == null) {

            Log.e(TAG, "No saved BLE device")

        } else {

            targetAddress = deviceAddress

            startBleScan()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.e(TAG, "onTaskRemoved()")
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }

        Log.e(TAG, "onDestroy()")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startBleScan() {

        Log.e(TAG, "Starting BLE Scan...")

        val adapter = BluetoothAdapter.getDefaultAdapter()

        if (adapter == null) {
            Log.e(TAG, "Bluetooth not supported")
            return
        }

        if (!adapter.isEnabled) {
            Log.e(TAG, "Bluetooth is OFF")
            return
        }

        scanner = adapter.bluetoothLeScanner

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.e(TAG, "BLUETOOTH_SCAN permission missing")
            return
        }

        scanner?.startScan(
            null,
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build(),
            scanCallback
        )
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {

            val device = result.device

            Log.e(
                TAG,
                "Found: ${device.name} ${device.address}"
            )

            if (device.address == targetAddress) {

                Log.e(TAG, "Target ESP32 Found!")

                scanner?.stopScan(this)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan Failed : $errorCode")
        }
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "TraceHer Service",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }
}