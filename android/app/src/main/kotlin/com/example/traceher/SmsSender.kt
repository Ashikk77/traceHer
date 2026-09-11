package com.example.traceher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat

object SmsSender {

    fun sendSMS(
        context: Context,
        phone: String,
        message: String
    ): Boolean {

        if (
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            android.util.Log.e(
                "TraceHerSMS",
                "SEND_SMS permission not granted"
            )

            return false
        }

        return try {

            val smsManager =
                context.getSystemService(
                    SmsManager::class.java
                )

            val parts =
                smsManager.divideMessage(message)

            smsManager.sendMultipartTextMessage(
                phone,
                null,
                parts,
                null,
                null
            )

            android.util.Log.d(
                "TraceHerSMS",
                "SMS sent successfully to: $phone"
            )

            true

        } catch (e: Exception) {

            android.util.Log.e(
                "TraceHerSMS",
                "SMS sending failed to: $phone",
                e
            )

            false
        }
    }
}