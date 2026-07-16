package com.example.traceher

import android.Manifest
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL = "traceher/sms"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->

                if (call.method == "sendSMS") {

                    val phone = call.argument<String>("phone")
                    val message = call.argument<String>("message")

                    if (phone == null || message == null) {
                        result.error("INVALID", "Phone or message is null", null)
                        return@setMethodCallHandler
                    }

                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.SEND_SMS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        result.error("PERMISSION", "SEND_SMS permission denied", null)
                        return@setMethodCallHandler
                    }

                    try {
                        val smsManager = getSystemService(SmsManager::class.java)

                        android.util.Log.d("TraceHer", "Phone: $phone")
                        android.util.Log.d("TraceHer", "Message: $message")

                        smsManager.sendTextMessage(
                            phone,
                            null,
                            message,
                            null,
                            null
                        )

                        android.util.Log.d("TraceHer", "SmsManager executed")

                        result.success("SMS Sent")

                    } catch (e: Exception) {
                        android.util.Log.e("TraceHer", "SMS Error", e)
                        result.error("FAILED", e.message, null)
                    }
                } else {
                    result.notImplemented()
                }
            }
    }
}