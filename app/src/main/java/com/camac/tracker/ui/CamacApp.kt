package com.camac.tracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Phishing
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.camac.tracker.update.UpdateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Tab(val title: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Početna", Icons.Outlined.Phishing, Icons.Filled.Phishing),
    COMMANDS("Komande", Icons.AutoMirrored.Filled.List, Icons.AutoMirrored.Filled.List),
    HISTORY("Istorija", Icons.Outlined.History, Icons.Filled.History),
    SETTINGS("Podešavanja", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CamacApp(vm: MainViewModel, onRequestPermissions: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Sat za "pre 5 min", trajanje pecanja i "čekam odgovor"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(state.messages, state.pending, state.settings.fishingSince) { now = System.currentTimeMillis() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbar.showSnackbar(it)
            vm.consumeSnackbar()
        }
    }

    // Za auto-update: sistem traži dozvolu "instaliraj nepoznate aplikacije" za ovu aplikaciju
    LaunchedEffect(state.needsInstallPermission) {
        if (state.needsInstallPermission) {
            vm.installPermissionHandled()
            vm.showMessage("Uključi \"Dozvoli iz ovog izvora\", pa vrati se i pritisni Ažuriraj")
            context.startActivity(UpdateManager.installPermissionIntent(context))
        }
    }

    // Predictive back: sa drugih tabova "nazad" vodi na Početnu
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    val runCommand = rememberCommandRunner(state.settings) { cmd, input -> vm.send(cmd, input) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(if (tab == Tab.HOME) state.settings.boatName else tab.title)
                },
                actions = {
                    if (tab == Tab.HOME || tab == Tab.HISTORY) {
                        IconButton(onClick = { vm.refresh(); vm.loadWeather(force = true) }) {
                            Icon(Icons.Default.Refresh, "Osveži")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, null) },
                        label = { Text(t.title) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "tab",
            modifier = Modifier.padding(padding)
        ) { current ->
            when (current) {
                Tab.HOME -> HomeScreen(
                    state = state,
                    now = now,
                    onRun = runCommand,
                    onOpenSettings = { tab = Tab.SETTINGS },
                    onOpenHistory = { tab = Tab.HISTORY },
                    onRequestPermissions = onRequestPermissions,
                    onDismissPending = vm::dismissPending,
                    onRefreshWeather = { vm.loadWeather() },
                    onInstallUpdate = vm::installUpdate,
                    onDismissSilence = vm::dismissSilence,
                    onCheckSilence = vm::checkSilence,
                )
                Tab.COMMANDS -> CommandsScreen(state = state, onRun = runCommand)
                Tab.HISTORY -> HistoryScreen(
                    state = state,
                    onShare = { gpx ->
                        scope.launch {
                            try {
                                context.startActivity(vm.buildShareIntent(gpx))
                            } catch (e: Exception) {
                                vm.showMessage("Izvoz nije uspeo")
                            }
                        }
                    },
                    onDeleteSession = vm::deleteSession,
                )
                Tab.SETTINGS -> SettingsScreen(
                    state = state,
                    onUpdate = vm::updateSettings,
                    onSetHomeFromLast = vm::setHomeFromLastPosition,
                    onRequestPermissions = onRequestPermissions,
                    onExportTarget = vm::setExportTarget,
                    onExportNow = vm::exportToTargetNow,
                    onCheckUpdate = { vm.checkForUpdate(manual = true) },
                )
            }
        }
    }
}
