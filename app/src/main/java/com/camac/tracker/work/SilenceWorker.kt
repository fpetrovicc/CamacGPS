package com.camac.tracker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.camac.tracker.data.Prefs
import com.camac.tracker.data.SilenceCheck
import com.camac.tracker.data.SmsRepository
import com.camac.tracker.sms.Notifier
import java.util.concurrent.TimeUnit

/**
 * Jednom dnevno proveri kad je stigla poslednja poruka od trackera.
 * Ako je prošlo više dana nego što piše u podešavanjima — notifikacija sa "Pošalji proveru" / "U redu".
 * NE šalje SMS sam od sebe.
 */
class SilenceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val s = prefs.loadSettings()
        if (s.trackerNumber.isBlank() || !s.notifications || s.staleAlertDays <= 0) return Result.success()

        val last = SmsRepository.loadConversation(ctx, s.trackerNumber, limit = 200)
            .firstOrNull { it.incoming }?.date ?: return Result.success()
        val now = System.currentTimeMillis()

        if (SilenceCheck.shouldNotify(last, s, now)) {
            Notifier.showSilence(ctx, SilenceCheck.daysSince(last, now), last)
            prefs.saveSettings(prefs.loadSettings().copy(staleNotifiedAt = now))
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "silence_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SilenceWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
