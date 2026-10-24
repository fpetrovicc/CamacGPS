package com.camac.tracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.cos
import kotlin.math.floor

data class Weather(
    val tempC: Double,
    val windKmh: Double,
    val gustKmh: Double,
    val windDirDeg: Int,
    val pressureHpa: Double,
    val pressureTrend3h: Double?, // hPa promena u poslednja 3 sata
    val code: Int,
    val sunrise: String?,
    val sunset: String?,
    val fetchedAt: Long,
) {
    val description: String get() = weatherLabel(code)
    val windDir: String get() = windDirLabel(windDirDeg)

    val pressureLabel: String
        get() {
            val t = pressureTrend3h ?: return "%.0f hPa".format(Locale.US, pressureHpa)
            val trend = when {
                t <= -2.0 -> "brzo pada"
                t <= -0.7 -> "pada"
                t >= 2.0 -> "brzo raste"
                t >= 0.7 -> "raste"
                else -> "stabilan"
            }
            return "%.0f hPa · %s".format(Locale.US, pressureHpa, trend)
        }

    /** Kratak pecaroški savet — iskustveno pravilo, ne nauka. */
    val fishingHint: String
        get() {
            val t = pressureTrend3h
            return when {
                windKmh >= 30 || gustKmh >= 45 -> "Jak vetar — oprez na vodi."
                t != null && t <= -0.7 && t > -2.0 -> "Pritisak lagano pada — riba je često aktivnija pred promenu vremena."
                t != null && t <= -2.0 -> "Pritisak naglo pada — moguće nevreme."
                t != null && t >= 2.0 -> "Pritisak naglo raste — riba je često tromija."
                else -> "Stabilni uslovi."
            }
        }
}

/** Faza meseca, računa se lokalno (bez interneta). */
data class MoonPhase(val name: String, val illumination: Int, val ageDays: Double)

object WeatherRepo {

    /** Open-Meteo — besplatno, bez ključa. */
    suspend fun fetch(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m,pressure_msl" +
                "&hourly=pressure_msl&daily=sunrise,sunset&timezone=auto&past_days=1&forecast_days=1",
            lat, lon
        )
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        val json = try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }

        val cur = json.getJSONObject("current")
        val curTime = cur.getString("time") // "2026-10-24T15:15"
        val hourKey = curTime.take(13) + ":00"
        val today = curTime.take(10)

        val hourly = json.getJSONObject("hourly")
        val hTimes = hourly.getJSONArray("time")
        val hPress = hourly.getJSONArray("pressure_msl")
        var trend: Double? = null
        for (i in 0 until hTimes.length()) {
            if (hTimes.getString(i) == hourKey && i >= 3 && !hPress.isNull(i) && !hPress.isNull(i - 3)) {
                trend = hPress.getDouble(i) - hPress.getDouble(i - 3)
                break
            }
        }

        val daily = json.getJSONObject("daily")
        val dTimes = daily.getJSONArray("time")
        var sunrise: String? = null
        var sunset: String? = null
        for (i in 0 until dTimes.length()) {
            if (dTimes.getString(i) == today) {
                sunrise = daily.getJSONArray("sunrise").optString(i).takeIf { it.length >= 16 }?.substring(11, 16)
                sunset = daily.getJSONArray("sunset").optString(i).takeIf { it.length >= 16 }?.substring(11, 16)
            }
        }

        Weather(
            tempC = cur.getDouble("temperature_2m"),
            windKmh = cur.getDouble("wind_speed_10m"),
            gustKmh = cur.optDouble("wind_gusts_10m", 0.0),
            windDirDeg = cur.optInt("wind_direction_10m", 0),
            pressureHpa = cur.getDouble("pressure_msl"),
            pressureTrend3h = trend,
            code = cur.optInt("weather_code", 0),
            sunrise = sunrise,
            sunset = sunset,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    fun moonPhase(ms: Long = System.currentTimeMillis()): MoonPhase {
        val synodic = 29.530588853
        val jd = ms / 86_400_000.0 + 2440587.5
        val cycles = (jd - 2451550.1) / synodic
        val age = (cycles - floor(cycles)) * synodic
        val illum = ((1 - cos(2 * Math.PI * age / synodic)) / 2 * 100).toInt()
        val name = when {
            age < 1.85 -> "Mlad mesec"
            age < 5.54 -> "Rastući srp"
            age < 9.23 -> "Prva četvrt"
            age < 12.92 -> "Rastući mesec"
            age < 16.61 -> "Pun mesec"
            age < 20.30 -> "Opadajući mesec"
            age < 23.99 -> "Poslednja četvrt"
            age < 27.68 -> "Opadajući srp"
            else -> "Mlad mesec"
        }
        return MoonPhase(name, illum, age)
    }
}

fun windDirLabel(deg: Int): String {
    val dirs = listOf("S", "SI", "I", "JI", "J", "JZ", "Z", "SZ")
    return dirs[((deg % 360 + 360) % 360 + 22) / 45 % 8]
}

/** WMO kodovi vremena (Open-Meteo) na srpskom. */
fun weatherLabel(code: Int): String = when (code) {
    0 -> "Vedro"
    1 -> "Pretežno vedro"
    2 -> "Delimično oblačno"
    3 -> "Oblačno"
    45, 48 -> "Magla"
    51, 53, 55, 56, 57 -> "Rosulja"
    61, 63, 66 -> "Kiša"
    65, 67 -> "Jaka kiša"
    71, 73, 75, 77 -> "Sneg"
    80, 81 -> "Pljuskovi"
    82 -> "Jaki pljuskovi"
    85, 86 -> "Snežni pljuskovi"
    95 -> "Grmljavina"
    96, 99 -> "Grmljavina sa gradom"
    else -> "—"
}
