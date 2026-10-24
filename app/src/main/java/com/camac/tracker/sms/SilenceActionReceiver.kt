package com.camac.tracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.camac.tracker.data.CommandCatalog
import com.camac.tracker.data.Prefs

/** Dugmad iz notifikacije "Nema podataka od trackera": "Pošalji proveru" ili "U redu". */
class SilenceActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        val s = prefs.loadSettings()

        if (intent.action == ACTION_CHECK) {
            val cmd = CommandCatalog.byId(CommandCatalog.LOCATION)
            val error = cmd.missing(s) ?: SmsSender.send(context, s.trackerNumber, cmd.resolve(s))
            Toast.makeText(
                context,
                error ?: "Poslato: ${cmd.label(s)} — odgovor stiže za minut-dva",
                Toast.LENGTH_LONG
            ).show()
        }

        prefs.saveSettings(s.copy(staleDismissedAt = System.currentTimeMillis()))
        Notifier.cancelSilence(context)
    }

    companion object {
        const val ACTION_CHECK = "com.camac.tracker.SILENCE_CHECK"
        const val ACTION_DISMISS = "com.camac.tracker.SILENCE_DISMISS"
    }
}
