package com.camac.tracker.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Settings(
    val boatName: String = "Čamac",
    val trackerNumber: String = "",
    val adminNumber: String = "",
    val pin: String = "123456",
    val fishInterval: Int = 60,     // s, dok se peca
    val normalInterval: Int = 300,  // s, dok stoji na vezu
    val moveRadius: Int = 100,      // m, za alarm pomeranja
    val homeLat: Double? = null,
    val homeLon: Double? = null,
    val homeRadiusM: Int = 150,
    val notifications: Boolean = true,
    val weeklyExport: Boolean = false,
    val exportUri: String? = null,
    val lastExportAt: Long = 0,
    val fishingSince: Long? = null, // pecanje u toku od…
    val pendingPin: String? = null, // novi PIN koji čeka potvrdu trackera
    val staleAlertDays: Int = 7,    // upozori ako nema poruka od trackera ovoliko dana (0 = isključeno)
    val staleDismissedAt: Long = 0, // kad je korisnik poslednji put rekao "U redu" / "Pošalji proveru"
    val staleNotifiedAt: Long = 0,  // kad je poslednji put poslata notifikacija o tome
) {
    val hasHome: Boolean get() = homeLat != null && homeLon != null
    val isFishing: Boolean get() = fishingSince != null
}

data class FishingSession(val start: Long, val end: Long) {
    val durationMin: Long get() = (end - start) / 60_000
}

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("camac", Context.MODE_PRIVATE)

    fun loadSettings(): Settings = Settings(
        boatName = sp.getString("boat_name", null) ?: "Čamac",
        trackerNumber = sp.getString("tracker_number", "") ?: "",
        adminNumber = sp.getString("admin_number", "") ?: "",
        pin = sp.getString("password", "123456") ?: "123456",
        fishInterval = sp.getInt("fish_interval", 60),
        normalInterval = sp.getInt("normal_interval", 300),
        moveRadius = sp.getInt("move_radius", 100),
        homeLat = sp.getString("home_lat", null)?.toDoubleOrNull(),
        homeLon = sp.getString("home_lon", null)?.toDoubleOrNull(),
        homeRadiusM = sp.getInt("home_radius", 150),
        notifications = sp.getBoolean("notifications", true),
        weeklyExport = sp.getBoolean("weekly_export", false),
        exportUri = sp.getString("export_uri", null),
        lastExportAt = sp.getLong("last_export", 0),
        fishingSince = sp.getLong("fishing_since", 0).takeIf { it > 0 },
        pendingPin = sp.getString("pending_pin", null),
        staleAlertDays = sp.getInt("stale_alert_days", 7),
        staleDismissedAt = sp.getLong("stale_dismissed_at", 0),
        staleNotifiedAt = sp.getLong("stale_notified_at", 0),
    )

    fun saveSettings(s: Settings) {
        sp.edit()
            .putString("boat_name", s.boatName)
            .putString("tracker_number", s.trackerNumber)
            .putString("admin_number", s.adminNumber)
            .putString("password", s.pin)
            .putInt("fish_interval", s.fishInterval)
            .putInt("normal_interval", s.normalInterval)
            .putInt("move_radius", s.moveRadius)
            .putString("home_lat", s.homeLat?.toString())
            .putString("home_lon", s.homeLon?.toString())
            .putInt("home_radius", s.homeRadiusM)
            .putBoolean("notifications", s.notifications)
            .putBoolean("weekly_export", s.weeklyExport)
            .putString("export_uri", s.exportUri)
            .putLong("last_export", s.lastExportAt)
            .putLong("fishing_since", s.fishingSince ?: 0)
            .putString("pending_pin", s.pendingPin)
            .putInt("stale_alert_days", s.staleAlertDays)
            .putLong("stale_dismissed_at", s.staleDismissedAt)
            .putLong("stale_notified_at", s.staleNotifiedAt)
            .apply()
    }

    fun loadSessions(): List<FishingSession> = try {
        val arr = JSONArray(sp.getString("sessions", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            FishingSession(o.getLong("start"), o.getLong("end"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Čuva najviše 100 poslednjih pecanja, najnovije prvo. */
    fun saveSessions(list: List<FishingSession>) {
        val arr = JSONArray()
        list.take(100).forEach { arr.put(JSONObject().put("start", it.start).put("end", it.end)) }
        sp.edit().putString("sessions", arr.toString()).apply()
    }

    var lastUpdateCheck: Long
        get() = sp.getLong("last_update_check", 0)
        set(v) = sp.edit().putLong("last_update_check", v).apply()
}
