package com.camac.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.camac.tracker.data.SmsRepository
import com.camac.tracker.sms.Notifier
import com.camac.tracker.ui.CamacApp
import com.camac.tracker.ui.CamacTheme
import com.camac.tracker.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Splash ostaje dok se ne učitaju poruke (obično ispod pola sekunde)
        splash.setKeepOnScreenCondition { vm.state.value.loading }
        enableEdgeToEdge()
        Notifier.createChannels(this)

        setContent {
            CamacTheme {
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { vm.refresh() }
                val request = { launcher.launch(vm.requiredPermissions().toTypedArray()) }

                LaunchedEffect(Unit) {
                    if (!SmsRepository.hasReadPermission(this@MainActivity)) request()
                }
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

                CamacApp(vm = vm, onRequestPermissions = request)
            }
        }
    }
}
