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

    override fun configureFlutterEngine(
        flutterEngine: FlutterEngine
    ) {

        super.configureFlutterEngine(flutterEngine)

        // ==========================
        // FOREGROUND SERVICE CHANNEL
        // ==========================

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            SERVICE_CHANNEL
        ).setMethodCallHandler { call, result ->

            when (call.method) {

                "isBleConnected" -> {

                    val connected =
                        BleForegroundService.isBleConnected()

                    android.util.Log.d(
                        "TraceHerService",
                        "BLE connection status requested: $connected"
                    )

                    result.success(connected)
                }

                "startService" -> {

                    val intent =
                        Intent(
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

                "startBleMonitoring" -> {

                    val intent =
                        Intent(
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

        // ==========================
        // OLD SMS CHANNEL
        // ==========================

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL
        ).setMethodCallHandler { call, result ->

            if (call.method == "sendSMS") {

                val phone =
                    call.argument<String>("phone")

                val message =
                    call.argument<String>("message")

                if (
                    phone == null ||
                    message == null
                ) {

                    result.error(
                        "INVALID",
                        "Phone or message is null",
                        null
                    )

                    return@setMethodCallHandler
                }

                val success =
                    SmsSender.sendSMS(
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

        // ==========================
        // SMS V2 CHANNEL
        // ==========================

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            CHANNEL_V2
        ).setMethodCallHandler { call, result ->

            if (call.method == "sendSMSV2") {

                val phone =
                    call.argument<String>("phone")

                val message =
                    call.argument<String>("message")

                if (
                    phone == null ||
                    message == null
                ) {

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

                val success =
                    SmsSender.sendSMS(
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