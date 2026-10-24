package com.camac.tracker.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.camac.tracker.data.InputKind
import com.camac.tracker.data.Settings
import com.camac.tracker.data.TrackerCommand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ---------- Formatiranje ----------

/** Srpski, latinica (inače Android za "sr" piše mesece ćirilicom). */
private val SR: Locale = Locale.Builder().setLanguage("sr").setScript("Latn").setRegion("RS").build()

fun formatDateTime(ms: Long): String = SimpleDateFormat("dd.MM. HH:mm", SR).format(Date(ms))
fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", SR).format(Date(ms))
fun formatDay(ms: Long): String =
    SimpleDateFormat("EEEE, d. MMMM", SR).format(Date(ms)).replaceFirstChar { it.uppercase() }

fun formatRelative(ms: Long, now: Long = System.currentTimeMillis()): String {
    val min = (now - ms) / 60_000
    return when {
        min < 1 -> "upravo"
        min < 60 -> "pre $min min"
        min < 24 * 60 -> "pre ${min / 60} h"
        min < 2 * 24 * 60 -> "juče"
        else -> "pre ${min / (24 * 60)} dana"
    }
}

fun formatDuration(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

fun formatDistance(m: Double): String =
    if (m < 1000) "${m.roundToInt()} m" else String.format(SR, "%.1f km", m / 1000)

fun batteryColor(pct: Int): Color = when {
    pct >= 50 -> OkGreen
    pct >= 25 -> WarnAmber
    else -> AlarmRed
}

fun openMap(context: Context, lat: Double, lon: Double, label: String = "Čamac") {
    val geo = Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(label)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$lat,$lon")))
    }
}

// ---------- Male komponente ----------

/** Naslov sekcije iznad kartica. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
    )
}

/** Mala "pilula" sa ikonicom — npr. "Na vezu", "Na pecanju 2 h". */
@Composable
fun StatusPill(text: String, icon: ImageVector, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Ikonica u zaobljenom kvadratu, za kartice. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier
            .size(40.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** SMS tekst u "čipu" sa monospace fontom. */
@Composable
fun SmsChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun PermissionCard(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null)
                Spacer(Modifier.width(8.dp))
                Text("Potrebne su dozvole", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Aplikacija šalje komande i čita odgovore trackera preko SMS-a.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequest) { Text("Dozvoli") }
        }
    }
}

// ---------- Pokretanje komande (potvrda / unos) ----------

@Composable
fun rememberCommandRunner(
    settings: Settings,
    onSend: (TrackerCommand, String) -> Unit,
): (TrackerCommand) -> Unit {
    var target by remember { mutableStateOf<TrackerCommand?>(null) }

    target?.let { cmd ->
        SendDialog(
            cmd = cmd,
            settings = settings,
            onDismiss = { target = null },
            onConfirm = { input ->
                onSend(cmd, input)
                target = null
            }
        )
    }

    return { cmd ->
        if (cmd.input != InputKind.NONE || cmd.confirm) target = cmd else onSend(cmd, "")
    }
}

@Composable
private fun SendDialog(
    cmd: TrackerCommand,
    settings: Settings,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val preview = cmd.resolve(settings, input)
    val canSend = cmd.input == InputKind.NONE || input.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(cmd.label(settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(cmd.describe(settings), style = MaterialTheme.typography.bodyMedium)
                if (cmd.input != InputKind.NONE) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(cmd.inputHint) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = when (cmd.input) {
                                InputKind.NUMBER -> KeyboardType.Number
                                InputKind.PHONE -> KeyboardType.Phone
                                else -> KeyboardType.Text
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column {
                    Text("Šalje se SMS:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    SmsChip(preview.ifBlank { "—" })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(input) },
                enabled = canSend,
                colors = if (cmd.danger) ButtonDefaults.buttonColors(containerColor = AlarmRed)
                else ButtonDefaults.buttonColors()
            ) { Text("Pošalji") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani") } }
    )
}
