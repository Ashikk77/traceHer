
package com.example.traceher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "traceher/sms"
    private val CHANNEL_V2 = "traceher/sms_v2"
    private val SERVICE_CHANNEL = "traceher/service"

    companion object {
        private const val BATTERY_VOLTAGE_KEY =
            "traceher_battery_voltage"

        private const val BATTERY_PERCENTAGE_KEY =
            "traceher_battery_percentage"

        private const val BATTERY_UPDATED_AT_KEY =
            "traceher_battery_updated_at"
    }

    override fun configureFlutterEngine(
        flutterEngine: FlutterEngine
    ) {

        super.configureFlutterEngine(flutterEngine)

        // ==========================================
        // FOREGROUND SERVICE AND BATTERY CHANNEL
        // ==========================================

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            SERVICE_CHANNEL
        ).setMethodCallHandler { call, result ->

            when (call.method) {

                // ==========================================
                // CHECK NATIVE BLE STATUS
                // ==========================================

                "isBleConnected" -> {

                    val connected =
                        BleForegroundService.isBleConnected()

                    android.util.Log.d(
                        "TraceHerService",
                        "BLE connection status requested: $connected"
                    )

                    result.success(connected)
                }

                // ==========================================
                // GET SAVED BATTERY READING
                // ==========================================

                "getSavedBattery" -> {

                    try {

                        val prefs = getSharedPreferences(
                            "TraceHerNativePrefs",
                            MODE_PRIVATE
                        )

                        val hasBatteryReading =
                            prefs.contains(BATTERY_PERCENTAGE_KEY) &&
                                    prefs.contains(BATTERY_VOLTAGE_KEY)

                        if (!hasBatteryReading) {

                            android.util.Log.d(
                                "TraceHerService",
                                "No saved battery reading available yet"
                            )

                            result.success(null)

                            return@setMethodCallHandler
                        }

                        val voltage = prefs.getFloat(
                            BATTERY_VOLTAGE_KEY,
                            0.0f
                        )

                        val percentage = prefs.getInt(
                            BATTERY_PERCENTAGE_KEY,
                            -1
                        )

                        val updatedAt = prefs.getLong(
                            BATTERY_UPDATED_AT_KEY,
                            0L
                        )

                        val batteryData = mapOf(
                            "voltage" to voltage.toDouble(),
                            "percentage" to percentage,
                            "updatedAt" to updatedAt
                        )

                        android.util.Log.d(
                            "TraceHerService",
                            "Saved battery requested: $batteryData"
                        )

                        result.success(batteryData)

                    } catch (e: Exception) {

                        android.util.Log.e(
                            "TraceHerService",
                            "Failed to read saved battery",
                            e
                        )

                        result.error(
                            "BATTERY_READ_ERROR",
                            "Could not read saved battery information",
                            null
                        )
                    }
                }

                // ==========================================
                // SAVE BLE DEVICE
                // ==========================================

                "saveBleDevice" -> {

                    val deviceId =
                        call.argument<String>("deviceId")

                    if (deviceId.isNullOrBlank()) {

                        android.util.Log.e(
                            "TraceHerService",
                            "Received empty BLE device address"
                        )

                        result.error(
                            "INVALID_DEVICE",
                            "BLE device address is null or empty",
                            null
                        )

                        return@setMethodCallHandler
                    }

                    android.util.Log.e(
                        "TraceHerService",
                        "========================================"
                    )

                    android.util.Log.e(
                        "TraceHerService",
                        "BLE DEVICE RECEIVED FROM FLUTTER"
                    )

                    android.util.Log.e(
                        "TraceHerService",
                        "Device address = $deviceId"
                    )

                    android.util.Log.e(
                        "TraceHerService",
                        "========================================"
                    )

                    // Save device address for native service.

                    val prefs = getSharedPreferences(
                        "TraceHerNativePrefs",
                        MODE_PRIVATE
                    )

                    prefs.edit()
                        .putString(
                            "native_saved_ble_device",
                            deviceId
                        )
                        .apply()

                    android.util.Log.e(
                        "TraceHerService",
                        "Native BLE device address saved = $deviceId"
                    )

                    // Start foreground service again.

                    try {

                        val intent = Intent(
                            this,
                            BleForegroundService::class.java
                        )

                        if (
                            android.os.Build.VERSION.SDK_INT >=
                            android.os.Build.VERSION_CODES.O
                        ) {

                            startForegroundService(intent)

                        } else {

                            startService(intent)
                        }

                        android.util.Log.e(
                            "TraceHerService",
                            "BLE foreground service triggered after saving device"
                        )

                    } catch (e: Exception) {

                        android.util.Log.e(
                            "TraceHerService",
                            "Failed to restart BLE foreground service",
                            e
                        )
                    }

                    result.success(true)
                }

                // ==========================================
                // START SERVICE
                // ==========================================

                "startService" -> {

                    val intent = Intent(
                        this,
                        BleForegroundService::class.java
                    )

                    if (
                        android.os.Build.VERSION.SDK_INT >=
                        android.os.Build.VERSION_CODES.O
                    ) {

                        startForegroundService(intent)

                    } else {

                        startService(intent)
                    }

                    result.success("Service Started")
                }

                // ==========================================
                // START BLE MONITORING
                // ==========================================

                "startBleMonitoring" -> {

                    val intent = Intent(
                        this,
                        BleForegroundService::class.java
                    )

                    if (
                        android.os.Build.VERSION.SDK_INT >=
                        android.os.Build.VERSION_CODES.O
                    ) {

                        startForegroundService(intent)

                    } else {

                        startService(intent)
                    }

                    android.util.Log.d(
                        "TraceHerService",
                        "Native BLE monitoring requested from Flutter"
                    )

                    result.success("BLE Monitoring Started")
                }

                else -> result.notImplemented()
            }
        }

        // ==========================================
        // OLD SMS CHANNEL
        // ==========================================

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->

            if (call.method == "sendSMS") {

                val phone =
                    call.argument<String>("phone")

                val message =
                    call.argument<String>("message")

                if (phone == null || message == null) {

                    result.error(
                        "INVALID",
                        "Phone or message is null",
                        null
                    )

                    return@setMethodCallHandler
                }

                val success = SmsSender.sendSMS(
                    this,
                    phone,
                    message
                )

                if (success) {

                    result.success("SMS Sent")

                } else {

                    result.error(
                        "FAILED",
                        "SMS could not be sent",
                        null
                    )
                }

            } else {

                result.notImplemented()
            }
        }

        // ==========================================
        // SMS V2 CHANNEL
        // ==========================================

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_V2
        ).setMethodCallHandler { call, result ->

            if (call.method == "sendSMSV2") {

                val phone =
                    call.argument<String>("phone")

                val message =
                    call.argument<String>("message")

                if (phone == null || message == null) {

                    result.error(
                        "INVALID",
                        "Phone or message is null",
                        null
                    )

                    return@setMethodCallHandler
                }

                android.util.Log.d(
                    "TraceHerV2",
                    "Sending SMS to: $phone"
                )

                android.util.Log.d(
                    "TraceHerV2",
                    "Message: $message"
                )

                val success = SmsSender.sendSMS(
                    this,
                    phone,
                    message
                )

                if (success) {

                    android.util.Log.d(
                        "TraceHerV2",
                        "SMS Sent Successfully"
                    )

                    result.success("V2 SMS Sent")

                } else {

                    android.util.Log.e(
                        "TraceHerV2",
                        "SMS Failed"
                    )

                    result.error(
                        "FAILED",
                        "SMS could not be sent",
                        null
                    )
                }

            } else {

                result.notImplemented()
            }
        }
    }
}
