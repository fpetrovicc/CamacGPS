package com.camac.tracker.ui

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.camac.tracker.data.CommandCatalog
import com.camac.tracker.data.Exporter
import com.camac.tracker.data.FishingSession
import com.camac.tracker.data.Prefs
import com.camac.tracker.data.ReplyKind
import com.camac.tracker.data.Settings
import com.camac.tracker.data.SmsMessage
import com.camac.tracker.data.SmsRepository
import com.camac.tracker.data.TrackerCommand
import com.camac.tracker.data.Weather
import com.camac.tracker.data.WeatherRepo
import com.camac.tracker.sms.Notifier
import com.camac.tracker.sms.SmsSender
import com.camac.tracker.update.UpdateInfo
import com.camac.tracker.update.UpdateManager
import com.camac.tracker.work.ExportWorker
import com.camac.tracker.work.SilenceWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SendStatus { SENDING, SENT, FAILED }

data class PendingCommand(
    val label: String,
    val text: String,
    val sentAt: Long,
    val status: SendStatus,
)

data class UiState(
    val settings: Settings = Settings(),
    val sessions: List<FishingSession> = emptyList(),
    val messages: List<SmsMessage> = emptyList(), // najnovije prvo
    val missingPermissions: List<String> = emptyList(),
    val pending: PendingCommand? = null,
    val snackbar: String? = null,
    val loading: Boolean = true,
    // vreme na vodi
    val weather: Weather? = null,
    val weatherLoading: Boolean = false,
    val weatherError: String? = null,
    // ažuriranja
    val update: UpdateInfo? = null,
    val updateProgress: Float? = null,
    val updateChecking: Boolean = false,
    val needsInstallPermission: Boolean = false,
) {
    val isConfigured: Boolean get() = settings.trackerNumber.isNotBlank()
    val lastIncoming: SmsMessage? get() = messages.firstOrNull { it.incoming }
    val lastPosition: SmsMessage? get() = messages.firstOrNull { it.reply?.hasPosition == true }
    val lastGpsPosition: SmsMessage?
        get() = messages.firstOrNull { it.reply?.hasPosition == true && it.reply?.approximate == false }
    val lastBattery: SmsMessage? get() = messages.firstOrNull { it.reply?.battery != null }
    val lastStatus: SmsMessage? get() = messages.firstOrNull { it.reply?.kind == ReplyKind.STATUS }

    /** Alarm iz poslednja 24 sata. */
    val recentAlarm: SmsMessage?
        get() = messages.firstOrNull {
            it.reply?.kind == ReplyKind.ALARM &&
                System.currentTimeMillis() - it.date < 24 * 60 * 60 * 1000L
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(
        UiState(settings = prefs.loadSettings(), sessions = prefs.loadSessions())
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val ctx: Context get() = getApplication()

    private val smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    private val sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val ok = resultCode == Activity.RESULT_OK
            _state.update { s ->
                val p = s.pending ?: return@update s
                s.copy(
                    pending = p.copy(status = if (ok) SendStatus.SENT else SendStatus.FAILED),
                    snackbar = if (ok) null else "SMS nije poslat — proveri signal i kredit"
                )
            }
        }
    }

    init {
        ctx.contentResolver.registerContentObserver(Uri.parse("content://sms"), true, smsObserver)
        ContextCompat.registerReceiver(
            ctx, sentReceiver, IntentFilter(SmsSender.ACTION_SENT), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ExportWorker.schedule(ctx, _state.value.settings.weeklyExport)
        SilenceWorker.schedule(ctx)
        refresh()
        checkForUpdate(manual = false)
    }

    override fun onCleared() {
        ctx.contentResolver.unregisterContentObserver(smsObserver)
        ctx.unregisterReceiver(sentReceiver)
    }

    fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun refresh() {
        // Podešavanja menjaju i pozadinski poslovi / dugmad iz notifikacija → uvek čitaj sveže
        _state.update { it.copy(settings = prefs.loadSettings()) }
        viewModelScope.launch {
            val number = _state.value.settings.trackerNumber
            val messages = withContext(Dispatchers.IO) { SmsRepository.loadConversation(ctx, number) }
            val missing = requiredPermissions().filter {
                ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
            }
            _state.update { s ->
                val answered = s.pending != null &&
                    messages.any { it.incoming && it.date >= s.pending.sentAt - 5_000 }
                s.copy(
                    messages = messages,
                    missingPermissions = missing,
                    pending = if (answered) null else s.pending,
                    loading = false,
                )
            }
            applyPinIfConfirmed(messages)
            loadWeather()
        }
    }

    /** Tracker je odgovorio "password ok" → novi PIN postaje važeći u aplikaciji. */
    private fun applyPinIfConfirmed(messages: List<SmsMessage>) {
        val newPin = _state.value.settings.pendingPin ?: return
        val last = messages.firstOrNull { it.incoming } ?: return
        if (last.reply?.kind == ReplyKind.ACK && last.body.contains("password", ignoreCase = true)) {
            updateSettings { it.copy(pin = newPin, pendingPin = null) }
            _state.update { it.copy(snackbar = "Tracker je potvrdio novi PIN — sačuvan u podešavanjima") }
        }
    }

    // ---------- Slanje komandi ----------

    fun send(cmd: TrackerCommand, input: String = "") {
        val s = _state.value.settings
        cmd.missing(s)?.let { msg ->
            _state.update { it.copy(snackbar = msg) }
            return
        }
        val text = cmd.resolve(s, input)
        if (text.isBlank()) {
            _state.update { it.copy(snackbar = "Komanda je prazna") }
            return
        }
        val error = SmsSender.send(ctx, s.trackerNumber, text)
        if (error != null) {
            _state.update { it.copy(snackbar = error) }
            return
        }
        _state.update {
            it.copy(pending = PendingCommand(cmd.label(s), text, System.currentTimeMillis(), SendStatus.SENDING))
        }
        afterSend(cmd, input)
    }

    /** Pamti početak/kraj pecanja i novi PIN. */
    private fun afterSend(cmd: TrackerCommand, input: String) {
        val now = System.currentTimeMillis()
        when (cmd.id) {
            CommandCatalog.FISH_START -> if (!_state.value.settings.isFishing) {
                updateSettings { it.copy(fishingSince = now) }
            }
            CommandCatalog.FISH_END -> {
                val start = _state.value.settings.fishingSince
                if (start != null) {
                    val sessions = listOf(FishingSession(start, now)) + _state.value.sessions
                    prefs.saveSessions(sessions)
                    _state.update { it.copy(sessions = sessions) }
                }
                updateSettings { it.copy(fishingSince = null) }
            }
            CommandCatalog.PASSWORD -> updateSettings { it.copy(pendingPin = input.trim()) }
        }
    }

    fun dismissPending() = _state.update { it.copy(pending = null) }

    // ---------- "Tracker se dugo nije javio" (bez automatskog SMS-a) ----------

    /** "U redu" — sakrij upozorenje; javiće se ponovo posle istog broja dana bez poruka. */
    fun dismissSilence() {
        updateSettings { it.copy(staleDismissedAt = System.currentTimeMillis()) }
        Notifier.cancelSilence(ctx)
    }

    /** "Pošalji proveru" — jedan SMS (Status lokacije) na zahtev korisnika, pa sakrij upozorenje. */
    fun checkSilence() {
        send(CommandCatalog.byId(CommandCatalog.LOCATION))
        dismissSilence()
    }
    fun consumeSnackbar() = _state.update { it.copy(snackbar = null) }
    fun showMessage(text: String) = _state.update { it.copy(snackbar = text) }

    // ---------- Podešavanja ----------

    fun updateSettings(transform: (Settings) -> Settings) {
        val old = _state.value.settings
        val new = transform(old)
        prefs.saveSettings(new)
        _state.update { it.copy(settings = new) }
        if (new.trackerNumber != old.trackerNumber) refresh()
        if (new.weeklyExport != old.weeklyExport) ExportWorker.schedule(ctx, new.weeklyExport)
    }

    fun setHomeFromLastPosition() {
        val msg = _state.value.lastGpsPosition
        val lat = msg?.reply?.lat
        val lon = msg?.reply?.lon
        if (msg == null || lat == null || lon == null) {
            showMessage("Nema GPS pozicije — pošalji \"Status lokacije\" dok je čamac na vezu")
            return
        }
        updateSettings { it.copy(homeLat = lat, homeLon = lon) }
        showMessage("Vez postavljen na poziciju od ${formatDateTime(msg.date)}")
    }

    fun deleteSession(session: FishingSession) {
        val sessions = _state.value.sessions - session
        prefs.saveSessions(sessions)
        _state.update { it.copy(sessions = sessions) }
    }

    // ---------- Vreme na vodi ----------

    fun loadWeather(force: Boolean = false) {
        val s = _state.value
        val w = s.weather
        if (!force && (s.weatherLoading || (w != null && System.currentTimeMillis() - w.fetchedAt < 30 * 60_000))) return
        val pos = s.lastPosition?.reply
        val lat = pos?.lat ?: s.settings.homeLat ?: return
        val lon = pos?.lon ?: s.settings.homeLon ?: return
        _state.update { it.copy(weatherLoading = true, weatherError = null) }
        viewModelScope.launch {
            try {
                val weather = WeatherRepo.fetch(lat, lon)
                _state.update { it.copy(weather = weather, weatherLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(weatherLoading = false, weatherError = "Nema interneta za prognozu") }
            }
        }
    }

    // ---------- Izvoz ----------

    /** Korisnik je izabrao fajl (npr. na Google Drive-u) za nedeljni izvoz. */
    fun setExportTarget(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Neki provajderi ne daju trajnu dozvolu — tada ostaje podsetnik notifikacijom
        }
        updateSettings { it.copy(exportUri = uri.toString(), weeklyExport = true) }
        exportToTargetNow()
    }

    fun exportToTargetNow() {
        val s = _state.value.settings
        val uri = s.exportUri ?: return showMessage("Prvo izaberi fajl za izvoz")
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val all = SmsRepository.loadConversation(ctx, s.trackerNumber, limit = 10_000)
                Exporter.writeToUri(ctx, Uri.parse(uri), Exporter.csv(all))
            }
            if (ok) {
                updateSettings { it.copy(lastExportAt = System.currentTimeMillis()) }
                showMessage("Istorija sačuvana u izabrani fajl")
            } else {
                showMessage("Upis nije uspeo — izaberi fajl ponovo ili koristi \"Podeli\"")
            }
        }
    }

    /** Pravi fajl za deljenje (Drive, mejl…). */
    suspend fun buildShareIntent(gpx: Boolean): Intent = withContext(Dispatchers.IO) {
        val s = _state.value.settings
        val all = SmsRepository.loadConversation(ctx, s.trackerNumber, limit = 10_000)
        if (gpx) {
            val uri = Exporter.writeShareFile(ctx, Exporter.fileName("pozicije", "gpx"), Exporter.gpx(all, s.boatName))
            Exporter.shareIntent(uri, "application/gpx+xml", "Pozicije čamca")
        } else {
            val uri = Exporter.writeShareFile(ctx, Exporter.fileName("istorija", "csv"), Exporter.csv(all))
            Exporter.shareIntent(uri, "text/csv", "Istorija čamca")
        }
    }

    // ---------- Ažuriranja ----------

    fun checkForUpdate(manual: Boolean) {
        if (!UpdateManager.enabled) {
            if (manual) showMessage("Provera ažuriranja još nije podešena")
            return
        }
        if (!manual && System.currentTimeMillis() - prefs.lastUpdateCheck < 12 * 60 * 60 * 1000L) return
        _state.update { it.copy(updateChecking = true) }
        viewModelScope.launch {
            try {
                val info = UpdateManager.check()
                prefs.lastUpdateCheck = System.currentTimeMillis()
                _state.update { it.copy(update = info, updateChecking = false) }
                if (info != null && !manual) Notifier.showUpdateAvailable(ctx, info.version)
                if (manual && info == null) showMessage("Imaš najnoviju verziju (${UpdateManager.currentVersion})")
            } catch (e: Exception) {
                _state.update { it.copy(updateChecking = false) }
                if (manual) showMessage("Provera nije uspela — proveri internet")
            }
        }
    }

    fun installUpdate() {
        val info = _state.value.update ?: return
        if (!UpdateManager.canInstall(ctx)) {
            _state.update { it.copy(needsInstallPermission = true) }
            return
        }
        _state.update { it.copy(updateProgress = 0f) }
        viewModelScope.launch {
            try {
                val apk = UpdateManager.download(ctx, info) { p -> _state.update { it.copy(updateProgress = p) } }
                UpdateManager.install(ctx, apk)
                _state.update { it.copy(updateProgress = null) }
            } catch (e: Exception) {
                _state.update { it.copy(updateProgress = null) }
                showMessage("Ažuriranje nije uspelo: ${e.message ?: "greška"}")
            }
        }
    }

    fun installPermissionHandled() = _state.update { it.copy(needsInstallPermission = false) }
}
