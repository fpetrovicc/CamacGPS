package com.camac.tracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.camac.tracker.data.PhoneNumbers
import com.camac.tracker.data.Prefs
import com.camac.tracker.data.TrackerParser

/**
 * Sistem šalje svaki primljeni SMS ovde. Poruku i dalje čuva tatina obična SMS aplikacija;
 * ovde samo proveravamo da li je od trackera i pravimo notifikaciju sa sažetkom.
 * Ekran aplikacije se osvežava sam preko ContentObserver-a u ViewModel-u.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val settings = Prefs(context).loadSettings()
        val from = parts.first().originatingAddress
        if (!PhoneNumbers.same(from, settings.trackerNumber)) return
        if (!settings.notifications) return

        val body = parts.joinToString("") { it.messageBody ?: "" }
        Notifier.showReply(context, TrackerParser.parse(body), body)
    }
}
