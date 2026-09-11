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
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class BleForegroundService : Service() {

    companion object {

        private const val TAG = "TraceHerService"

        private const val CHANNEL_ID = "traceher_service"
        private const val NOTIFICATION_ID = 1001

        private const val DEVICE_NAME = "traceHer"

        private const val SERVICE_UUID =
            "12345678-1234-1234-1234-1234567890AB"

        private const val CHARACTERISTIC_UUID =
            "87654321-4321-4321-4321-BA0987654321"

        private const val CCCD_UUID =
            "00002902-0000-1000-8000-00805f9b34fb"

        private const val EMERGENCY_CONTACTS_KEY =
            "emergency_contacts"

        private const val SAVED_DEVICE_KEY =
            "saved_ble_device"

        private const val LATITUDE_KEY =
            "traceher_last_latitude"

        private const val LONGITUDE_KEY =
            "traceher_last_longitude"

        private const val LOCATION_TIME_KEY =
            "traceher_last_location_time"

        private const val RECONNECT_DELAY = 3000L

        private const val SCAN_TIMEOUT = 8000L

        private var instance: BleForegroundService? = null

        @JvmStatic
        fun isBleConnected(): Boolean {
            return instance?.isConnected == true
        }
    }

    // ============================================================
    // BLUETOOTH
    // ============================================================

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // ============================================================
    // STATE
    // ============================================================

    private var isScanning = false
    private var isConnected = false
    private var notificationsReady = false

    private var connectionInProgress = false
    private var reconnectScheduled = false

    // ============================================================
    // HANDLER
    // ============================================================

    private val handler =
        Handler(Looper.getMainLooper())

    private var reconnectRunnable: Runnable? = null
    private var scanTimeoutRunnable: Runnable? = null

    // ============================================================
    // UUID
    // ============================================================

    private val serviceUUID =
        UUID.fromString(SERVICE_UUID)

    private val characteristicUUID =
        UUID.fromString(CHARACTERISTIC_UUID)

    private val cccdUUID =
        UUID.fromString(CCCD_UUID)

    // ============================================================
    // SERVICE CREATED
    // ============================================================

    override fun onCreate() {

        super.onCreate()

        instance = this

        Log.e(
            TAG,
            "========================================"
        )

        Log.e(
            TAG,
            "BleForegroundService CREATED"
        )

        Log.e(
            TAG,
            "========================================"
        )

        createNotificationChannel()

        val notification =
            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle("TraceHer")
                .setContentText(
                    "TraceHer is running in the background"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_info_details
                )
                .setOngoing(true)
                .build()

        startForeground(
            NOTIFICATION_ID,
            notification
        )

        initializeBluetooth()
    }

    // ============================================================
    // INITIALIZE BLUETOOTH
    // ============================================================

    private fun initializeBluetooth() {

        try {

            val bluetoothManager =
                getSystemService(
                    Context.BLUETOOTH_SERVICE
                ) as BluetoothManager

            bluetoothAdapter =
                bluetoothManager.adapter

            scanner =
                bluetoothAdapter?.bluetoothLeScanner

            Log.e(
                TAG,
                "Bluetooth initialized"
            )

            Log.e(
                TAG,
                "Bluetooth adapter available = ${
                    bluetoothAdapter != null
                }"
            )

            Log.e(
                TAG,
                "Bluetooth enabled = ${
                    bluetoothAdapter?.isEnabled
                }"
            )

            Log.e(
                TAG,
                "BLE scanner available = ${
                    scanner != null
                }"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Bluetooth initialization failed",
                e
            )

            bluetoothAdapter = null
            scanner = null
        }
    }

    // ============================================================
    // SERVICE START COMMAND
    // ============================================================

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.e(
            TAG,
            "========================================"
        )

        Log.e(
            TAG,
            "onStartCommand()"
        )

        Log.e(
            TAG,
            "isConnected = $isConnected"
        )

        Log.e(
            TAG,
            "isScanning = $isScanning"
        )

        Log.e(
            TAG,
            "connectionInProgress = $connectionInProgress"
        )

        Log.e(
            TAG,
            "========================================"
        )

        initializeBluetooth()

        ensureBleMonitoring()

        return START_STICKY
    }

    // ============================================================
    // ENSURE BLE MONITORING
    // ============================================================

    private fun ensureBleMonitoring() {

        if (isConnected) {

            Log.e(
                TAG,
                "BLE already connected"
            )

            return
        }

        if (connectionInProgress) {

            Log.e(
                TAG,
                "BLE connection already in progress"
            )

            return
        }

        if (isScanning) {

            Log.e(
                TAG,
                "BLE scan already running"
            )

            return
        }

        val savedAddress =
            getSavedDeviceAddress()

        if (savedAddress.isNullOrBlank()) {

            Log.e(
                TAG,
                "No saved BLE device address"
            )

            return
        }

        val adapter =
            bluetoothAdapter

        if (adapter == null) {

            Log.e(
                TAG,
                "Bluetooth adapter unavailable"
            )

            scheduleReconnect()

            return
        }

        if (!adapter.isEnabled) {

            Log.e(
                TAG,
                "Bluetooth is disabled"
            )

            scheduleReconnect()

            return
        }

        Log.e(
            TAG,
            "========================================"
        )

        Log.e(
            TAG,
            "STARTING DIRECT BLE CONNECTION"
        )

        Log.e(
            TAG,
            "Saved device address = $savedAddress"
        )

        Log.e(
            TAG,
            "========================================"
        )

        try {

            val device =
                adapter.getRemoteDevice(
                    savedAddress
                )

            Log.e(
                TAG,
                "Remote BluetoothDevice created"
            )

            Log.e(
                TAG,
                "Device address = ${device.address}"
            )

            try {

                Log.e(
                    TAG,
                    "Device name = ${device.name}"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Could not read device name",
                    e
                )
            }

            connectToDevice(device)

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to create remote BLE device",
                e
            )

            scheduleReconnect()
        }
    }

    // ============================================================
    // GET SAVED DEVICE ADDRESS
    // ============================================================

    private fun getSavedDeviceAddress(): String? {

        val prefs =
            getSharedPreferences(
                "FlutterSharedPreferences",
                Context.MODE_PRIVATE
            )

        val savedAddress =
            prefs.getString(
                "flutter.$SAVED_DEVICE_KEY",
                null
            )

        Log.e(
            TAG,
            "Saved BLE device = $savedAddress"
        )

        return savedAddress
    }

    // ============================================================
    // TASK REMOVED
    // ============================================================

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {

        Log.e(
            TAG,
            "========================================"
        )

        Log.e(
            TAG,
            "onTaskRemoved()"
        )

        Log.e(
            TAG,
            "Foreground service should continue"
        )

        Log.e(
            TAG,
            "========================================"
        )

        /*
         * Do NOT stop the BLE service when the Flutter
         * application is removed from Recents.
         *
         * If BLE is disconnected, immediately try to
         * reconnect using the saved Bluetooth address.
         */

        if (!isConnected &&
            !connectionInProgress &&
            !isScanning
        ) {

            Log.e(
                TAG,
                "Task removed - restarting BLE monitoring"
            )

            ensureBleMonitoring()

        } else {

            Log.e(
                TAG,
                "Task removed - BLE monitoring already active"
            )
        }

        super.onTaskRemoved(rootIntent)
    }

    // ============================================================
    // START BLE SCAN
    // ============================================================

    private fun startBleScan(
        address: String
    ) {

        if (isConnected) {

            Log.e(
                TAG,
                "Scan ignored - already connected"
            )

            return
        }

        if (connectionInProgress) {

            Log.e(
                TAG,
                "Scan ignored - connection already in progress"
            )

            return
        }

        if (isScanning) {

            Log.e(
                TAG,
                "Scan ignored - scan already running"
            )

            return
        }

        initializeBluetooth()

        val adapter =
            bluetoothAdapter

        if (adapter == null) {

            Log.e(
                TAG,
                "Bluetooth adapter unavailable"
            )

            scheduleReconnect()

            return
        }

        if (!adapter.isEnabled) {

            Log.e(
                TAG,
                "Bluetooth is disabled"
            )

            scheduleReconnect()

            return
        }

        val bluetoothScanner =
            adapter.bluetoothLeScanner

        if (bluetoothScanner == null) {

            Log.e(
                TAG,
                "Bluetooth scanner unavailable"
            )

            scheduleReconnect()

            return
        }

        scanner = bluetoothScanner

        Log.e(
            TAG,
            "----------------------------------------"
        )

        Log.e(
            TAG,
            "STARTING BLE SCAN"
        )

        Log.e(
            TAG,
            "Target address = $address"
        )

        Log.e(
            TAG,
            "----------------------------------------"
        )

        val filter =
            try {

                ScanFilter.Builder()
                    .setDeviceAddress(address)
                    .build()

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Invalid BLE address: $address",
                    e
                )

                scheduleReconnect()

                return
            }

        val settings =
            ScanSettings.Builder()
                .setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                )
                .build()

        isScanning = true

        try {

            bluetoothScanner.startScan(
                listOf(filter),
                settings,
                scanCallback
            )

            Log.e(
                TAG,
                "BLE SCAN STARTED"
            )

            scanTimeoutRunnable =
                Runnable {

                    if (!isScanning) {
                        return@Runnable
                    }

                    Log.e(
                        TAG,
                        "BLE scan timeout - target not found"
                    )

                    stopBleScan()

                    scheduleReconnect()
                }

            handler.postDelayed(
                scanTimeoutRunnable!!,
                SCAN_TIMEOUT
            )

        } catch (e: Exception) {

            isScanning = false

            Log.e(
                TAG,
                "BLE scan start failed",
                e
            )

            scheduleReconnect()
        }
    }

    // ============================================================
    // STOP BLE SCAN
    // ============================================================

    private fun stopBleScan() {

        scanTimeoutRunnable?.let {

            handler.removeCallbacks(it)
        }

        scanTimeoutRunnable = null

        if (!isScanning) {
            return
        }

        try {

            scanner?.stopScan(
                scanCallback
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to stop BLE scan",
                e
            )
        }

        isScanning = false

        Log.e(
            TAG,
            "BLE SCAN STOPPED"
        )
    }

    // ============================================================
    // BLE SCAN CALLBACK
    // ============================================================

    private val scanCallback =
        object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {

                val device =
                    result.device

                Log.e(
                    TAG,
                    "----------------------------------------"
                )

                Log.e(
                    TAG,
                    "BLE DEVICE FOUND"
                )

                Log.e(
                    TAG,
                    "Name = ${device.name}"
                )

                Log.e(
                    TAG,
                    "Address = ${device.address}"
                )

                Log.e(
                    TAG,
                    "----------------------------------------"
                )

                val savedAddress =
                    getSavedDeviceAddress()

                if (
                    savedAddress.isNullOrBlank() ||
                    !device.address.equals(
                        savedAddress,
                        ignoreCase = true
                    )
                ) {

                    Log.e(
                        TAG,
                        "Found device is not saved TraceHer device"
                    )

                    return
                }

                Log.e(
                    TAG,
                    "TARGET TRACEHER DEVICE FOUND"
                )

                stopBleScan()

                connectToDevice(device)
            }

            override fun onScanFailed(
                errorCode: Int
            ) {

                Log.e(
                    TAG,
                    "========================================"
                )

                Log.e(
                    TAG,
                    "BLE SCAN FAILED"
                )

                Log.e(
                    TAG,
                    "Error code = $errorCode"
                )

                Log.e(
                    TAG,
                    "========================================"
                )

                isScanning = false

                scanTimeoutRunnable?.let {

                    handler.removeCallbacks(it)
                }

                scanTimeoutRunnable = null

                scheduleReconnect()
            }
        }

    // ============================================================
    // CONNECT TO DEVICE
    // ============================================================

    private fun connectToDevice(
        device: BluetoothDevice
    ) {

        if (isConnected) {

            Log.e(
                TAG,
                "Connection ignored - already connected"
            )

            return
        }

        if (connectionInProgress) {

            Log.e(
                TAG,
                "Connection ignored - already connecting"
            )

            return
        }

        bluetoothGatt?.let { oldGatt ->

            try {

                Log.e(
                    TAG,
                    "Closing old GATT before reconnect"
                )

                oldGatt.disconnect()
                oldGatt.close()

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Old GATT cleanup failed",
                    e
                )
            }

            bluetoothGatt = null
        }

        connectionInProgress = true
        notificationsReady = false

        Log.e(
            TAG,
            "----------------------------------------"
        )

        Log.e(
            TAG,
            "CONNECTING TO TRACEHER"
        )

        Log.e(
            TAG,
            "Device = ${device.address}"
        )

        Log.e(
            TAG,
            "----------------------------------------"
        )

        try {

            bluetoothGatt =
                device.connectGatt(
                    this,
                    false,
                    gattCallback
                )

            Log.e(
                TAG,
                "connectGatt() called successfully"
            )

        } catch (e: Exception) {

            connectionInProgress = false
            bluetoothGatt = null

            Log.e(
                TAG,
                "connectGatt() failed",
                e
            )

            scheduleReconnect()
        }
    }

    // ============================================================
    // GATT CALLBACK
    // ============================================================

    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                Log.e(
                    TAG,
                    "========================================"
                )

                Log.e(
                    TAG,
                    "GATT STATE CHANGE"
                )

                Log.e(
                    TAG,
                    "Status = $status"
                )

                Log.e(
                    TAG,
                    "New State = $newState"
                )

                Log.e(
                    TAG,
                    "========================================"
                )

                if (
                    newState ==
                    BluetoothGatt.STATE_CONNECTED
                ) {

                    connectionInProgress = false
                    isConnected = true
                    notificationsReady = false
                    reconnectScheduled = false

                    reconnectRunnable?.let {

                        handler.removeCallbacks(it)
                    }

                    reconnectRunnable = null

                    Log.e(
                        TAG,
                        "========================================"
                    )

                    Log.e(
                        TAG,
                        "GATT CONNECTED"
                    )

                    Log.e(
                        TAG,
                        "========================================"
                    )

                    stopBleScan()

                    try {

                        val discoveryStarted =
                            gatt.discoverServices()

                        Log.e(
                            TAG,
                            "Service discovery started = $discoveryStarted"
                        )

                        if (!discoveryStarted) {

                            Log.e(
                                TAG,
                                "Service discovery could not start"
                            )

                            handleGattFailure(gatt)
                        }

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Service discovery exception",
                            e
                        )

                        handleGattFailure(gatt)
                    }

                } else if (
                    newState ==
                    BluetoothGatt.STATE_DISCONNECTED
                ) {

                    Log.e(
                        TAG,
                        "========================================"
                    )

                    Log.e(
                        TAG,
                        "GATT DISCONNECTED"
                    )

                    Log.e(
                        TAG,
                        "Status = $status"
                    )

                    Log.e(
                        TAG,
                        "========================================"
                    )

                    isConnected = false
                    connectionInProgress = false
                    notificationsReady = false

                    try {

                        gatt.close()

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "GATT close failed",
                            e
                        )
                    }

                    if (
                        bluetoothGatt === gatt
                    ) {

                        bluetoothGatt = null
                    }

                    scheduleReconnect()
                }
            }

            // ====================================================
            // SERVICES DISCOVERED
            // ====================================================

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                Log.e(
                    TAG,
                    "----------------------------------------"
                )

                Log.e(
                    TAG,
                    "SERVICES DISCOVERED"
                )

                Log.e(
                    TAG,
                    "Status = $status"
                )

                Log.e(
                    TAG,
                    "----------------------------------------"
                )

                if (
                    status !=
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    Log.e(
                        TAG,
                        "Service discovery FAILED"
                    )

                    handleGattFailure(gatt)

                    return
                }

                val service =
                    gatt.getService(
                        serviceUUID
                    )

                if (service == null) {

                    Log.e(
                        TAG,
                        "TraceHer service NOT found"
                    )

                    handleGattFailure(gatt)

                    return
                }

                Log.e(
                    TAG,
                    "TraceHer service FOUND"
                )

                val characteristic =
                    service.getCharacteristic(
                        characteristicUUID
                    )

                if (characteristic == null) {

                    Log.e(
                        TAG,
                        "TraceHer characteristic NOT found"
                    )

                    handleGattFailure(gatt)

                    return
                }

                Log.e(
                    TAG,
                    "TraceHer characteristic FOUND"
                )

                try {

                    val notificationEnabled =
                        gatt.setCharacteristicNotification(
                            characteristic,
                            true
                        )

                    Log.e(
                        TAG,
                        "Local notification enabled = $notificationEnabled"
                    )

                    if (!notificationEnabled) {

                        Log.e(
                            TAG,
                            "Could not enable local notifications"
                        )

                        handleGattFailure(gatt)

                        return
                    }

                    val descriptor =
                        characteristic.getDescriptor(
                            cccdUUID
                        )

                    if (descriptor == null) {

                        Log.e(
                            TAG,
                            "CCCD descriptor NOT found"
                        )

                        handleGattFailure(gatt)

                        return
                    }

                    Log.e(
                        TAG,
                        "CCCD descriptor FOUND"
                    )

                    descriptor.value =
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                    val writeStarted =
                        gatt.writeDescriptor(
                            descriptor
                        )

                    Log.e(
                        TAG,
                        "CCCD write started = $writeStarted"
                    )

                    if (!writeStarted) {

                        Log.e(
                            TAG,
                            "CCCD write could not start"
                        )

                        handleGattFailure(gatt)
                    }

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Notification setup failed",
                        e
                    )

                    handleGattFailure(gatt)
                }
            }

            // ====================================================
            // DESCRIPTOR WRITE
            // ====================================================

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {

                Log.e(
                    TAG,
                    "----------------------------------------"
                )

                Log.e(
                    TAG,
                    "CCCD DESCRIPTOR WRITE"
                )

                Log.e(
                    TAG,
                    "Status = $status"
                )

                Log.e(
                    TAG,
                    "----------------------------------------"
                )

                if (
                    descriptor.uuid == cccdUUID &&
                    status ==
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    notificationsReady = true

                    Log.e(
                        TAG,
                        "========================================"
                    )

                    Log.e(
                        TAG,
                        "BLE NOTIFICATIONS READY"
                    )

                    Log.e(
                        TAG,
                        "TRACEHER IS READY FOR SOS"
                    )

                    Log.e(
                        TAG,
                        "========================================"

                    )

                } else {

                    Log.e(
                        TAG,
                        "CCCD setup FAILED"
                    )

                    handleGattFailure(gatt)
                }
            }

            // ====================================================
            // CHARACTERISTIC CHANGED
            // ====================================================

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {

                if (
                    characteristic.uuid !=
                    characteristicUUID
                ) {

                    return
                }

                val message =
                    characteristic.value
                        ?.toString(Charsets.UTF_8)
                        ?.trim()

                Log.e(
                    TAG,
                    "========================================"
                )

                Log.e(
                    TAG,
                    "BLE MESSAGE RECEIVED = $message"
                )

                Log.e(
                    TAG,
                    "========================================"
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

                    sendEmergencySMS()
                }
            }
        }

    // ============================================================
    // GATT FAILURE
    // ============================================================

    private fun handleGattFailure(
        gatt: BluetoothGatt
    ) {

        Log.e(
            TAG,
            "Handling GATT failure"
        )

        isConnected = false
        connectionInProgress = false
        notificationsReady = false

        try {

            gatt.disconnect()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "GATT disconnect failed",
                e
            )
        }

        try {

            gatt.close()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "GATT close failed",
                e
            )
        }

        if (
            bluetoothGatt === gatt
        ) {

            bluetoothGatt = null
        }

        scheduleReconnect()
    }

    // ============================================================
    // SCHEDULE RECONNECT
    // ============================================================

    private fun scheduleReconnect() {

        if (isConnected) {

            Log.e(
                TAG,
                "Reconnect not needed - already connected"
            )

            return
        }

        if (isScanning) {

            Log.e(
                TAG,
                "Reconnect not scheduled - scan already running"
            )

            return
        }

        if (reconnectScheduled) {

            Log.e(
                TAG,
                "Reconnect already scheduled"
            )

            return
        }

        val savedAddress =
            getSavedDeviceAddress()

        if (savedAddress.isNullOrBlank()) {

            Log.e(
                TAG,
                "Reconnect impossible - no saved device"
            )

            return
        }

        reconnectScheduled = true

        Log.e(
            TAG,
            "========================================"
        )

        Log.e(
            TAG,
            "RECONNECT SCHEDULED"
        )

        Log.e(
            TAG,
            "Retry in ${RECONNECT_DELAY} ms"
        )

        Log.e(
            TAG,
            "========================================"
        )

        reconnectRunnable =
            Runnable {

                reconnectScheduled = false
                reconnectRunnable = null

                Log.e(
                    TAG,
                    "Reconnect timer fired"
                )

                if (isConnected) {

                    Log.e(
                        TAG,
                        "Reconnect cancelled - already connected"
                    )

                    return@Runnable
                }

                if (connectionInProgress) {

                    Log.e(
                        TAG,
                        "Reconnect cancelled - connection in progress"
                    )

                    return@Runnable
                }

                if (isScanning) {

                    Log.e(
                        TAG,
                        "Reconnect cancelled - scan already running"
                    )

                    return@Runnable
                }

                val currentAddress =
                    getSavedDeviceAddress()

                if (currentAddress.isNullOrBlank()) {

                    Log.e(
                        TAG,
                        "Reconnect cancelled - no saved address"
                    )

                    return@Runnable
                }

                Log.e(
                    TAG,
                    "Starting DIRECT reconnect now"
                )

                try {

                    val adapter =
                        bluetoothAdapter

                    if (adapter == null) {

                        Log.e(
                            TAG,
                            "Bluetooth adapter unavailable during reconnect"
                        )

                        scheduleReconnect()

                        return@Runnable
                    }

                    if (!adapter.isEnabled) {

                        Log.e(
                            TAG,
                            "Bluetooth disabled during reconnect"
                        )

                        scheduleReconnect()

                        return@Runnable
                    }

                    val device =
                        adapter.getRemoteDevice(
                            currentAddress
                        )

                    Log.e(
                        TAG,
                        "Direct reconnect device created"
                    )

                    Log.e(
                        TAG,
                        "Device address = ${device.address}"
                    )

                    connectToDevice(device)

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Direct reconnect failed",
                        e
                    )

                    scheduleReconnect()
                }
            }

        handler.postDelayed(
            reconnectRunnable!!,
            RECONNECT_DELAY
        )
    }

    // ============================================================
    // EMERGENCY SMS
    // ============================================================

    private fun sendEmergencySMS() {

        val contacts =
            getEmergencyContacts()

        Log.e(
            TAG,
            "Emergency contacts found = ${contacts.size}"
        )

        if (contacts.isEmpty()) {

            Log.e(
                TAG,
                "No emergency contacts found"
            )

            return
        }

        val locationText =
            getLocationText()

        val message =
            """
            🚨 TRACEHER EMERGENCY ALERT 🚨
            
            ⚠️ Emergency assistance is required.
            
            📍 Current Location:
            $locationText
            
            Please respond immediately.
            """.trimIndent()

        Log.e(
            TAG,
            "SOS SMS message prepared:\n$message"
        )

        for (contact in contacts) {

            val phone =
                contact.optString(
                    "phoneNumber"
                )

            if (phone.isNullOrBlank()) {

                continue
            }

            Log.e(
                TAG,
                "Sending SOS SMS to: $phone"
            )

            try {

                if (
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.SEND_SMS
                    ) !=
                    PackageManager.PERMISSION_GRANTED
                ) {

                    Log.e(
                        TAG,
                        "SEND_SMS permission not granted"
                    )

                    continue
                }

                val smsManager =
                    SmsManager.getDefault()

                val parts =
                    smsManager.divideMessage(
                        message
                    )

                smsManager.sendMultipartTextMessage(
                    phone,
                    null,
                    parts,
                    null,
                    null
                )

                Log.e(
                    TAG,
                    "SOS SMS SENT to $phone"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to send SOS SMS to $phone",
                    e
                )
            }
        }
    }

    // ============================================================
    // EMERGENCY CONTACTS
    // ============================================================

    private fun getEmergencyContacts():
            MutableList<JSONObject> {

        val contacts =
            mutableListOf<JSONObject>()

        val prefs =
            getSharedPreferences(
                "FlutterSharedPreferences",
                Context.MODE_PRIVATE
            )

        val rawValue =
            prefs.all[
                "flutter.$EMERGENCY_CONTACTS_KEY"
            ]

        Log.e(
            TAG,
            "Emergency contacts raw type = ${
                rawValue?.javaClass?.name
            }"
        )

        Log.e(
            TAG,
            "Emergency contacts raw value = $rawValue"
        )

        if (rawValue == null) {

            return contacts
        }

        try {

            var jsonText: String? = null

            if (rawValue is String) {

                val value =
                    rawValue.trim()

                val flutterStringListPrefix =
                    "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu!"

                if (
                    value.startsWith(
                        flutterStringListPrefix
                    )
                ) {

                    Log.e(
                        TAG,
                        "Flutter StringList prefix detected"
                    )

                    jsonText =
                        value.substring(
                            flutterStringListPrefix.length
                        )

                } else {

                    jsonText = value
                }
            }

            if (
                jsonText.isNullOrBlank()
            ) {

                return contacts
            }

            val array =
                JSONArray(jsonText)

            Log.e(
                TAG,
                "Decoded contact JSON list = $array"
            )

            for (
            i in 0 until array.length()
            ) {

                try {

                    val item =
                        array.getString(i)

                    Log.e(
                        TAG,
                        "Contact JSON string = $item"
                    )

                    val contact =
                        JSONObject(item)

                    Log.e(
                        TAG,
                        "Contact JSON = $contact"
                    )

                    contacts.add(contact)

                    Log.e(
                        TAG,
                        "Contact loaded: ${
                            contact.optString("name")
                        } / ${
                            contact.optString("phoneNumber")
                        }"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Failed to parse contact at index $i",
                        e
                    )
                }
            }

            Log.e(
                TAG,
                "Successfully decoded ${contacts.size} emergency contacts"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Emergency contact decoding failed",
                e
            )
        }

        return contacts
    }

    // ============================================================
    // LOCATION
    // ============================================================

    private fun getLocationText(): String {

        val prefs =
            getSharedPreferences(
                "FlutterSharedPreferences",
                Context.MODE_PRIVATE
            )

        val latitude =
            prefs.getString(
                "flutter.$LATITUDE_KEY",
                null
            )

        val longitude =
            prefs.getString(
                "flutter.$LONGITUDE_KEY",
                null
            )

        val savedTime =
            prefs.getLong(
                "flutter.$LOCATION_TIME_KEY",
                0L
            )

        Log.e(
            TAG,
            "Saved latitude = $latitude"
        )

        Log.e(
            TAG,
            "Saved longitude = $longitude"
        )

        Log.e(
            TAG,
            "Saved location time = $savedTime"
        )

        if (
            latitude.isNullOrBlank() ||
            longitude.isNullOrBlank()
        ) {

            Log.e(
                TAG,
                "No saved Flutter location available"
            )

            return "Location unavailable."
        }

        return try {

            val mapsLink =
                "https://maps.google.com/?q=" +
                        "$latitude,$longitude"

            Log.e(
                TAG,
                "Using saved Flutter location = " +
                        "$latitude, $longitude"
            )

            Log.e(
                TAG,
                "Google Maps link = $mapsLink"
            )

            mapsLink

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to create location link",
                e
            )

            "Location unavailable."
        }
    }

    // ============================================================
    // NOTIFICATION CHANNEL
    // ============================================================

    private fun createNotificationChannel() {

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "TraceHer Background Service",
                NotificationManager.IMPORTANCE_LOW
            )

        channel.description =
            "Keeps TraceHer BLE monitoring active"

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            channel
        )
    }

    // ============================================================
    // SERVICE DESTROYED
    // ============================================================

    override fun onDestroy() {

        Log.e(
            TAG,
            "========================================"
        )

        Log.e(
            TAG,
            "BleForegroundService DESTROYED"
        )

        Log.e(
            TAG,
            "========================================"
        )

        reconnectRunnable?.let {

            handler.removeCallbacks(it)
        }

        reconnectRunnable = null
        reconnectScheduled = false

        scanTimeoutRunnable?.let {

            handler.removeCallbacks(it)
        }

        scanTimeoutRunnable = null

        stopBleScan()

        try {

            bluetoothGatt?.disconnect()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "GATT disconnect failed during destroy",
                e
            )
        }

        try {

            bluetoothGatt?.close()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "GATT close failed during destroy",
                e
            )
        }

        bluetoothGatt = null

        isConnected = false
        connectionInProgress = false
        notificationsReady = false

        instance = null

        super.onDestroy()
    }

    // ============================================================
    // BIND
    // ============================================================

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}