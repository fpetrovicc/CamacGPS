package com.camac.tracker.work

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.camac.tracker.data.Exporter
import com.camac.tracker.data.Prefs
import com.camac.tracker.data.SmsRepository
import com.camac.tracker.sms.Notifier
import java.util.concurrent.TimeUnit

/**
 * Nedeljni izvoz istorije.
 *  - Ako je izabran fajl (npr. na Google Drive-u) → prepisuje ga novom istorijom.
 *  - Ako nije ili upis ne uspe → notifikacija "dodirni da sačuvaš", koja otvara deljenje (Drive je jedan dodir).
 */
class ExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val s = prefs.loadSettings()
        if (!s.weeklyExport || s.trackerNumber.isBlank()) return Result.success()

        val messages = SmsRepository.loadConversation(ctx, s.trackerNumber, limit = 10_000)
        if (messages.isEmpty()) return Result.success()
        val csv = Exporter.csv(messages)

        val written = s.exportUri?.let { Exporter.writeToUri(ctx, Uri.parse(it), csv) } ?: false
        if (written) {
            prefs.saveSettings(prefs.loadSettings().copy(lastExportAt = System.currentTimeMillis()))
            Notifier.showExportDone(ctx, messages.size)
        } else {
            val uri = Exporter.writeShareFile(ctx, Exporter.fileName("istorija", "csv"), csv)
            Notifier.showExportReminder(ctx, uri)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "weekly_export"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<ExportWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
