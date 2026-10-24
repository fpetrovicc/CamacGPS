package com.camac.tracker.sms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

object SmsSender {
    const val ACTION_SENT = "com.camac.tracker.SMS_SENT"
    const val EXTRA_TEXT = "text"

    fun hasSendPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Šalje komandu. Rezultat slanja (poslato / greška) stiže kao broadcast [ACTION_SENT]
     * sa resultCode == Activity.RESULT_OK ako je uspelo.
     * Vraća grešku kao tekst ako slanje nije ni pokrenuto, inače null.
     */
    fun send(context: Context, number: String, text: String): String? {
        if (number.isBlank()) return "Nije podešen broj trackera"
        if (!hasSendPermission(context)) return "Nema dozvole za slanje SMS-a"

        val maybeSms: SmsManager? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        val sms = maybeSms ?: return "SMS nije dostupan na ovom telefonu"

        val sentIntent = Intent(ACTION_SENT)
            .setPackage(context.packageName)
            .putExtra(EXTRA_TEXT, text)
        val pi = PendingIntent.getBroadcast(
            context,
            (System.currentTimeMillis() and 0xFFFFFFF).toInt(),
            sentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
        return try {
            val parts = sms.divideMessage(text)
            if (parts.size <= 1) {
                sms.sendTextMessage(number, null, text, pi, null)
            } else {
                val intents = ArrayList<PendingIntent?>(parts.size).apply {
                    repeat(parts.size) { add(if (it == parts.size - 1) pi else null) }
                }
                sms.sendMultipartTextMessage(number, null, parts, intents, null)
            }
            null
        } catch (e: Exception) {
            "Slanje nije uspelo: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
