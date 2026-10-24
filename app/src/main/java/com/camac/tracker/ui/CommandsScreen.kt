package com.camac.tracker.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.camac.tracker.data.CommandCatalog
import com.camac.tracker.data.CommandCategory
import com.camac.tracker.data.Settings
import com.camac.tracker.data.TrackerCommand

private fun CommandCategory.icon(): ImageVector = when (this) {
    CommandCategory.STATUS -> Icons.Default.MyLocation
    CommandCategory.FISHING -> Icons.Default.Phishing
    CommandCategory.ALARMS -> Icons.Default.NotificationsActive
    CommandCategory.BATTERY -> Icons.Default.BatteryChargingFull
    CommandCategory.ADMIN -> Icons.Default.AdminPanelSettings
    CommandCategory.NETWORK -> Icons.Default.Dns
    CommandCategory.ADVANCED -> Icons.Default.Build
}

@Composable
fun CommandsScreen(
    state: UiState,
    onRun: (TrackerCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val s = state.settings
    val q = query.trim().lowercase()
    val filtered = CommandCatalog.all.filter {
        q.isEmpty() ||
            it.label(s).lowercase().contains(q) ||
            it.describe(s).lowercase().contains(q) ||
            it.resolve(s).lowercase().contains(q)
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Pretraži komande") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Clear, "Obriši") }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text(
                "PIN, admin broj i intervali se uzimaju iz Podešavanja — kad ih promeniš, komande se same ažuriraju.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        CommandCategory.entries.forEach { cat ->
            val cmds = filtered.filter { it.category == cat }
            if (cmds.isEmpty()) return@forEach
            item(key = "cat-${cat.name}") {
                Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(cat.icon(), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(cat.title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            items(cmds, key = { it.id }) { cmd -> CommandCard(cmd, s, onRun) }
        }

        if (filtered.isEmpty()) {
            item { Text("Nema komande za \"$query\".", modifier = Modifier.padding(16.dp)) }
        }
    }
}

@Composable
private fun CommandCard(cmd: TrackerCommand, s: Settings, onRun: (TrackerCommand) -> Unit) {
    val missing = cmd.missing(s)
    ElevatedCard(
        onClick = { onRun(cmd) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cmd.danger) {
                        Icon(Icons.Default.Warning, null, tint = AlarmRed, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        cmd.label(s),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (cmd.danger) AlarmRed else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                SmsChip(cmd.resolve(s, if (cmd.input != com.camac.tracker.data.InputKind.NONE) "…" else ""))
                Spacer(Modifier.height(8.dp))
                Text(
                    cmd.describe(s),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (missing != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(missing, style = MaterialTheme.typography.bodySmall, color = WarnAmber)
                }
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalIconButton(
                onClick = { onRun(cmd) },
                colors = if (cmd.danger) IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = AlarmRed
                ) else IconButtonDefaults.filledTonalIconButtonColors()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Pošalji")
            }
        }
    }
}
