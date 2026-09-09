package com.example.traceher

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import java.util.UUID

class BleForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "traceher_service"
        private const val TAG = "TraceHerService"
        private const val NOTIFICATION_ID = 1

        private const val SAVED_DEVICE_KEY = "saved_ble_device"

        private const val SERVICE_UUID =
            "12345678-1234-1234-1234-1234567890AB"

        private const val CHARACTERISTIC_UUID =
            "87654321-4321-4321-4321-BA0987654321"

        // Standard Client Characteristic Configuration Descriptor
        private const val CCCD_UUID =
            "00002902-0000-1000-8000-00805f9b34fb"
    }

    private var scanner: BluetoothLeScanner? = null
    private var targetAddress: String? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var isScanning = false
    private var isConnecting = false

    private val handler = Handler(Looper.getMainLooper())

    // =========================================================
    // SERVICE CREATED
    // =========================================================

    override fun onCreate() {

        super.onCreate()

        Log.e(TAG, "onCreate()")

        createNotificationChannel()

        val notification = Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("TraceHer")
            .setContentText(
                "Monitoring emergency device..."
            )
            .setSmallIcon(
                android.R.drawable.stat_sys_data_bluetooth
            )
            .setOngoing(true)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification
        )

        Log.e(TAG, "Foreground Started")
    }

    // =========================================================
    // SERVICE START
    // =========================================================

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

        var deviceAddress = prefs.getString(
            "flutter.$SAVED_DEVICE_KEY",
            null
        )

        if (deviceAddress == null) {

            deviceAddress = prefs.getString(
                SAVED_DEVICE_KEY,
                null
            )
        }

        Log.e(
            TAG,
            "Saved BLE Address = $deviceAddress"
        )

        if (deviceAddress == null) {

            Log.e(
                TAG,
                "No saved BLE device"
            )

        } else {

            targetAddress = deviceAddress

            Log.e(
                TAG,
                "Target Address Set = $targetAddress"
            )

            /*
             * Start scan only if we are not already connected,
             * connecting, or scanning.
             */

            if (
                bluetoothGatt == null &&
                !isConnecting &&
                !isScanning
            ) {

                startBleScan()
            } else {

                Log.e(
                    TAG,
                    "BLE already active - no new scan required"
                )
            }
        }

        /*
         * START_STICKY tells Android that this service is intended
         * to remain running if the process is recreated.
         */

        return START_STICKY
    }

    // =========================================================
    // APP TASK REMOVED
    // =========================================================

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {

        super.onTaskRemoved(rootIntent)

        Log.e(
            TAG,
            "onTaskRemoved() - foreground service still running"
        )

        /*
         * We intentionally do NOT stop the BLE service here.
         *
         * The Flutter application can disappear from Recents,
         * while this foreground service continues monitoring BLE.
         */

        if (
            bluetoothGatt == null &&
            targetAddress != null &&
            !isScanning &&
            !isConnecting
        ) {

            Log.e(
                TAG,
                "No active GATT connection. Starting BLE scan..."
            )

            startBleScan()
        }
    }

    // =========================================================
    // SERVICE DESTROYED
    // =========================================================

    override fun onDestroy() {

        Log.e(TAG, "onDestroy()")

        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }

        isScanning = false

        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {
        }

        bluetoothGatt = null
        isConnecting = false

        super.onDestroy()
    }

    // =========================================================
    // SERVICE BINDING
    // =========================================================

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }

    // =========================================================
    // START BLE SCAN
    // =========================================================

    private fun startBleScan() {

        if (isScanning) {

            Log.e(
                TAG,
                "Scan already running"
            )

            return
        }

        if (isConnecting) {

            Log.e(
                TAG,
                "Connection attempt already running"
            )

            return
        }

        if (bluetoothGatt != null) {

            Log.e(
                TAG,
                "GATT already exists - scan not required"
            )

            return
        }

        Log.e(
            TAG,
            "Starting BLE Scan..."
        )

        val adapter =
            BluetoothAdapter.getDefaultAdapter()

        if (adapter == null) {

            Log.e(
                TAG,
                "Bluetooth not supported"
            )

            return
        }

        if (!adapter.isEnabled) {

            Log.e(
                TAG,
                "Bluetooth is OFF"
            )

            return
        }

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.e(
                TAG,
                "BLUETOOTH_SCAN permission missing"
            )

            return
        }

        scanner = adapter.bluetoothLeScanner

        if (scanner == null) {

            Log.e(
                TAG,
                "BluetoothLeScanner is null"
            )

            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(
                ScanSettings.SCAN_MODE_LOW_LATENCY
            )
            .build()

        try {

            scanner?.startScan(
                null,
                settings,
                scanCallback
            )

            isScanning = true

            Log.e(
                TAG,
                "BLE Scan Started"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to start BLE scan: ${e.message}"
            )

            isScanning = false
        }
    }

    // =========================================================
    // BLE SCAN CALLBACK
    // =========================================================

    private val scanCallback =
        object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {

                val device = result.device

                if (
                    ActivityCompat.checkSelfPermission(
                        this@BleForegroundService,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                    Log.e(
                        TAG,
                        "BLUETOOTH_CONNECT permission missing"
                    )

                    return
                }

                Log.e(
                    TAG,
                    "Found: ${device.name} ${device.address}"
                )

                if (
                    targetAddress != null &&
                    device.address.equals(
                        targetAddress,
                        ignoreCase = true
                    )
                ) {

                    Log.e(
                        TAG,
                        "Target ESP32 Found!"
                    )

                    stopBleScan()

                    connectToDevice(device)
                }
            }

            override fun onScanFailed(
                errorCode: Int
            ) {

                isScanning = false

                Log.e(
                    TAG,
                    "Scan Failed : $errorCode"
                )

                /*
                 * Try again after a short delay.
                 */

                scheduleReconnect()
            }
        }

    // =========================================================
    // STOP BLE SCAN
    // =========================================================

    private fun stopBleScan() {

        if (!isScanning) {
            return
        }

        try {

            if (
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {

                scanner?.stopScan(scanCallback)
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Stop scan error: ${e.message}"
            )
        }

        isScanning = false

        Log.e(
            TAG,
            "BLE Scan Stopped"
        )
    }

    // =========================================================
    // CONNECT TO ESP32
    // =========================================================

    private fun connectToDevice(
        device: BluetoothDevice
    ) {

        if (isConnecting) {

            Log.e(
                TAG,
                "Already connecting"
            )

            return
        }

        Log.e(
            TAG,
            "Connecting to ESP32..."
        )

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.e(
                TAG,
                "BLUETOOTH_CONNECT permission missing"
            )

            return
        }

        isConnecting = true

        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {
        }

        bluetoothGatt = null

        try {

            bluetoothGatt = device.connectGatt(
                this,
                false,
                gattCallback
            )

            Log.e(
                TAG,
                "connectGatt() called"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "connectGatt error: ${e.message}"
            )

            isConnecting = false

            scheduleReconnect()
        }
    }

    // =========================================================
    // GATT CALLBACK
    // =========================================================

    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                super.onConnectionStateChange(
                    gatt,
                    status,
                    newState
                )

                Log.e(
                    TAG,
                    "GATT state changed. Status=$status State=$newState"
                )

                // =================================================
                // CONNECTED
                // =================================================

                if (
                    newState ==
                    BluetoothProfile.STATE_CONNECTED
                ) {

                    isConnecting = false

                    Log.e(
                        TAG,
                        "GATT CONNECTED"
                    )

                    if (
                        ActivityCompat.checkSelfPermission(
                            this@BleForegroundService,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {

                        Log.e(
                            TAG,
                            "BLUETOOTH_CONNECT permission missing"
                        )

                        return
                    }

                    /*
                     * Ask Android to discover the ESP32 services.
                     */

                    val result =
                        gatt.discoverServices()

                    Log.e(
                        TAG,
                        "Service discovery started = $result"
                    )

                }

                // =================================================
                // DISCONNECTED
                // =================================================

                else if (
                    newState ==
                    BluetoothProfile.STATE_DISCONNECTED
                ) {

                    isConnecting = false

                    Log.e(
                        TAG,
                        "GATT DISCONNECTED"
                    )

                    /*
                     * Status 8 is GATT connection timeout.
                     */

                    if (status == 8) {

                        Log.e(
                            TAG,
                            "GATT connection timeout detected"
                        )
                    }

                    try {
                        gatt.close()
                    } catch (_: Exception) {
                    }

                    if (bluetoothGatt == gatt) {
                        bluetoothGatt = null
                    }

                    /*
                     * Automatically search for ESP32 again.
                     */

                    scheduleReconnect()
                }
            }

            // =====================================================
            // SERVICES DISCOVERED
            // =====================================================

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                super.onServicesDiscovered(
                    gatt,
                    status
                )

                Log.e(
                    TAG,
                    "Services discovered. Status=$status"
                )

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    Log.e(
                        TAG,
                        "Service discovery failed"
                    )

                    scheduleReconnect()

                    return
                }

                // =================================================
                // FIND TRACEHER SERVICE
                // =================================================

                val service = gatt.getService(
                    UUID.fromString(
                        SERVICE_UUID
                    )
                )

                if (service == null) {

                    Log.e(
                        TAG,
                        "TraceHer service NOT found"
                    )

                    scheduleReconnect()

                    return
                }

                Log.e(
                    TAG,
                    "TraceHer service found"
                )

                // =================================================
                // FIND CHARACTERISTIC
                // =================================================

                val characteristic =
                    service.getCharacteristic(
                        UUID.fromString(
                            CHARACTERISTIC_UUID
                        )
                    )

                if (characteristic == null) {

                    Log.e(
                        TAG,
                        "TraceHer characteristic NOT found"
                    )

                    scheduleReconnect()

                    return
                }

                Log.e(
                    TAG,
                    "TraceHer characteristic found"
                )

                if (
                    ActivityCompat.checkSelfPermission(
                        this@BleForegroundService,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                    Log.e(
                        TAG,
                        "BLUETOOTH_CONNECT permission missing"
                    )

                    return
                }

                // =================================================
                // ENABLE LOCAL NOTIFICATION
                // =================================================

                val notificationEnabled =
                    gatt.setCharacteristicNotification(
                        characteristic,
                        true
                    )

                Log.e(
                    TAG,
                    "Local notification enabled = $notificationEnabled"
                )

                // =================================================
                // WRITE CCCD
                // =================================================

                val descriptor =
                    characteristic.getDescriptor(
                        UUID.fromString(
                            CCCD_UUID
                        )
                    )

                if (descriptor == null) {

                    Log.e(
                        TAG,
                        "CCCD descriptor NOT found"
                    )

                    return
                }

                Log.e(
                    TAG,
                    "CCCD descriptor found"
                )

                descriptor.value =
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                val descriptorWriteStarted =
                    gatt.writeDescriptor(
                        descriptor
                    )

                Log.e(
                    TAG,
                    "CCCD write started = $descriptorWriteStarted"
                )
            }

            // =====================================================
            // DESCRIPTOR WRITE COMPLETE
            // =====================================================

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {

                super.onDescriptorWrite(
                    gatt,
                    descriptor,
                    status
                )

                Log.e(
                    TAG,
                    "CCCD descriptor write complete. Status=$status"
                )

                if (
                    status ==
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    Log.e(
                        TAG,
                        "BLE NOTIFICATIONS READY"
                    )

                } else {

                    Log.e(
                        TAG,
                        "BLE notification setup FAILED"
                    )
                }
            }

            // =====================================================
            // BLE NOTIFICATION RECEIVED
            // =====================================================

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: android.bluetooth.BluetoothGattCharacteristic,
                value: ByteArray
            ) {

                super.onCharacteristicChanged(
                    gatt,
                    characteristic,
                    value
                )

                val message =
                    value.toString(Charsets.UTF_8)
                        .trim()

                Log.e(
                    TAG,
                    "BLE MESSAGE RECEIVED = $message"
                )

                if (
                    message.equals(
                        "SOS",
                        ignoreCase = true
                    )
                ) {

                    Log.e(
                        TAG,
                        "!!!!!!!! SOS RECEIVED !!!!!!!!"
                    )

                    /*
                     * SMS will be added later.
                     *
                     * For this step we ONLY prove that
                     * the native foreground service receives
                     * the ESP32 SOS.
                     */
                }
            }
        }

    // =========================================================
    // RECONNECT
    // =========================================================

    private fun scheduleReconnect() {

        if (targetAddress == null) {

            Log.e(
                TAG,
                "Cannot reconnect - no target address"
            )

            return
        }

        if (isScanning) {

            Log.e(
                TAG,
                "Reconnect skipped - scan already running"
            )

            return
        }

        if (isConnecting) {

            Log.e(
                TAG,
                "Reconnect skipped - connection in progress"
            )

            return
        }

        if (bluetoothGatt != null) {

            Log.e(
                TAG,
                "Reconnect skipped - GATT already exists"
            )

            return
        }

        Log.e(
            TAG,
            "Scheduling BLE reconnect in 3 seconds..."
        )

        handler.postDelayed({

            if (
                bluetoothGatt == null &&
                !isScanning &&
                !isConnecting &&
                targetAddress != null
            ) {

                startBleScan()
            }

        }, 3000)
    }

    // =========================================================
    // NOTIFICATION CHANNEL
    // =========================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "TraceHer Service",
                NotificationManager.IMPORTANCE_LOW
            )

            channel.description =
                "Keeps TraceHer connected to the emergency device."

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }
}