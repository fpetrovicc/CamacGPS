package com.camac.tracker.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

/** Jedna poruka iz prepiske sa trackerom (čita se iz sistemskog SMS sandučeta). */
data class SmsMessage(
    val id: Long,
    val body: String,
    val date: Long,
    val incoming: Boolean,
    val failed: Boolean,
) {
    /** Parsira se samo za dolazne poruke, jednom. */
    val reply: TrackerReply? by lazy { if (incoming) TrackerParser.parse(body) else null }
}

object PhoneNumbers {
    private fun digits(s: String) = s.filter { it.isDigit() }

    /** Poslednjih 8 cifara — poklapa "+38161…", "0038161…" i "061…". */
    fun key(number: String): String = digits(number).takeLast(8)

    fun same(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val ka = key(a)
        return ka.length >= 6 && ka == key(b)
    }
}

object SmsRepository {

    fun hasReadPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Vraća prepisku sa trackerom, najnovije prvo.
     * Poslate komande upisuje sistem sam (i kad aplikacija nije podrazumevana SMS aplikacija),
     * pa istorija sadrži i ono što je poslato iz ove aplikacije i iz običnih Poruka.
     */
    fun loadConversation(context: Context, trackerNumber: String, limit: Int = 500): List<SmsMessage> {
        if (trackerNumber.isBlank() || !hasReadPermission(context)) return emptyList()
        val key = PhoneNumbers.key(trackerNumber)
        if (key.length < 6) return emptyList()

        val out = ArrayList<SmsMessage>()
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY,
            Telephony.Sms.DATE, Telephony.Sms.TYPE
        )
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.ADDRESS} LIKE ?",
                arrayOf("%$key"),
                "${Telephony.Sms.DATE} DESC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val iType = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (c.moveToNext() && out.size < limit) {
                    if (!PhoneNumbers.same(c.getString(iAddr), trackerNumber)) continue
                    val type = c.getInt(iType)
                    out += SmsMessage(
                        id = c.getLong(iId),
                        body = c.getString(iBody) ?: "",
                        date = c.getLong(iDate),
                        incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                        failed = type == Telephony.Sms.MESSAGE_TYPE_FAILED,
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }
}
