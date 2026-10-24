package com.camac.tracker.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.camac.tracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

/**
 * Provera i instalacija nove verzije sa GitHub Releases.
 *
 * Kako objaviti verziju:
 *  1. U app/build.gradle.kts povećaj versionCode i versionName (npr. 1.2.0).
 *  2. Napravi potpisan release APK (UVEK istim keystore-om).
 *  3. Na GitHub-u: Releases → Draft new release → tag "v1.2.0" → zakači .apk → Publish.
 * Repozitorijum mora biti javan (ili bar njegovi release-ovi).
 */
object UpdateManager {
    private val repo: String = BuildConfig.UPDATE_REPO
    /** Samo prava (release) verzija se ažurira sama; test verzija ima drugi paket. */
    val enabled: Boolean get() = repo.isNotBlank() && !BuildConfig.DEBUG
    val currentVersion: String get() = BuildConfig.VERSION_NAME

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        val conn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection)
            .apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CamacGPS/${BuildConfig.VERSION_NAME}")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
        try {
            if (conn.responseCode != 200) error("GitHub HTTP ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            if (!isNewer(tag, BuildConfig.VERSION_NAME)) return@withContext null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk", ignoreCase = true)) {
                    return@withContext UpdateInfo(
                        version = tag.removePrefix("v").removePrefix("V"),
                        notes = json.optString("body", ""),
                        apkUrl = a.getString("browser_download_url"),
                        sizeBytes = a.optLong("size", 0),
                    )
                }
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    /** "v1.10.0" > "1.9.2" — poredi brojeve redom. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '_').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "camac-${info.version}.apk")
            val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CamacGPS/${BuildConfig.VERSION_NAME}")
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                if (conn.responseCode != 200) error("Preuzimanje HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            file
        }

    /** Da li je korisnik dozvolio ovoj aplikaciji da instalira APK. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    /** Instalira preko PackageInstaller-a; sistem prikazuje "Ažuriraj?" i aplikacija se restartuje. */
    suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("camac.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(
                context, sessionId,
                Intent(context, InstallResultReceiver::class.java),
                flags
            )
            session.commit(pi.intentSender)
        }
    }
}
