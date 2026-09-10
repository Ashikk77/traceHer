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
import android.bluetooth.BluetoothGattService
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
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var isScanning = false
    private var isConnected = false
    private var notificationsReady = false

    private val handler =
        Handler(Looper.getMainLooper())

    private var reconnectRunnable: Runnable? = null

    private val serviceUUID =
        UUID.fromString(SERVICE_UUID)

    private val characteristicUUID =
        UUID.fromString(CHARACTERISTIC_UUID)

    private val cccdUUID =
        UUID.fromString(CCCD_UUID)

    override fun onCreate() {
        super.onCreate()

        Log.e(TAG, "BleForegroundService created")

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
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .build()

        startForeground(
            NOTIFICATION_ID,
            notification
        )

        val bluetoothManager =
            getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        bluetoothAdapter =
            bluetoothManager.adapter

        scanner =
            bluetoothAdapter?.bluetoothLeScanner
    }

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

        val savedAddress =
            prefs.getString(
                "flutter.$SAVED_DEVICE_KEY",
                null
            )

        Log.e(
            TAG,
            "Saved BLE device = $savedAddress"
        )

        if (
            !isConnected &&
            !isScanning &&
            !savedAddress.isNullOrBlank()
        ) {
            startBleScan(savedAddress)
        } else {

            Log.e(
                TAG,
                "BLE already active - no scan needed"
            )
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {

        Log.e(
            TAG,
            "onTaskRemoved() - foreground service still running"
        )

        if (
            !isConnected &&
            !isScanning
        ) {

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

            if (!savedAddress.isNullOrBlank()) {

                Log.e(
                    TAG,
                    "Task removed - BLE inactive, starting scan"
                )

                startBleScan(savedAddress)

            } else {

                Log.e(
                    TAG,
                    "Task removed - no saved BLE address"
                )
            }

        } else {

            Log.e(
                TAG,
                "Task removed - BLE already active, no scan needed"
            )
        }

        super.onTaskRemoved(rootIntent)
    }

    private fun startBleScan(
        address: String
    ) {

        if (isConnected) {

            Log.e(
                TAG,
                "Scan request ignored - already connected"
            )

            return
        }

        if (isScanning) {

            Log.e(
                TAG,
                "Scan request ignored - scan already running"
            )

            return
        }

        val bluetoothScanner =
            scanner

        if (bluetoothScanner == null) {

            Log.e(
                TAG,
                "Bluetooth scanner unavailable"
            )

            return
        }

        Log.e(
            TAG,
            "Starting filtered BLE scan for $address"
        )

        val filter =
            ScanFilter.Builder()
                .setDeviceAddress(address)
                .build()

        val settings =
            ScanSettings.Builder()
                .setScanMode(
                    ScanSettings.SCAN_MODE_LOW_LATENCY
                )
                .build()

        isScanning = true

        bluetoothScanner.startScan(
            listOf(filter),
            settings,
            scanCallback
        )

        Log.e(
            TAG,
            "BLE Scan Started"
        )
    }

    private fun stopBleScan() {

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
            "BLE Scan Stopped"
        )
    }

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
                    "TraceHer found: ${device.address}"
                )

                Log.e(
                    TAG,
                    "Target ESP32 Found!"
                )

                stopBleScan()

                connectToDevice(device)
            }

            override fun onScanFailed(
                errorCode: Int
            ) {

                isScanning = false

                Log.e(
                    TAG,
                    "BLE scan failed. Error=$errorCode"
                )
            }
        }

    private fun connectToDevice(
        device: BluetoothDevice
    ) {

        if (isConnected) {

            Log.e(
                TAG,
                "Connection request ignored - already connected"
            )

            return
        }

        Log.e(
            TAG,
            "Connecting to ESP32..."
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
                "connectGatt() called"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "connectGatt failed",
                e
            )

            scheduleReconnect()
        }
    }

    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                Log.e(
                    TAG,
                    "GATT state changed. Status=$status State=$newState"
                )

                if (
                    newState ==
                    BluetoothGatt.STATE_CONNECTED
                ) {

                    isConnected = true
                    notificationsReady = false

                    Log.e(
                        TAG,
                        "GATT CONNECTED"
                    )

                    stopBleScan()

                    Log.e(
                        TAG,
                        "BLE scanning disabled while connected"
                    )

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

                val service =
                    gatt.getService(
                        serviceUUID
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
                        characteristicUUID
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
                        cccdUUID
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

                val writeStarted =
                    gatt.writeDescriptor(
                        descriptor
                    )

                Log.e(
                    TAG,
                    "CCCD write started = $writeStarted"
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
                    descriptor.uuid == cccdUUID &&
                    status ==
                    BluetoothGatt.GATT_SUCCESS
                ) {

                    notificationsReady = true

                    Log.e(
                        TAG,
                        "BLE NOTIFICATIONS READY"
                    )
                }
            }

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

    private fun scheduleReconnect() {

        reconnectRunnable?.let {
            handler.removeCallbacks(it)
        }

        reconnectRunnable =
            Runnable {

                if (
                    isConnected ||
                    isScanning
                ) {

                    Log.e(
                        TAG,
                        "Reconnect skipped - BLE already active"
                    )

                    return@Runnable
                }

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

                if (
                    savedAddress.isNullOrBlank()
                ) {

                    Log.e(
                        TAG,
                        "Reconnect skipped - no saved BLE address"
                    )

                    return@Runnable
                }

                Log.e(
                    TAG,
                    "Starting BLE reconnect scan"
                )

                startBleScan(savedAddress)
            }

        handler.postDelayed(
            reconnectRunnable!!,
            RECONNECT_DELAY
        )

        Log.e(
            TAG,
            "BLE reconnect scheduled in 3 seconds"
        )
    }

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

    override fun onDestroy() {

        Log.e(
            TAG,
            "BleForegroundService destroyed"
        )

        reconnectRunnable?.let {
            handler.removeCallbacks(it)
        }

        stopBleScan()

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
        notificationsReady = false

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}