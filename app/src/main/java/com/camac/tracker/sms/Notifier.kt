package com.camac.tracker.sms

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.camac.tracker.MainActivity
import com.camac.tracker.R
import com.camac.tracker.data.Exporter
import com.camac.tracker.data.ReplyKind
import com.camac.tracker.data.TrackerReply

object Notifier {
    private const val CH_REPLIES = "replies"
    private const val CH_ALARMS = "alarms"
    private const val CH_EXPORT = "export"
    private const val CH_UPDATES = "updates"

    private const val ID_EXPORT = 2001
    private const val ID_UPDATE = 2002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf(
            NotificationChannel(CH_REPLIES, "Odgovori trackera", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Kad tracker odgovori na komandu" },
            NotificationChannel(CH_ALARMS, "Alarmi čamca", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Pomeranje, slaba baterija i slična upozorenja" },
            NotificationChannel(CH_EXPORT, "Izvoz istorije", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Nedeljni izvoz poruka na Google Drive" },
            NotificationChannel(CH_UPDATES, "Ažuriranja aplikacije", NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach(nm::createNotificationChannel)
    }

    private fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (!canNotify(context)) return
        createChannels(context)
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // dozvola povučena u međuvremenu
        }
    }

    fun showReply(context: Context, reply: TrackerReply, body: String) {
        val isAlarm = reply.kind == ReplyKind.ALARM
        val b = NotificationCompat.Builder(context, if (isAlarm) CH_ALARMS else CH_REPLIES)
            .setSmallIcon(R.drawable.ic_stat_boat)
            .setContentTitle(if (isAlarm) "Čamac — alarm" else "Čamac je odgovorio")
            .setContentText(reply.summary())
            .setStyle(NotificationCompat.BigTextStyle().bigText(reply.summary() + "\n\n" + body))
            .setPriority(if (isAlarm) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setColor(ContextCompat.getColor(context, R.color.pine))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
        post(context, (System.currentTimeMillis() and 0xFFFFFFF).toInt(), b)
    }

    fun showExportDone(context: Context, count: Int) {
        post(
            context, ID_EXPORT,
            NotificationCompat.Builder(context, CH_EXPORT)
                .setSmallIcon(R.drawable.ic_stat_boat)
                .setContentTitle("Istorija sačuvana")
                .setContentText("Nedeljni izvoz: $count poruka upisano u izabrani fajl.")
                .setColor(ContextCompat.getColor(context, R.color.pine))
                .setAutoCancel(true)
        )
    }

    fun showExportReminder(context: Context, fileUri: Uri) {
        val share = Exporter.shareIntent(fileUri, "text/csv", "Istorija čamca")
        val pi = PendingIntent.getActivity(
            context, 1, share, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        post(
            context, ID_EXPORT,
            NotificationCompat.Builder(context, CH_EXPORT)
                .setSmallIcon(R.drawable.ic_stat_boat)
                .setContentTitle("Nedeljni izvoz je spreman")
                .setContentText("Dodirni i izaberi Google Drive da sačuvaš istoriju poruka.")
                .setColor(ContextCompat.getColor(context, R.color.pine))
                .setContentIntent(pi)
                .setAutoCancel(true)
        )
    }

    fun showUpdateAvailable(context: Context, version: String) {
        post(
            context, ID_UPDATE,
            NotificationCompat.Builder(context, CH_UPDATES)
                .setSmallIcon(R.drawable.ic_stat_boat)
                .setContentTitle("Nova verzija $version")
                .setContentText("Dodirni i pritisni \"Ažuriraj\" na početnom ekranu.")
                .setColor(ContextCompat.getColor(context, R.color.pine))
                .setContentIntent(openAppIntent(context))
                .setAutoCancel(true)
        )
    }

    fun showUpdateError(context: Context, message: String) {
        post(
            context, ID_UPDATE,
            NotificationCompat.Builder(context, CH_UPDATES)
                .setSmallIcon(R.drawable.ic_stat_boat)
                .setContentTitle("Ažuriranje nije uspelo")
                .setContentText(message)
                .setAutoCancel(true)
        )
    }
}
