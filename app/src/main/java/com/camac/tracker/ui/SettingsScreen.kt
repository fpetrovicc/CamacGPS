package com.camac.tracker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.camac.tracker.data.Settings
import com.camac.tracker.update.UpdateManager
import java.util.Locale

@Composable
fun SettingsScreen(
    state: UiState,
    onUpdate: ((Settings) -> Settings) -> Unit,
    onSetHomeFromLast: () -> Unit,
    onRequestPermissions: () -> Unit,
    onExportTarget: (Uri) -> Unit,
    onExportNow: () -> Unit,
    onCheckUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = state.settings
    val context = LocalContext.current
    var manualHome by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) onExportTarget(uri)
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---------- Tracker ----------
        Section("Čamac i tracker") {
            TextSetting("Naziv čamca", s.boatName, KeyboardType.Text) { v -> onUpdate { it.copy(boatName = v) } }
            TextSetting(
                "Broj trackera", s.trackerNumber, KeyboardType.Phone,
                supporting = "Broj SIM kartice koja je u trackeru"
            ) { v -> onUpdate { it.copy(trackerNumber = v.trim()) } }
            TextSetting(
                "Admin broj", s.adminNumber, KeyboardType.Phone,
                supporting = "Tvoj broj — on dobija alarme i šalje komande"
            ) { v -> onUpdate { it.copy(adminNumber = v.trim()) } }
            PinSetting(s.pin) { v -> onUpdate { it.copy(pin = v.trim()) } }
            if (s.pendingPin != null) {
                Text(
                    "Čeka se potvrda trackera za novi PIN (${s.pendingPin}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarnAmber
                )
            }
        }

        // ---------- Pecanje ----------
        Section("Pecanje i alarmi") {
            NumberSetting(
                "Slanje dok se peca (s)", s.fishInterval, 10..300,
                "Za \"Idem na pecanje\". Manje = detaljnija ruta, više troši bateriju."
            ) { v -> onUpdate { it.copy(fishInterval = v) } }
            NumberSetting(
                "Slanje na vezu (s)", s.normalInterval, 10..300,
                "Za \"Završio sam\". 300 je najređe što tracker podržava."
            ) { v -> onUpdate { it.copy(normalInterval = v) } }
            NumberSetting(
                "Radijus alarma pomeranja (m)", s.moveRadius, 50..9999,
                "Za \"Alarm pomeranja\". Na vodi GPS \"šeta\", zato ne ispod 100 m."
            ) { v -> onUpdate { it.copy(moveRadius = v) } }
        }

        // ---------- Vez ----------
        Section("Vez") {
            Text(
                if (s.hasHome) String.format(Locale.US, "%.5f, %.5f", s.homeLat, s.homeLon)
                else "Nije obeležen. Kad je čamac na vezu, pošalji \"Status lokacije\" pa pritisni dugme ispod.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onSetHomeFromLast, modifier = Modifier.fillMaxWidth()) {
                Text("Obeleži vez na poslednjoj GPS poziciji")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { manualHome = true }) { Text("Unesi ručno") }
                if (s.hasHome) {
                    TextButton(onClick = { onUpdate { it.copy(homeLat = null, homeLon = null) } }) { Text("Obriši") }
                }
            }
            NumberSetting(
                "Dozvoljeno odstupanje (m)", s.homeRadiusM, 20..5000,
                "Do ove udaljenosti čamac se smatra \"na vezu\"."
            ) { v -> onUpdate { it.copy(homeRadiusM = v) } }
        }

        // ---------- Izvoz ----------
        Section("Izvoz istorije") {
            SwitchRow("Nedeljni izvoz (svakih 7 dana)", s.weeklyExport) { v -> onUpdate { it.copy(weeklyExport = v) } }
            Text(
                if (s.exportUri != null) "Upisuje se u izabrani fajl" + (if (s.lastExportAt > 0) " · poslednji ${formatDateTime(s.lastExportAt)}" else "")
                else "Fajl nije izabran — jednom nedeljno stiže notifikacija, dodirneš je i izabereš Google Drive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = { pickFile.launch("camac_istorija.csv") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (s.exportUri == null) "Izaberi fajl na Google Drive-u" else "Promeni fajl")
            }
            if (s.exportUri != null) {
                OutlinedButton(onClick = onExportNow, modifier = Modifier.fillMaxWidth()) { Text("Izvezi odmah") }
            }
            Text(
                "U prozoru za čuvanje izaberi Google Drive (levi meni ☰) i folder. Aplikacija posle sama " +
                    "prepisuje taj fajl svake nedelje.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---------- Obaveštenja ----------
        Section("Obaveštenja") {
            SwitchRow("Notifikacija kad tracker odgovori ili pošalje alarm", s.notifications) { v ->
                onUpdate { it.copy(notifications = v) }
            }
        }

        // ---------- Ažuriranja ----------
        Section("Ažuriranja") {
            Text("Verzija ${UpdateManager.currentVersion}", style = MaterialTheme.typography.bodyLarge)
            if (UpdateManager.enabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onCheckUpdate, enabled = !state.updateChecking) { Text("Proveri ažuriranje") }
                    if (state.updateChecking) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                state.update?.let { Text("Dostupna je verzija ${it.version} — vidi početni ekran.", color = MaterialTheme.colorScheme.primary) }
            } else {
                Text(
                    if (com.camac.tracker.BuildConfig.DEBUG) "Test verzija se ne ažurira sama — instaliraj pravu verziju sa GitHub Releases."
                    else "Automatska provera nije podešena.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ---------- Dozvole ----------
        Section("Dozvole") {
            val perms = buildList {
                add(Manifest.permission.SEND_SMS to "Slanje SMS-a")
                add(Manifest.permission.READ_SMS to "Čitanje SMS-a (istorija)")
                add(Manifest.permission.RECEIVE_SMS to "Prijem SMS-a (obaveštenja)")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS to "Notifikacije")
                }
            }
            perms.forEach { (perm, label) ->
                val ok = perm !in state.missingPermissions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (ok) Icons.Default.CheckCircle else Icons.Default.Cancel, null,
                        tint = if (ok) OkGreen else AlarmRed, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.missingPermissions.isNotEmpty()) {
                Button(onClick = onRequestPermissions) { Text("Dozvoli") }
                Text(
                    "Ako je dozvola siva (\"Ograničeno podešavanje\"): Podešavanja aplikacije → ⋮ → " +
                        "Dozvoli ograničena podešavanja.",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                    )
                }) { Text("Otvori podešavanja aplikacije") }
            }
        }

        Section("O aplikaciji") {
            Text(
                "Svako dugme šalje običan SMS trackeru sa ovog telefona. Odgovori stižu u Poruke, " +
                    "a aplikacija ih čita i prikazuje sređeno. Prognoza: Open-Meteo. Font: Manrope (OFL).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (manualHome) {
        ManualHomeDialog(
            initialLat = s.homeLat,
            initialLon = s.homeLon,
            onDismiss = { manualHome = false },
            onSave = { lat, lon ->
                onUpdate { it.copy(homeLat = lat, homeLon = lon) }
                manualHome = false
            }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/** Tekstualno polje sa lokalnim stanjem (da kursor ne skače dok se kuca). */
@Composable
private fun TextSetting(
    label: String,
    initial: String,
    keyboard: KeyboardType,
    supporting: String? = null,
    onChange: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(label) },
        supportingText = if (supporting != null) { { Text(supporting) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PinSetting(initial: String, onChange: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var visible by remember { mutableStateOf(false) }
    // Ako aplikacija sama promeni PIN (potvrda trackera), osveži polje
    LaunchedEffect(initial) { if (initial != text) text = initial }
    OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v.filter { it.isDigit() }.take(8); onChange(text) },
        label = { Text("PIN trackera") },
        supportingText = { Text("Fabrički je 123456") },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Prikaži PIN")
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NumberSetting(
    label: String,
    value: Int,
    range: IntRange,
    supporting: String,
    onChange: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in range
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v.filter { it.isDigit() }.take(5)
            text.toIntOrNull()?.takeIf { it in range }?.let(onChange)
        },
        label = { Text(label) },
        supportingText = { Text(if (valid) supporting else "Dozvoljeno ${range.first}–${range.last}") },
        isError = !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ManualHomeDialog(
    initialLat: Double?,
    initialLon: Double?,
    onDismiss: () -> Unit,
    onSave: (Double, Double) -> Unit,
) {
    var lat by remember { mutableStateOf(initialLat?.toString() ?: "") }
    var lon by remember { mutableStateOf(initialLon?.toString() ?: "") }
    val latV = lat.replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lonV = lon.replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pozicija veza") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("U Google mapama dugo pritisni mesto i prepiši brojeve.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    lat, { lat = it }, label = { Text("Širina (npr. 44.8615)") }, singleLine = true,
                    isError = lat.isNotBlank() && latV == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    lon, { lon = it }, label = { Text("Dužina (npr. 19.7242)") }, singleLine = true,
                    isError = lon.isNotBlank() && lonV == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(enabled = latV != null && lonV != null, onClick = { onSave(latV!!, lonV!!) }) { Text("Sačuvaj") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Odustani") } }
    )
}
