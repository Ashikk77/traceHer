package com.example.traceher

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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

        private const val CHANNEL_ID =
            "traceher_service"

        private const val TAG =
            "TraceHerService"

        private const val NOTIFICATION_ID =
            1

        private const val SAVED_DEVICE_KEY =
            "saved_ble_device"

        private const val SERVICE_UUID =
            "12345678-1234-1234-1234-1234567890AB"

        private const val CHARACTERISTIC_UUID =
            "87654321-4321-4321-4321-BA0987654321"

        private const val CCCD_UUID =
            "00002902-0000-1000-8000-00805f9b34fb"

        private const val CONTACTS_KEY =
            "emergency_contacts"

        // Existing Flutter location cache
        private const val LATITUDE_KEY =
            "traceher_last_latitude"

        private const val LONGITUDE_KEY =
            "traceher_last_longitude"

        private const val LOCATION_TIME_KEY =
            "traceher_last_location_time"
    }

    // ============================================================
    // BLE STATE
    // ============================================================

    private var scanner: BluetoothLeScanner? = null

    private var targetAddress: String? = null

    private var bluetoothGatt: BluetoothGatt? = null

    private var isScanning = false

    private var isConnecting = false

    private var isConnected = false

    // ============================================================
    // RECONNECT HANDLER
    // ============================================================

    private val handler =
        Handler(Looper.getMainLooper())

    private val reconnectRunnable =
        Runnable {

            Log.e(
                TAG,
                "Reconnect timer fired"
            )

            if (
                bluetoothGatt == null &&
                !isScanning &&
                !isConnecting &&
                !isConnected &&
                targetAddress != null
            ) {

                Log.e(
                    TAG,
                    "Starting BLE scan after disconnect"
                )

                startBleScan()

            } else {

                Log.e(
                    TAG,
                    "Reconnect scan skipped - BLE already active"
                )
            }
        }

    // ============================================================
    // SERVICE CREATE
    // ============================================================

    override fun onCreate() {

        super.onCreate()

        Log.e(
            TAG,
            "onCreate()"
        )

        createNotificationChannel()

        val notification =
            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle(
                    "TraceHer"
                )
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

        Log.e(
            TAG,
            "Foreground Started"
        )

        val bluetoothManager =
            getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        scanner =
            bluetoothManager
                .adapter
                ?.bluetoothLeScanner
    }

    // ============================================================
    // SERVICE START
    // ============================================================

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.e(
            TAG,
            "onStartCommand()"
        )

        val prefs =
            getSharedPreferences(
                "FlutterSharedPreferences",
                Context.MODE_PRIVATE
            )

        var deviceAddress =
            prefs.getString(
                "flutter.$SAVED_DEVICE_KEY",
                null
            )

        if (deviceAddress == null) {

            deviceAddress =
                prefs.getString(
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

            targetAddress =
                deviceAddress

            Log.e(
                TAG,
                "Target Address Set = $targetAddress"
            )

            if (
                bluetoothGatt == null &&
                !isConnected &&
                !isConnecting &&
                !isScanning
            ) {

                Log.e(
                    TAG,
                    "BLE inactive - starting scan"
                )

                startBleScan()

            } else {

                Log.e(
                    TAG,
                    "BLE already active - NO scan started"
                )

                Log.e(
                    TAG,
                    "Connected=$isConnected " +
                            "Connecting=$isConnecting " +
                            "Scanning=$isScanning " +
                            "GATT=${bluetoothGatt != null}"
                )
            }
        }

        return START_STICKY
    }

    // ============================================================
    // APP REMOVED FROM RECENTS
    // ============================================================

    override fun onTaskRemoved(
        rootIntent: Intent?
    ) {

        super.onTaskRemoved(
            rootIntent
        )

        Log.e(
            TAG,
            "onTaskRemoved() - foreground service still running"
        )

        if (
            bluetoothGatt == null &&
            !isConnected &&
            !isConnecting &&
            !isScanning &&
            targetAddress != null
        ) {

            Log.e(
                TAG,
                "No active BLE connection - starting scan"
            )

            startBleScan()

        } else {

            Log.e(
                TAG,
                "Task removed - BLE already active, no scan needed"
            )
        }
    }

    // ============================================================
    // SERVICE DESTROY
    // ============================================================

    override fun onDestroy() {

        Log.e(
            TAG,
            "onDestroy()"
        )

        handler.removeCallbacks(
            reconnectRunnable
        )

        stopBleScan()

        try {

            bluetoothGatt?.close()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error closing GATT",
                e
            )
        }

        bluetoothGatt = null

        isConnected = false

        isConnecting = false

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
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

        val manager =
            getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.createNotificationChannel(
            channel
        )
    }

    // ============================================================
    // START BLE SCAN
    // ============================================================

    private fun startBleScan() {

        if (isConnected) {

            Log.e(
                TAG,
                "startBleScan() BLOCKED - already connected"
            )

            return
        }

        if (bluetoothGatt != null) {

            Log.e(
                TAG,
                "startBleScan() BLOCKED - GATT exists"
            )

            return
        }

        if (isConnecting) {

            Log.e(
                TAG,
                "startBleScan() BLOCKED - connection in progress"
            )

            return
        }

        if (isScanning) {

            Log.e(
                TAG,
                "startBleScan() BLOCKED - scan already running"
            )

            return
        }

        val address =
            targetAddress

        if (address == null) {

            Log.e(
                TAG,
                "Cannot scan - target address is null"
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

        val bluetoothManager =
            getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        val adapter =
            bluetoothManager.adapter

        if (adapter == null) {

            Log.e(
                TAG,
                "Bluetooth adapter unavailable"
            )

            return
        }

        if (!adapter.isEnabled) {

            Log.e(
                TAG,
                "Bluetooth is disabled"
            )

            return
        }

        scanner =
            adapter.bluetoothLeScanner

        if (scanner == null) {

            Log.e(
                TAG,
                "BLE scanner unavailable"
            )

            return
        }

        val filter =
            ScanFilter.Builder()
                .setDeviceAddress(
                    address
                )
                .build()

        val settings =
            ScanSettings.Builder()
                .setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                )
                .build()

        Log.e(
            TAG,
            "================================"
        )

        Log.e(
            TAG,
            "STARTING TRACEHER BLE SCAN"
        )

        Log.e(
            TAG,
            "Target = $address"
        )

        Log.e(
            TAG,
            "================================"
        )

        isScanning = true

        try {

            scanner?.startScan(
                listOf(filter),
                settings,
                scanCallback
            )

        } catch (e: Exception) {

            isScanning = false

            Log.e(
                TAG,
                "Failed to start BLE scan",
                e
            )
        }
    }

    // ============================================================
    // STOP BLE SCAN
    // ============================================================

    private fun stopBleScan() {

        if (!isScanning) {

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
                "Cannot stop scan - permission missing"
            )

            isScanning = false

            return
        }

        try {

            scanner?.stopScan(
                scanCallback
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error stopping BLE scan",
                e
            )
        }

        isScanning = false

        Log.e(
            TAG,
            "BLE Scan Stopped"
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

                if (isConnected) {

                    return
                }

                if (isConnecting) {

                    return
                }

                val device =
                    result.device

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
                    "TraceHer found: ${device.address}"
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

                    connectToDevice(
                        device
                    )
                }
            }

            override fun onScanFailed(
                errorCode: Int
            ) {

                isScanning = false

                Log.e(
                    TAG,
                    "BLE Scan Failed: $errorCode"
                )
            }
        }

    // ============================================================
    // CONNECT TO ESP32
    // ============================================================

    private fun connectToDevice(
        device: BluetoothDevice
    ) {

        stopBleScan()

        if (isConnected) {

            Log.e(
                TAG,
                "connectToDevice() ignored - already connected"
            )

            return
        }

        if (isConnecting) {

            Log.e(
                TAG,
                "connectToDevice() ignored - already connecting"
            )

            return
        }

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

        Log.e(
            TAG,
            "Connecting to ESP32..."
        )

        try {

            bluetoothGatt?.close()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Error closing old GATT",
                e
            )
        }

        bluetoothGatt = null

        try {

            bluetoothGatt =
                device.connectGatt(
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
                "connectGatt() failed",
                e
            )

            isConnecting = false

            bluetoothGatt = null

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
                    "GATT state changed. " +
                            "Status=$status State=$newState"
                )

                if (
                    newState ==
                    BluetoothGatt.STATE_CONNECTED
                ) {

                    isConnecting = false

                    isConnected = true

                    stopBleScan()

                    handler.removeCallbacks(
                        reconnectRunnable
                    )

                    Log.e(
                        TAG,
                        "GATT CONNECTED"
                    )

                    Log.e(
                        TAG,
                        "BLE scanning disabled while connected"
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

                    val discoveryStarted =
                        gatt.discoverServices()

                    Log.e(
                        TAG,
                        "Service discovery started = $discoveryStarted"
                    )

                } else if (
                    newState ==
                    BluetoothGatt.STATE_DISCONNECTED
                ) {

                    Log.e(
                        TAG,
                        "GATT DISCONNECTED"
                    )

                    isConnected = false

                    isConnecting = false

                    stopBleScan()

                    try {

                        gatt.close()

                    } catch (e: Exception) {

                        Log.e(
                            TAG,
                            "Error closing disconnected GATT",
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

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

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

                    return
                }

                if (!isConnected) {

                    Log.e(
                        TAG,
                        "Ignoring services - no longer connected"
                    )

                    return
                }

                val service =
                    gatt.getService(
                        UUID.fromString(
                            SERVICE_UUID
                        )
                    )

                if (service == null) {

                    Log.e(
                        TAG,
                        "TraceHer service NOT found"
                    )

                    return
                }

                Log.e(
                    TAG,
                    "TraceHer service found"
                )

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

                    return
                }

                Log.e(
                    TAG,
                    "TraceHer characteristic found"
                )

                enableNotifications(
                    gatt,
                    characteristic
                )
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {

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
                        "BLE notification setup failed"
                    )
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {

                val value =
                    characteristic.value

                val message =
                    value.toString(
                        Charsets.UTF_8
                    ).trim()

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

                    sendEmergencySMS()
                }
            }
        }

    // ============================================================
    // ENABLE NOTIFICATIONS
    // ============================================================

    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {

        if (!isConnected) {

            Log.e(
                TAG,
                "Cannot enable notifications - not connected"
            )

            return
        }

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

        val localResult =
            gatt.setCharacteristicNotification(
                characteristic,
                true
            )

        Log.e(
            TAG,
            "Local notification enabled = $localResult"
        )

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
            BluetoothGattDescriptor
                .ENABLE_NOTIFICATION_VALUE

        val writeStarted =
            gatt.writeDescriptor(
                descriptor
            )

        Log.e(
            TAG,
            "CCCD write started = $writeStarted"
        )
    }

    // ============================================================
    // RECONNECT
    // ============================================================

    private fun scheduleReconnect() {

        if (targetAddress == null) {

            Log.e(
                TAG,
                "Cannot reconnect - no target address"
            )

            return
        }

        handler.removeCallbacks(
            reconnectRunnable
        )

        if (isConnected) {

            Log.e(
                TAG,
                "Reconnect skipped - still connected"
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
                "Reconnect skipped - GATT still exists"
            )

            return
        }

        Log.e(
            TAG,
            "Scheduling BLE reconnect in 3 seconds..."
        )

        handler.postDelayed(
            reconnectRunnable,
            3000
        )
    }

    // ============================================================
    // SOS SMS
    // ============================================================

    private fun sendEmergencySMS() {

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.e(
                TAG,
                "SEND_SMS permission missing"
            )

            return
        }

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
            "TRACEHER SOS ALERT!\n" +
                    "Emergency assistance is required.\n" +
                    locationText

        Log.e(
            TAG,
            "SOS SMS message prepared:\n$message"
        )

        val smsManager =
            SmsManager.getDefault()

        for (contact in contacts) {

            try {

                Log.e(
                    TAG,
                    "Sending SOS SMS to: ${contact.phoneNumber}"
                )

                val parts =
                    smsManager.divideMessage(
                        message
                    )

                smsManager.sendMultipartTextMessage(
                    contact.phoneNumber,
                    null,
                    parts,
                    null,
                    null
                )

                Log.e(
                    TAG,
                    "SOS SMS SENT to ${contact.phoneNumber}"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to send SOS SMS to ${contact.phoneNumber}",
                    e
                )
            }
        }
    }

    // ============================================================
    // EMERGENCY CONTACT MODEL
    // ============================================================

    private data class EmergencyContactNative(
        val name: String,
        val phoneNumber: String,
        val relationship: String
    )

    // ============================================================
    // EMERGENCY CONTACTS
    // ============================================================

    private fun getEmergencyContacts():
            List<EmergencyContactNative> {

        val prefs =
            getSharedPreferences(
                "FlutterSharedPreferences",
                Context.MODE_PRIVATE
            )

        val key =
            "flutter.$CONTACTS_KEY"

        val rawValue =
            prefs.all[key]

        Log.e(
            TAG,
            "Emergency contacts raw type = " +
                    "${rawValue?.javaClass?.name}"
        )

        Log.e(
            TAG,
            "Emergency contacts raw value = $rawValue"
        )

        if (rawValue == null) {

            Log.e(
                TAG,
                "Emergency contacts preference not found"
            )

            return emptyList()
        }

        val result =
            mutableListOf<EmergencyContactNative>()

        try {

            if (rawValue is String) {

                var encoded =
                    rawValue

                val flutterListPrefix =
                    "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBhIGxpc3Qu"

                if (
                    encoded.startsWith(
                        flutterListPrefix
                    )
                ) {

                    Log.e(
                        TAG,
                        "Flutter StringList prefix detected"
                    )

                    encoded =
                        encoded.substring(
                            flutterListPrefix.length
                        )
                }

                if (
                    encoded.startsWith("!")
                ) {

                    encoded =
                        encoded.substring(1)
                }

                Log.e(
                    TAG,
                    "Decoded contact JSON list = $encoded"
                )

                val jsonArray =
                    JSONArray(encoded)

                for (
                i in 0 until jsonArray.length()
                ) {

                    val contactString =
                        jsonArray.getString(i)

                    Log.e(
                        TAG,
                        "Contact JSON string = $contactString"
                    )

                    parseEmergencyContact(
                        contactString,
                        result
                    )
                }

            } else if (rawValue is Set<*>) {

                Log.e(
                    TAG,
                    "Emergency contacts stored as Set"
                )

                for (item in rawValue) {

                    if (item !is String) {
                        continue
                    }

                    parseEmergencyContact(
                        item,
                        result
                    )
                }

            } else if (rawValue is List<*>) {

                Log.e(
                    TAG,
                    "Emergency contacts stored as List"
                )

                for (item in rawValue) {

                    if (item !is String) {
                        continue
                    }

                    parseEmergencyContact(
                        item,
                        result
                    )
                }

            } else {

                Log.e(
                    TAG,
                    "Unsupported emergency contact preference type"
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Emergency contact decoding failed",
                e
            )
        }

        Log.e(
            TAG,
            "Successfully decoded " +
                    "${result.size} emergency contacts"
        )

        return result
    }

    // ============================================================
    // PARSE SINGLE CONTACT
    // ============================================================

    private fun parseEmergencyContact(
        contactString: String,
        result: MutableList<EmergencyContactNative>
    ) {

        try {

            val jsonObject =
                JSONObject(
                    contactString
                )

            val name =
                jsonObject.optString(
                    "name"
                )

            val phone =
                jsonObject.optString(
                    "phoneNumber"
                )

            val relationship =
                jsonObject.optString(
                    "relationship"
                )

            Log.e(
                TAG,
                "Contact JSON = $contactString"
            )

            if (phone.isNotEmpty()) {

                result.add(
                    EmergencyContactNative(
                        name,
                        phone,
                        relationship
                    )
                )

                Log.e(
                    TAG,
                    "Contact loaded: " +
                            "$name / $phone"
                )

            } else {

                Log.e(
                    TAG,
                    "Contact skipped - phone number empty"
                )
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to parse contact JSON: $contactString",
                e
            )
        }
    }

    // ============================================================
    // LOCATION
    // ============================================================
    //
    // IMPORTANT:
    // We are using the EXISTING Flutter LocationManager data.
    //
    // Flutter already saves:
    //
    // flutter.traceher_last_latitude
    // flutter.traceher_last_longitude
    // flutter.traceher_last_location_time
    //
    // The native background service simply reads those values
    // when SOS is received.
    //
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

            "Location:\n$mapsLink"

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to create location link",
                e
            )

            "Location unavailable."
        }
    }
}