package com.camac.tracker.data

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Vrsta odgovora koji je stigao od trackera. */
enum class ReplyKind { POSITION, STATUS, ACK, ALARM, OTHER }

/**
 * Rezultat parsiranja jednog SMS-a od TK915/TK905.
 * Sva polja su opciona jer TKSTAR klonovi šalju malo različite formate.
 */
data class TrackerReply(
    val kind: ReplyKind,
    val lat: Double? = null,
    val lon: Double? = null,
    val approximate: Boolean = false, // LBS (bazne stanice) umesto pravog GPS-a
    val battery: Int? = null,
    val speedKmh: Double? = null,
    val deviceTime: String? = null,   // vreme iz poruke, tracker ga šalje u UTC
    val gpsSatellites: Int? = null,
    val gsmSignal: Int? = null,
    val server: String? = null,
    val apn: String? = null,
    val firmware: String? = null,
    val alarmText: String? = null,
) {
    val hasPosition: Boolean get() = lat != null && lon != null

    /** Kratak opis za notifikaciju i istoriju. */
    fun summary(): String {
        val parts = mutableListOf<String>()
        when (kind) {
            ReplyKind.ALARM -> parts += "⚠ Alarm: ${alarmText ?: "upozorenje"}"
            ReplyKind.ACK -> parts += "Potvrda primljena"
            ReplyKind.STATUS -> parts += "Status uređaja"
            ReplyKind.POSITION, ReplyKind.OTHER -> Unit
        }
        battery?.let { parts += "baterija $it%" }
        if (hasPosition) parts += if (approximate) "približna lokacija" else "GPS lokacija"
        gpsSatellites?.let { parts += if (it == 0) "nema GPS signala" else "GPS: $it sat." }
        gsmSignal?.let { parts += "mreža: ${gsmLabel(it)}" }
        if (parts.isEmpty()) parts += "Nova poruka od trackera"
        return parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }

    companion object {
        /** GSM (CSQ) vrednost 0–31 u ljudski opis. */
        fun gsmLabel(csq: Int): String = when {
            csq <= 0 || csq == 99 -> "nema"
            csq < 10 -> "slab ($csq)"
            csq < 15 -> "srednji ($csq)"
            csq < 20 -> "dobar ($csq)"
            else -> "odličan ($csq)"
        }
    }
}

object TrackerParser {
    private val opt = setOf(RegexOption.IGNORE_CASE)

    private val latRe = Regex("""\blat(?:itude)?\s*[:=]\s*([NS])?\s*(-?\d{1,3}\.\d+)""", opt)
    private val lonRe = Regex("""\b(?:lon|long|lng|longitude)\s*[:=]\s*([EW])?\s*(-?\d{1,3}\.\d+)""", opt)
    // TK915 šalje npr. maps?q=N44.86149,E19.72416
    private val linkRe = Regex("""[?&](?:q|ll)=\s*([NS])?(-?\d{1,3}\.\d+)\s*,\s*([EW])?(-?\d{1,3}\.\d+)""", opt)
    private val batRe = Regex("""\bbat(?:tery)?\s*[:=]?\s*(\d{1,3})\s*%""", opt)
    private val speedRe = Regex("""\b(?:speed|spd)\s*[:=]\s*(\d+(?:\.\d+)?)""", opt)
    private val timeRe = Regex("""\bT\s*:\s*(\d{2}/\d{2}/\d{2}\s+\d{1,2}:\d{2}(?::\d{2})?)""", opt)
    private val gpsRe = Regex("""\bGPS\s*:\s*(\d{1,2})\b""", opt)
    private val gsmRe = Regex("""\bGSM\s*:\s*(\d{1,2})\b""", opt)
    private val ipRe = Regex("""\bIP\s*:\s*([^\s,]+)""", opt)
    private val apnRe = Regex("""\bAPN\s*:\s*([^,\s]*)""", opt)
    private val fwRe = Regex("""\b([A-Z]{2}_TK\d{3}\S*)""", opt)
    private val lbsRe = Regex("""\blac\s*:""", opt)
    private val ackRe = Regex("""^\s*[a-z]+(?:\s+[a-z]+)?\s+ok!?\s*$""", opt)
    private val alarmRe = Regex(
        """(help me|\bsos\b(?![\d:])|low ?bat\w*|move!|moving|shock alarm|vibration|out of (?:fence|area)|geo-?fence|overspeed|speed alarm|power off|power cut)""",
        opt
    )

    fun parse(body: String): TrackerReply {
        val text = body.trim()

        var lat: Double? = null
        var lon: Double? = null
        latRe.find(text)?.let { m ->
            val v = m.groupValues[2].toDoubleOrNull()
            lat = if (m.groupValues[1].equals("S", true) && v != null) -v else v
        }
        lonRe.find(text)?.let { m ->
            val v = m.groupValues[2].toDoubleOrNull()
            lon = if (m.groupValues[1].equals("W", true) && v != null) -v else v
        }
        if (lat == null || lon == null) {
            linkRe.find(text)?.let { m ->
                val la = m.groupValues[2].toDoubleOrNull()
                val lo = m.groupValues[4].toDoubleOrNull()
                lat = if (m.groupValues[1].equals("S", true) && la != null) -la else la
                lon = if (m.groupValues[3].equals("W", true) && lo != null) -lo else lo
            }
        }
        // (0,0) znači "nema fiksa" kod nekih firmvera
        if (lat == 0.0 && lon == 0.0) { lat = null; lon = null }

        val battery = batRe.find(text)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
        val gps = gpsRe.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val gsm = gsmRe.find(text)?.groupValues?.get(1)?.toIntOrNull()
        val server = ipRe.find(text)?.groupValues?.get(1)
        val apn = apnRe.find(text)?.groupValues?.get(1)
        val approximate = lbsRe.containsMatchIn(text)

        val isAck = ackRe.matches(text)
        val alarm = if (isAck) null else alarmRe.find(text)?.value

        val kind = when {
            server != null || apn != null -> ReplyKind.STATUS // odgovor na check
            alarm != null -> ReplyKind.ALARM
            isAck -> ReplyKind.ACK
            gsm != null -> ReplyKind.STATUS
            lat != null && lon != null -> ReplyKind.POSITION
            else -> ReplyKind.OTHER
        }

        return TrackerReply(
            kind = kind,
            lat = lat,
            lon = lon,
            approximate = approximate,
            battery = battery,
            speedKmh = speedRe.find(text)?.groupValues?.get(1)?.toDoubleOrNull(),
            deviceTime = timeRe.find(text)?.groupValues?.get(1),
            gpsSatellites = gps,
            gsmSignal = gsm,
            server = server,
            apn = apn,
            firmware = fwRe.find(text)?.groupValues?.get(1),
            alarmText = alarm,
        )
    }

    /** Udaljenost u metrima (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
