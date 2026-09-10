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
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SmsManager
import android.util.Base64
import android.util.Log
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass
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

        private const val CCCD_UUID =
            "00002902-0000-1000-8000-00805f9b34fb"

        private const val CONTACTS_KEY =
            "emergency_contacts"
    }

    private var scanner: BluetoothLeScanner? = null
    private var targetAddress: String? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var isScanning = false
    private var isConnecting = false

    private val handler =
        Handler(Looper.getMainLooper())

    // =========================================================
    // SERVICE CREATED
    // =========================================================

    override fun onCreate() {

        super.onCreate()

        Log.e(TAG, "onCreate()")

        createNotificationChannel()

        val notification =
            Notification.Builder(
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

        Log.e(
            TAG,
            "Foreground Started"
        )
    }

    // =========================================================
    // SERVICE START
    // =========================================================

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

        return START_STICKY
    }

    // =========================================================
    // APP TASK REMOVED
    // =========================================================

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

        Log.e(
            TAG,
            "onDestroy()"
        )

        try {

            scanner?.stopScan(
                scanCallback
            )

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

        scanner =
            adapter.bluetoothLeScanner

        if (scanner == null) {

            Log.e(
                TAG,
                "BluetoothLeScanner is null"
            )

            return
        }

        val settings =
            ScanSettings.Builder()
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
                    "Scan Failed : $errorCode"
                )

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

                scanner?.stopScan(
                    scanCallback
                )
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

                    scheduleReconnect()

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

                val notificationEnabled =
                    gatt.setCharacteristicNotification(
                        characteristic,
                        true
                    )

                Log.e(
                    TAG,
                    "Local notification enabled = $notificationEnabled"
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
                characteristic:
                android.bluetooth.BluetoothGattCharacteristic,
                value: ByteArray
            ) {

                super.onCharacteristicChanged(
                    gatt,
                    characteristic,
                    value
                )

                val message =
                    value
                        .toString(Charsets.UTF_8)
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

                    sendEmergencySMS()
                }
            }
        }

    // =========================================================
    // SEND EMERGENCY SMS
    // =========================================================

    private fun sendEmergencySMS() {

        Log.e(
            TAG,
            "Starting native emergency SMS..."
        )

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

        val prefs =
            getSharedPreferences(
                "FlutterSharedPreferences",
                Context.MODE_PRIVATE
            )

        val contacts =
            getEmergencyContacts(
                prefs
            )

        if (contacts.isEmpty()) {

            Log.e(
                TAG,
                "No emergency contacts found"
            )

            return
        }

        Log.e(
            TAG,
            "Emergency contacts found = ${contacts.size}"
        )

        val locationText =
            getLocationText()

        val message =
            "TRACEHER SOS ALERT!\n" +
                    "Emergency assistance is required.\n" +
                    locationText

        Log.e(
            TAG,
            "SOS SMS message prepared:"
        )

        Log.e(
            TAG,
            message
        )

        val smsManager =
            getSystemService(
                SmsManager::class.java
            )

        for (contact in contacts) {

            val phoneNumber =
                contact.phoneNumber

            if (phoneNumber.isBlank()) {
                continue
            }

            try {

                Log.e(
                    TAG,
                    "Sending SOS SMS to: $phoneNumber"
                )

                val parts =
                    smsManager.divideMessage(
                        message
                    )

                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    parts,
                    null,
                    null
                )

                Log.e(
                    TAG,
                    "SOS SMS SENT to $phoneNumber"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "SMS FAILED for $phoneNumber",
                    e
                )
            }
        }
    }

    // =========================================================
    // NATIVE CONTACT MODEL
    // =========================================================

    private data class EmergencyContactNative(
        val name: String,
        val phoneNumber: String,
        val relationship: String
    )

    // =========================================================
    // READ EMERGENCY CONTACTS
    // =========================================================

    private fun getEmergencyContacts(
        prefs: android.content.SharedPreferences
    ): List<EmergencyContactNative> {

        val possibleKeys =
            listOf(
                "flutter.$CONTACTS_KEY",
                CONTACTS_KEY
            )

        var rawValue: String? = null

        for (key in possibleKeys) {

            val value =
                prefs.getString(
                    key,
                    null
                )

            if (!value.isNullOrBlank()) {

                rawValue = value

                Log.e(
                    TAG,
                    "Found emergency contacts using key: $key"
                )

                break
            }
        }

        if (rawValue == null) {

            Log.e(
                TAG,
                "Emergency contacts SharedPreferences value not found"
            )

            return emptyList()
        }

        Log.e(
            TAG,
            "Raw emergency contacts data found"
        )

        Log.e(
            TAG,
            "Raw value length = ${rawValue.length}"
        )

        return try {

            val separatorIndex =
                rawValue.indexOf("!")

            if (separatorIndex <= 0) {

                Log.e(
                    TAG,
                    "Flutter List encoding separator not found"
                )

                return emptyList()
            }

            val encodedData =
                rawValue.substring(
                    separatorIndex + 1
                )

            Log.e(
                TAG,
                "Flutter list prefix detected"
            )

            Log.e(
                TAG,
                "Encoded list data length = ${encodedData.length}"
            )

            if (encodedData.isBlank()) {

                Log.e(
                    TAG,
                    "Encoded contact list is empty"
                )

                return emptyList()
            }

            // =================================================
            // DECODE FLUTTER LIST
            // =================================================

            val contactStrings =
                try {

                    val decodedBytes =
                        Base64.decode(
                            encodedData,
                            Base64.DEFAULT
                        )

                    Log.e(
                        TAG,
                        "Base64 decoded successfully"
                    )

                    /*
                     * Older Flutter shared_preferences stores
                     * List<String> using Java serialization.
                     *
                     * Only ArrayList and String are allowed here.
                     */

                    val inputStream =
                        object : ObjectInputStream(
                            ByteArrayInputStream(
                                decodedBytes
                            )
                        ) {

                            override fun resolveClass(
                                desc: ObjectStreamClass
                            ): Class<*> {

                                return when (desc.name) {

                                    "java.util.ArrayList" ->
                                        ArrayList::class.java

                                    "java.lang.String" ->
                                        String::class.java

                                    else ->
                                        throw ClassNotFoundException(
                                            "Blocked class: ${desc.name}"
                                        )
                                }
                            }
                        }

                    inputStream.use {

                        val decodedObject =
                            it.readObject()

                        when (decodedObject) {

                            is ArrayList<*> -> {

                                decodedObject.mapNotNull { item ->

                                    if (
                                        item is String
                                    ) {
                                        item
                                    } else {
                                        null
                                    }
                                }
                            }

                            is List<*> -> {

                                decodedObject.mapNotNull { item ->

                                    if (
                                        item is String
                                    ) {
                                        item
                                    } else {
                                        null
                                    }
                                }
                            }

                            else -> {

                                Log.e(
                                    TAG,
                                    "Unexpected Flutter list object: ${decodedObject.javaClass.name}"
                                )

                                emptyList()
                            }
                        }
                    }

                } catch (legacyException: Exception) {

                    Log.e(
                        TAG,
                        "Legacy Java list decoding failed. Trying JSON format.",
                        legacyException
                    )

                    // =================================================
                    // JSON FALLBACK
                    // =================================================

                    try {

                        val decodedBytes =
                            Base64.decode(
                                encodedData,
                                Base64.DEFAULT
                            )

                        val decodedText =
                            String(
                                decodedBytes,
                                Charsets.UTF_8
                            )

                        Log.e(
                            TAG,
                            "Trying decoded JSON:"
                        )

                        Log.e(
                            TAG,
                            decodedText
                        )

                        val jsonArray =
                            JSONArray(
                                decodedText
                            )

                        val list =
                            mutableListOf<String>()

                        for (
                        i in 0 until jsonArray.length()
                        ) {

                            val item =
                                jsonArray.optString(
                                    i,
                                    ""
                                )

                            if (item.isNotBlank()) {
                                list.add(item)
                            }
                        }

                        list

                    } catch (jsonException: Exception) {

                        Log.e(
                            TAG,
                            "JSON decoding also failed. Trying raw payload.",
                            jsonException
                        )

                        // =================================================
                        // RAW JSON FALLBACK
                        // =================================================

                        try {

                            val jsonArray =
                                JSONArray(
                                    encodedData
                                )

                            val list =
                                mutableListOf<String>()

                            for (
                            i in 0 until jsonArray.length()
                            ) {

                                val item =
                                    jsonArray.optString(
                                        i,
                                        ""
                                    )

                                if (item.isNotBlank()) {
                                    list.add(item)
                                }
                            }

                            list

                        } catch (rawException: Exception) {

                            Log.e(
                                TAG,
                                "All contact decoding methods failed.",
                                rawException
                            )

                            emptyList()
                        }
                    }
                }

            Log.e(
                TAG,
                "Decoded contact string count = ${contactStrings.size}"
            )

            val result =
                mutableListOf<EmergencyContactNative>()

            for (contactString in contactStrings) {

                try {

                    Log.e(
                        TAG,
                        "Contact JSON = $contactString"
                    )

                    val contactJson =
                        org.json.JSONObject(
                            contactString
                        )

                    val name =
                        contactJson.optString(
                            "name",
                            ""
                        )

                    val phoneNumber =
                        contactJson.optString(
                            "phoneNumber",
                            ""
                        )

                    val relationship =
                        contactJson.optString(
                            "relationship",
                            ""
                        )

                    if (
                        phoneNumber.isNotBlank()
                    ) {

                        result.add(
                            EmergencyContactNative(
                                name = name,
                                phoneNumber = phoneNumber,
                                relationship = relationship
                            )
                        )

                        Log.e(
                            TAG,
                            "Contact loaded: $name / $phoneNumber"
                        )

                    } else {

                        Log.e(
                            TAG,
                            "Contact skipped because phone number is empty"
                        )
                    }

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "Failed to decode individual contact",
                        e
                    )
                }
            }

            Log.e(
                TAG,
                "Successfully decoded ${result.size} emergency contacts"
            )

            result

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Failed to decode emergency contacts",
                e
            )

            emptyList()
        }
    }

    // =========================================================
    // GET LOCATION
    // =========================================================

    private fun getLocationText(): String {

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.e(
                TAG,
                "Location permission missing"
            )

            return "Location unavailable."
        }

        return try {

            val locationManager =
                getSystemService(
                    Context.LOCATION_SERVICE
                ) as LocationManager

            var bestLocation: Location? =
                null

            val providers =
                locationManager.getProviders(
                    true
                )

            for (provider in providers) {

                try {

                    val location =
                        locationManager.getLastKnownLocation(
                            provider
                        )

                    if (location != null) {

                        if (
                            bestLocation == null ||
                            location.time >
                            bestLocation!!.time
                        ) {

                            bestLocation =
                                location
                        }
                    }

                } catch (e: SecurityException) {

                    Log.e(
                        TAG,
                        "Unable to read location from $provider",
                        e
                    )
                }
            }

            if (bestLocation == null) {

                Log.e(
                    TAG,
                    "No last known location available"
                )

                return "Location unavailable."
            }

            val latitude =
                bestLocation.latitude

            val longitude =
                bestLocation.longitude

            val mapsLink =
                "https://maps.google.com/?q=$latitude,$longitude"

            Log.e(
                TAG,
                "Background location = $latitude, $longitude"
            )

            "Location:\n$mapsLink"

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Location lookup failed",
                e
            )

            "Location unavailable."
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

            val channel =
                NotificationChannel(
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