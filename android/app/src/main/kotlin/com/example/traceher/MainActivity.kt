package com.example.traceher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "traceher/sms"
    private val CHANNEL_V2 = "traceher/sms_v2"
    private val SERVICE_CHANNEL = "traceher/service"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // ==========================
        // FOREGROUND SERVICE CHANNEL
        // ==========================
        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            SERVICE_CHANNEL
        ).setMethodCallHandler { call, result ->

            when (call.method) {

                "startService" -> {

                    val intent = Intent(this, BleForegroundService::class.java)

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }

                    result.success("Service Started")
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

                val phone = call.argument<String>("phone")
                val message = call.argument<String>("message")

                if (phone == null || message == null) {

                    result.error(
                        "INVALID",
                        "Phone or message is null",
                        null
                    )

                    return@setMethodCallHandler
                }

                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.SEND_SMS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                    result.error(
                        "PERMISSION",
                        "SEND_SMS permission denied",
                        null
                    )

                    return@setMethodCallHandler
                }

                try {

                    val smsManager =
                        getSystemService(SmsManager::class.java)

                    val parts = smsManager.divideMessage(message)

                    smsManager.sendMultipartTextMessage(
                        phone,
                        null,
                        parts,
                        null,
                        null
                    )

                    result.success("SMS Sent")

                } catch (e: Exception) {

                    result.error(
                        "FAILED",
                        e.message,
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

                val phone = call.argument<String>("phone")
                val message = call.argument<String>("message")

                if (phone == null || message == null) {

                    result.error(
                        "INVALID",
                        "Phone or message is null",
                        null
                    )

                    return@setMethodCallHandler
                }

                try {

                    val smsManager =
                        getSystemService(SmsManager::class.java)

                    android.util.Log.d(
                        "TraceHerV2",
                        "Sending SMS to: $phone"
                    )

                    android.util.Log.d(
                        "TraceHerV2",
                        "Message: $message"
                    )

                    val parts = smsManager.divideMessage(message)

                    smsManager.sendMultipartTextMessage(
                        phone,
                        null,
                        parts,
                        null,
                        null
                    )

                    android.util.Log.d(
                        "TraceHerV2",
                        "SMS Sent Successfully"
                    )

                    result.success("V2 SMS Sent")

                } catch (e: Exception) {

                    android.util.Log.e(
                        "TraceHerV2",
                        "SMS Failed",
                        e
                    )

                    result.error(
                        "FAILED",
                        e.message,
                        null
                    )

                }

            } else {

                result.notImplemented()

            }

        }

    }

}