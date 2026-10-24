package com.camac.tracker.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Izvoz istorije u CSV (tabela, otvara se u Excel-u / Google Sheets) i GPX (pozicije za mape). */
object Exporter {

    private val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun fileName(kind: String, ext: String): String =
        "camac_${kind}_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.$ext"

    /** CSV sa ";" (tako ga Excel na srpskom podešavanju odmah deli u kolone) i BOM-om za č/ć/š. */
    fun csv(messages: List<SmsMessage>): String = buildString {
        append('﻿')
        append("Datum;Vreme;Smer;Tip;Baterija %;Lat;Lon;Poruka\n")
        messages.sortedBy { it.date }.forEach { m ->
            val r = m.reply
            val d = Date(m.date)
            append(dateFmt.format(d)).append(';')
            append(timeFmt.format(d)).append(';')
            append(if (m.incoming) "primljeno" else if (m.failed) "neuspelo" else "poslato").append(';')
            append(
                when (r?.kind) {
                    ReplyKind.POSITION -> if (r?.approximate == true) "lokacija (približna)" else "lokacija"
                    ReplyKind.STATUS -> "status"
                    ReplyKind.ACK -> "potvrda"
                    ReplyKind.ALARM -> "ALARM"
                    ReplyKind.OTHER -> "ostalo"
                    null -> "komanda"
                }
            ).append(';')
            append(r?.battery?.toString() ?: "").append(';')
            append(r?.lat?.toString() ?: "").append(';')
            append(r?.lon?.toString() ?: "").append(';')
            append('"').append(m.body.replace("\"", "\"\"").replace("\n", " ")).append('"')
            append('\n')
        }
    }

    /** GPX sa svim GPS pozicijama iz poruka — otvara se u Google Earth-u, OsmAnd-u, Ruhaviku… */
    fun gpx(messages: List<SmsMessage>, name: String): String {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val label = SimpleDateFormat("dd.MM. HH:mm", Locale.getDefault())
        val points = messages.filter { it.reply?.hasPosition == true }.sortedBy { it.date }
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
            append("""<gpx version="1.1" creator="Camac GPS" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
            append("  <metadata><name>").append(xml(name)).append("</name></metadata>\n")
            points.forEach { m ->
                val r = m.reply ?: return@forEach
                append("""  <wpt lat="${r.lat}" lon="${r.lon}">""").append('\n')
                append("    <time>").append(iso.format(Date(m.date))).append("</time>\n")
                append("    <name>").append(label.format(Date(m.date))).append("</name>\n")
                val desc = listOfNotNull(
                    r.battery?.let { "baterija $it%" },
                    if (r.approximate) "približna lokacija" else null
                ).joinToString(", ")
                if (desc.isNotEmpty()) append("    <desc>").append(xml(desc)).append("</desc>\n")
                append("  </wpt>\n")
            }
            append("</gpx>\n")
        }
    }

    private fun xml(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Upisuje fajl u keš i vraća content:// URI za deljenje. */
    fun writeShareFile(context: Context, name: String, content: String): Uri {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, name)
        file.writeText(content, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Sheet za deljenje: korisnik bira Google Drive, Gmail, Vibera… */
    fun shareIntent(uri: Uri, mime: String, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(title, uri)
        return Intent.createChooser(send, title)
    }

    /** Upisuje (prepisuje) sadržaj u fajl koji je korisnik izabrao (npr. na Google Drive-u). */
    fun writeToUri(context: Context, uri: Uri, content: String): Boolean = try {
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            true
        } ?: false
    } catch (e: Exception) {
        false
    }
}
