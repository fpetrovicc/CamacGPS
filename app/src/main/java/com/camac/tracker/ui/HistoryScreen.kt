package com.camac.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.camac.tracker.data.FishingSession
import com.camac.tracker.data.ReplyKind
import com.camac.tracker.data.SmsMessage

private enum class HistoryFilter(val label: String) {
    ALL("Sve"), REPLIES("Odgovori"), ALARMS("Alarmi"), SENT("Poslato"), SESSIONS("Pecanja")
}

@Composable
fun HistoryScreen(
    state: UiState,
    onShare: (gpx: Boolean) -> Unit,
    onDeleteSession: (FishingSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }

    val filtered = state.messages.filter {
        when (filter) {
            HistoryFilter.ALL, HistoryFilter.SESSIONS -> true
            HistoryFilter.REPLIES -> it.incoming
            HistoryFilter.ALARMS -> it.reply?.kind == ReplyKind.ALARM
            HistoryFilter.SENT -> !it.incoming
        }
    }
    val batteryPoints = state.messages
        .mapNotNull { m -> m.reply?.battery?.let { m.date to it } }
        .take(40)
        .reversed()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { ExportCard(state, onShare) }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(HistoryFilter.entries) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }

        if (filter == HistoryFilter.SESSIONS) {
            if (state.sessions.isEmpty()) {
                item {
                    Text(
                        "Još nema zabeleženih pecanja. Pecanje se beleži kad pritisneš \"Idem na pecanje\", " +
                            "a završava sa \"Završio sam\".",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                item { SessionsSummary(state.sessions) }
                items(state.sessions, key = { "s-${it.start}" }) { SessionRow(it, onDeleteSession) }
            }
            return@LazyColumn
        }

        if (batteryPoints.size >= 2 && filter != HistoryFilter.SENT) {
            item { BatteryChart(batteryPoints) }
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    if (!state.isConfigured) "Prvo podesi broj trackera u Podešavanjima." else "Nema poruka.",
                    modifier = Modifier.padding(24.dp)
                )
            }
        }

        var lastDay = ""
        filtered.forEach { msg ->
            val day = formatDay(msg.date)
            if (day != lastDay) {
                lastDay = day
                item(key = "day-${msg.id}") {
                    Text(
                        day,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp)
                    )
                }
            }
            item(key = msg.id) { MessageBubble(msg) }
        }
    }
}

@Composable
private fun ExportCard(state: UiState, onShare: (Boolean) -> Unit) {
    val s = state.settings
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Default.CloudUpload)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Izvoz na Google Drive", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !s.weeklyExport -> "Nedeljni izvoz je isključen (Podešavanja)"
                            s.lastExportAt > 0 -> "Svakih 7 dana · poslednji ${formatDateTime(s.lastExportAt)}"
                            else -> "Svakih 7 dana · još nije bilo izvoza"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onShare(false) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.TableChart, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Poruke")
                }
                OutlinedButton(onClick = { onShare(true) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Timeline, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pozicije")
                }
            }
            Text(
                "Poruke = tabela (CSV) za Excel/Sheets. Pozicije = GPX za Google Earth i mape.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun SessionsSummary(sessions: List<FishingSession>) {
    val totalMin = sessions.sumOf { it.durationMin }
    val monthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
    val lastMonth = sessions.count { it.start >= monthAgo }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat("${sessions.size}", "pecanja", Modifier.weight(1f))
            Stat("$lastMonth", "u 30 dana", Modifier.weight(1f))
            Stat(formatDuration(totalMin), "na vodi", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun SessionRow(session: FishingSession, onDelete: (FishingSession) -> Unit) {
    ElevatedCard(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.Phishing)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(formatDay(session.start), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatTime(session.start)} – ${formatTime(session.end)} · ${formatDuration(session.durationMin)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onDelete(session) }) { Icon(Icons.Default.DeleteOutline, "Obriši") }
        }
    }
}

@Composable
private fun MessageBubble(msg: SmsMessage) {
    val context = LocalContext.current
    val reply = msg.reply
    val isAlarm = reply?.kind == ReplyKind.ALARM
    val bg = when {
        msg.failed || isAlarm -> MaterialTheme.colorScheme.errorContainer
        msg.incoming -> MaterialTheme.colorScheme.surfaceContainerLowest
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = if (msg.incoming) Alignment.CenterStart else Alignment.CenterEnd) {
        Surface(
            color = bg,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (msg.incoming) 4.dp else 18.dp,
                bottomEnd = if (msg.incoming) 18.dp else 4.dp
            ),
            tonalElevation = if (msg.incoming) 1.dp else 0.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    buildString {
                        append(if (msg.incoming) "Tracker" else "Poslato")
                        append(" · ").append(formatTime(msg.date))
                        if (msg.failed) append(" · nije poslato")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                SelectionContainer {
                    Text(msg.body, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                }
                if (reply != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            reply.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAlarm) AlarmRed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val lat = reply.lat
                        val lon = reply.lon
                        if (lat != null && lon != null) {
                            IconButton(onClick = { openMap(context, lat, lon) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Map, "Otvori na mapi", Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryChart(points: List<Pair<Long, Int>>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Baterija kroz vreme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${points.last().second}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = batteryColor(points.last().second)
                )
            }
            Spacer(Modifier.height(12.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                val t0 = points.first().first.toFloat()
                val span = (points.last().first - points.first().first).toFloat().coerceAtLeast(1f)
                fun x(t: Long) = (t - t0) / span * size.width
                fun y(p: Int) = size.height - p / 100f * size.height

                listOf(25, 50, 75).forEach { lvl ->
                    drawLine(
                        if (lvl == 25) AlarmRed.copy(alpha = 0.35f) else gridColor,
                        Offset(0f, y(lvl)), Offset(size.width, y(lvl)), strokeWidth = 1.dp.toPx()
                    )
                }
                val line = Path()
                points.forEachIndexed { i, (t, p) -> if (i == 0) line.moveTo(x(t), y(p)) else line.lineTo(x(t), y(p)) }
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(points.last().first), size.height)
                    lineTo(x(points.first().first), size.height)
                    close()
                }
                drawPath(area, fillColor)
                drawPath(line, lineColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                points.forEach { (t, p) ->
                    drawCircle(if (p < 25) AlarmRed else lineColor, 3.5.dp.toPx(), Offset(x(t), y(p)))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDateTime(points.first().first), style = MaterialTheme.typography.labelSmall)
                Text(formatDateTime(points.last().first), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
