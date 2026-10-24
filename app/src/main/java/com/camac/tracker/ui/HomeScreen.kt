package com.camac.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.camac.tracker.data.CommandCatalog
import com.camac.tracker.data.TrackerCommand
import com.camac.tracker.data.TrackerParser
import com.camac.tracker.data.TrackerReply
import com.camac.tracker.data.WeatherRepo
import java.util.Locale

@Composable
fun HomeScreen(
    state: UiState,
    now: Long,
    onRun: (TrackerCommand) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onRequestPermissions: () -> Unit,
    onDismissPending: () -> Unit,
    onRefreshWeather: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { onRefreshWeather() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.missingPermissions.isNotEmpty()) PermissionCard(onRequestPermissions)
        state.update?.let { UpdateBanner(it.version, state.updateProgress, onInstallUpdate) }

        if (!state.isConfigured) {
            SetupCard(onOpenSettings)
            return@Column
        }

        state.recentAlarm?.let { AlarmCard(it.body, it.date, now) }
        state.pending?.let { PendingCard(it, now, onDismissPending) }

        HeroCard(state, now)

        // --- Pecanje ---
        SectionLabel("Pecanje")
        FishingButtons(state, onRun)

        // --- Brze komande ---
        SectionLabel("Brze komande")
        val s = state.settings
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(CommandCatalog.byId(CommandCatalog.LOCATION), Icons.Default.MyLocation, s, onRun, Modifier.weight(1f))
            QuickTile(CommandCatalog.byId(CommandCatalog.CHECK), Icons.Default.SignalCellularAlt, s, onRun, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickTile(CommandCatalog.byId(CommandCatalog.MOVE), Icons.Default.NotificationsActive, s, onRun, Modifier.weight(1f))
            QuickTile(CommandCatalog.byId(CommandCatalog.NOMOVE), Icons.Default.NotificationsOff, s, onRun, Modifier.weight(1f))
        }

        // --- Stanje ---
        SectionLabel("Stanje")
        LocationCard(state, now)
        DeviceCard(state, now)
        ConditionsCard(state, onRefreshWeather)
        LastMessageCard(state, now, onOpenHistory)

        Spacer(Modifier.height(16.dp))
    }
}

// =====================================================================
// Glavna kartica
// =====================================================================

private data class BoatStatus(val text: String, val icon: ImageVector, val warn: Boolean)

private fun boatStatus(state: UiState, now: Long): BoatStatus {
    val s = state.settings
    s.fishingSince?.let { return BoatStatus("Na pecanju · ${formatDuration((now - it) / 60_000)}", Icons.Default.Phishing, false) }
    val pos = state.lastGpsPosition?.reply
    val lat = pos?.lat
    val lon = pos?.lon
    if (s.homeLat != null && s.homeLon != null && lat != null && lon != null) {
        val d = TrackerParser.distanceMeters(s.homeLat, s.homeLon, lat, lon)
        return if (d <= s.homeRadiusM) BoatStatus("Na vezu", Icons.Default.CheckCircle, false)
        else BoatStatus("Udaljen ${formatDistance(d)} od veza", Icons.Default.Warning, true)
    }
    return if (state.lastIncoming == null) BoatStatus("Čeka prvi izveštaj", Icons.Default.DirectionsBoat, false)
    else BoatStatus("Tracker aktivan", Icons.Default.DirectionsBoat, false)
}

@Composable
private fun HeroCard(state: UiState, now: Long) {
    val status = boatStatus(state, now)
    val last = state.lastIncoming
    val bat = state.lastBattery?.reply?.battery
    val stale = last != null && now - last.date > 2 * 24 * 60 * 60 * 1000L

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Pine, PineDeep)))
        ) {
            WaterLines(Modifier.align(Alignment.BottomCenter))
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.settings.boatName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(10.dp))
                        StatusPill(
                            status.text, status.icon,
                            container = if (status.warn) Color(0xFFF2C98B) else Color.White.copy(alpha = 0.16f),
                            content = if (status.warn) Color(0xFF3B2600) else Color.White
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    BatteryRing(bat)
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    when {
                        last == null -> "Još nema odgovora od trackera"
                        else -> "Poslednje javljanje: ${formatRelative(last.date, now)} · ${formatDateTime(last.date)}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (stale) Color(0xFFF4A9A3) else Color.White.copy(alpha = 0.85f)
                )
                if (stale) {
                    Text(
                        "Tracker se dugo nije javio — proveri bateriju i kredit na SIM-u.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF4A9A3)
                    )
                }
                state.settings.fishingSince?.let {
                    Text(
                        "Krenuo u ${formatTime(it)} · slanje na svakih ${state.settings.fishInterval} s",
                        style = MaterialTheme.typography.bodySmall,
                        color = Reed
                    )
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

/** Diskretne linije vode na dnu glavne kartice. */
@Composable
private fun WaterLines(modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        listOf(0.35f to 0.10f, 0.75f to 0.06f).forEach { (yFrac, alpha) ->
            val path = Path()
            val y = size.height * yFrac
            val wave = 34.dp.toPx()
            path.moveTo(0f, y)
            var x = 0f
            while (x < size.width) {
                path.relativeQuadraticBezierTo(wave / 4, -6.dp.toPx(), wave / 2, 0f)
                path.relativeQuadraticBezierTo(wave / 4, 6.dp.toPx(), wave / 2, 0f)
                x += wave
            }
            drawPath(
                path, Color.White.copy(alpha = alpha),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun BatteryRing(bat: Int?) {
    val ringColor = when {
        bat == null -> Color.White.copy(alpha = 0.4f)
        bat >= 50 -> Color(0xFFA8E6C1)
        bat >= 25 -> Color(0xFFF2C98B)
        else -> Color(0xFFF4A9A3)
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
        CircularProgressIndicator(
            progress = { (bat ?: 0) / 100f },
            modifier = Modifier.size(92.dp),
            color = ringColor,
            trackColor = Color.White.copy(alpha = 0.15f),
            strokeWidth = 8.dp,
            strokeCap = StrokeCap.Round,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (bat != null) "$bat%" else "—",
                color = Color.White,
                fontSize = 24.sp,
                style = MaterialTheme.typography.titleLarge
            )
            Text("baterija", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
        }
    }
}

// =====================================================================
// Dugmad
// =====================================================================

@Composable
private fun FishingButtons(state: UiState, onRun: (TrackerCommand) -> Unit) {
    val s = state.settings
    val start = CommandCatalog.byId(CommandCatalog.FISH_START)
    val end = CommandCatalog.byId(CommandCatalog.FISH_END)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigActionButton(
            title = "Idem na pecanje",
            subtitle = "slanje na ${s.fishInterval} s",
            icon = Icons.Default.Phishing,
            emphasized = !s.isFishing,
            onClick = { onRun(start) },
            modifier = Modifier.weight(1f)
        )
        BigActionButton(
            title = "Završio sam",
            subtitle = "vraća na ${s.normalInterval} s",
            icon = Icons.Default.DirectionsBoat,
            emphasized = s.isFishing,
            onClick = { onRun(end) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BigActionButton(
    title: String,
    subtitle: String,
    icon: ImageVector,
    emphasized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (emphasized) {
        Button(onClick = onClick, shape = shape, modifier = modifier.heightIn(min = 104.dp)) { content() }
    } else {
        FilledTonalButton(onClick = onClick, shape = shape, modifier = modifier.heightIn(min = 104.dp)) { content() }
    }
}

@Composable
private fun QuickTile(
    cmd: TrackerCommand,
    icon: ImageVector,
    settings: com.camac.tracker.data.Settings,
    onRun: (TrackerCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick = { onRun(cmd) },
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(14.dp)) {
            IconBadge(icon)
            Spacer(Modifier.height(10.dp))
            Text(cmd.label(settings), style = MaterialTheme.typography.titleSmall, maxLines = 2)
        }
    }
}

// =====================================================================
// Kartice stanja
// =====================================================================

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun CardHeader(icon: ImageVector, title: String, subtitle: String?, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
private fun LocationCard(state: UiState, now: Long) {
    val context = LocalContext.current
    val msg = state.lastPosition
    val pos = msg?.reply
    InfoCard {
        if (msg == null || pos?.lat == null || pos.lon == null) {
            CardHeader(Icons.Default.Place, "Lokacija", "Pritisni \"Status lokacije\" da dobiješ poziciju")
            return@InfoCard
        }
        CardHeader(
            Icons.Default.Place,
            if (pos.approximate) "Približna lokacija" else "GPS lokacija",
            "${formatRelative(msg.date, now)} · ${formatDateTime(msg.date)}"
        )
        if (pos.approximate) {
            Text(
                "Tracker nije imao GPS signal, pa je lokacija procenjena preko baznih stanica (greška i do par km).",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        val s = state.settings
        if (s.homeLat != null && s.homeLon != null) {
            val d = TrackerParser.distanceMeters(s.homeLat, s.homeLon, pos.lat, pos.lon)
            val atHome = d <= s.homeRadiusM
            val color = when {
                pos.approximate -> MaterialTheme.colorScheme.onSurfaceVariant
                atHome -> OkGreen
                else -> AlarmRed
            }
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (atHome) Icons.Default.CheckCircle else Icons.Default.Warning, null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        pos.approximate -> "Oko ${formatDistance(d)} od veza (nepouzdano)"
                        atHome -> "Na vezu — ${formatDistance(d)} od obeležene tačke"
                        else -> "Udaljen ${formatDistance(d)} od veza"
                    },
                    color = color,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        pos.speedKmh?.takeIf { it > 1 }?.let {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Speed, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(String.format(Locale.US, "U pokretu: %.0f km/h", it), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { openMap(context, pos.lat, pos.lon, state.settings.boatName) }) {
            Icon(Icons.Default.Map, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Otvori na mapi")
        }
    }
}

@Composable
private fun DeviceCard(state: UiState, now: Long) {
    val msg = state.lastStatus
    val st = msg?.reply
    InfoCard {
        if (msg == null || st == null) {
            CardHeader(Icons.Default.SignalCellularAlt, "Uređaj", "Pritisni \"GSM status\" za signal i podešavanja")
            return@InfoCard
        }
        CardHeader(Icons.Default.SignalCellularAlt, "Uređaj", "Provera ${formatRelative(msg.date, now)}")
        Spacer(Modifier.height(12.dp))
        st.gsmSignal?.let { csq ->
            InfoRow("Mobilna mreža") {
                SignalBars(csq)
                Spacer(Modifier.width(8.dp))
                Text(TrackerReply.gsmLabel(csq), style = MaterialTheme.typography.bodyMedium)
            }
        }
        st.gpsSatellites?.let { sats ->
            InfoRow("GPS sateliti") {
                Text(
                    if (sats == 0) "nema signala" else "$sats vidljivo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sats == 0) WarnAmber else MaterialTheme.colorScheme.onSurface
                )
            }
        }
        st.server?.let { ip ->
            InfoRow("Server") { Text(serverLabel(ip), style = MaterialTheme.typography.bodyMedium) }
        }
        st.apn?.takeIf { it.isNotBlank() }?.let { apn ->
            InfoRow("APN") { Text(apn, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun serverLabel(ip: String): String = when {
    ip.contains("flespi", true) || ip.startsWith("185.213.2.") -> "Ruhavik"
    ip.contains("tkstar", true) || ip.startsWith("47.88.85.196") -> "TKSTAR"
    else -> ip
}

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        value()
    }
}

@Composable
private fun SignalBars(csq: Int) {
    val level = when {
        csq <= 0 || csq == 99 -> 0
        csq < 10 -> 1
        csq < 15 -> 2
        csq < 20 -> 3
        else -> 4
    }
    val on = if (level <= 1) WarnAmber else MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (1..4).forEach { i ->
            Box(
                Modifier
                    .width(5.dp)
                    .height((5 + i * 4).dp)
                    .background(if (i <= level) on else off, RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun ConditionsCard(state: UiState, onRefresh: () -> Unit) {
    val w = state.weather
    val moon = WeatherRepo.moonPhase()
    InfoCard {
        CardHeader(
            Icons.Default.WbTwilight, "Uslovi na vodi",
            when {
                state.weatherLoading -> "Učitavam prognozu…"
                w != null -> "Ažurirano u ${formatTime(w.fetchedAt)}"
                state.weatherError != null -> state.weatherError
                else -> "Treba bar jedna poznata lokacija čamca"
            }
        ) {
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Osveži prognozu") }
        }
        if (w != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Thermostat, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text(String.format(Locale.US, "%.0f°C", w.tempC), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(12.dp))
                Text(w.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            InfoRow("Vetar") {
                Icon(Icons.Default.Air, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    String.format(Locale.US, "%.0f km/h %s · udari %.0f", w.windKmh, w.windDir, w.gustKmh),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            InfoRow("Pritisak") { Text(w.pressureLabel, style = MaterialTheme.typography.bodyMedium) }
            if (w.sunrise != null && w.sunset != null) {
                InfoRow("Izlazak / zalazak") { Text("${w.sunrise} / ${w.sunset}", style = MaterialTheme.typography.bodyMedium) }
            }
        }
        InfoRow("Mesec") {
            Icon(Icons.Default.NightsStay, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("${moon.name} · ${moon.illumination}%", style = MaterialTheme.typography.bodyMedium)
        }
        if (w != null) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text(
                w.fishingHint,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun LastMessageCard(state: UiState, now: Long, onOpenHistory: () -> Unit) {
    val last = state.lastIncoming ?: return
    InfoCard {
        Text(
            "Poslednja poruka · ${formatRelative(last.date, now)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        SelectionContainer {
            Text(last.body, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onOpenHistory, modifier = Modifier.align(Alignment.End)) { Text("Cela istorija") }
    }
}

// =====================================================================
// Baneri
// =====================================================================

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(24.dp)) {
            Icon(Icons.Default.Phishing, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("Dobrodošao!", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text("Za početak upiši broj trackera, admin broj i PIN u podešavanjima.")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSettings) { Text("Otvori podešavanja") }
        }
    }
}

@Composable
private fun UpdateBanner(version: String, progress: Float?, onInstall: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SystemUpdate, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nova verzija $version", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (progress != null) "Preuzimam… ${(progress * 100).toInt()}%" else "Dodirni da ažuriraš aplikaciju",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (progress == null) Button(onClick = onInstall) { Text("Ažuriraj") }
            }
            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AlarmCard(body: String, date: Long, now: Long) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AlarmRed, contentColor = Color.White)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, Modifier.size(30.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Alarm — ${formatRelative(date, now)}", style = MaterialTheme.typography.titleMedium)
                Text(body, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PendingCard(p: PendingCommand, now: Long, onDismiss: () -> Unit) {
    val waitingMin = (now - p.sentAt) / 60_000
    val failed = p.status == SendStatus.FAILED
    val (title, subtitle) = when {
        failed -> "SMS nije poslat" to "Proveri signal i kredit na telefonu, pa probaj ponovo."
        p.status == SendStatus.SENDING -> "Šaljem…" to p.text
        waitingMin >= 3 -> "Još nema odgovora" to
            "Tracker možda nema signal, SIM nema kredit ili je baterija prazna."
        else -> "Poslato, čekam odgovor…" to p.text
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (failed) Icon(Icons.Default.ErrorOutline, null)
            else CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${p.label}: $title", style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Sakrij") }
        }
    }
}
