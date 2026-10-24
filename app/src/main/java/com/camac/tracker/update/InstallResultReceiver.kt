package com.camac.tracker.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.camac.tracker.sms.Notifier

/** Sistem javlja rezultat instalacije. Ako traži potvrdu korisnika — prikazujemo sistemski dijalog. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // aplikacija se restartuje sa novom verzijom
            else -> Notifier.showUpdateError(
                context,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Instalacija nije uspela"
            )
        }
    }
}
