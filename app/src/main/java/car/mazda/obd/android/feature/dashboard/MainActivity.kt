package car.mazda.obd.android.feature.dashboard

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import car.mazda.obd.android.feature.dashboard.command.MainViewCommand
import car.mazda.obd.android.feature.logs.LogsScreen
import car.mazda.obd.android.feature.dashboard.ui.MainScreen
import car.mazda.obd.android.feature.trip.summary.TripsScreen
import car.mazda.obd.android.ui.AppDrawer
import car.mazda.obd.android.ui.AppDrawerDestination
import car.mazda.obd.android.ui.theme.MazdaOBDAndroidTheme
import car.mazda.obd.android.core.sound.SoundPatterns
import car.mazda.obd.android.core.sound.SoundPlayer
import car.mazda.obd.android.core.sound.SpeechPlayer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModelFactory by lazy {
        MainViewModelFactory(applicationContext)
    }

    private val viewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory
        )[MainViewModel::class.java]
    }

    private val speechPlayer by lazy { SpeechPlayer(this) }
    private val soundPlayer by lazy { SoundPlayer() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MazdaOBDAndroidTheme {
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                var screen by rememberSaveable { mutableStateOf("main") }
                val connectionText by viewModel.connectionTextState.collectAsStateWithLifecycle()
                val selectedDestination = when (screen) {
                    "trips" -> AppDrawerDestination.Trips
                    "logs" -> AppDrawerDestination.Logs
                    else -> AppDrawerDestination.Dashboard
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawer(
                            selectedDestination = selectedDestination,
                            isReady = connectionText.contains("ready", ignoreCase = true),
                            onSelectDestination = { destination ->
                                screen = when (destination) {
                                    AppDrawerDestination.Dashboard -> "main"
                                    AppDrawerDestination.Trips -> "trips"
                                    AppDrawerDestination.Logs -> "logs"
                                }
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                ) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        val openDrawer = {
                            scope.launch { drawerState.open() }
                            Unit
                        }

                        when (screen) {
                            "main" -> MainScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = viewModel,
                                onOpenMenu = openDrawer
                            )

                            "logs" -> LogsScreen(
                                modifier = Modifier.padding(innerPadding),
                                onOpenMenu = openDrawer
                            )

                            "trips" -> TripsScreen(
                                modifier = Modifier.padding(innerPadding),
                                viewModel = viewModel,
                                onOpenMenu = openDrawer
                            )
                        }
                    }
                }
            }
        }

        observeSoundCommands()
        viewModel.onCreate()
    }

    override fun onStart() {
        super.onStart()
        requestWifiPermission()
    }

    override fun onStop() {
        super.onStop()
        speechPlayer.stop()
    }

    override fun onDestroy() {
        soundPlayer.release()
        super.onDestroy()
    }

    private fun observeSoundCommands() {
        lifecycleScope.launch {
            viewModel.mainViewCommands.collect { cmd ->
                when (cmd) {
                    MainViewCommand.SoundGreeting -> speechPlayer.greetingSound()
                    MainViewCommand.SoundGoodbye -> soundPlayer.playPattern(SoundPatterns.TripleLongAlert)
                    MainViewCommand.SoundWarmupWarning -> soundPlayer.playPattern(SoundPatterns.TripleShortAlert)
                    MainViewCommand.SoundOverheatWarning -> soundPlayer.playPattern(SoundPatterns.RapidAlert)
                }
            }
        }
    }

    private fun requestWifiPermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                    1001
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1002
                )
            }
        }
    }
}
