package car.mazda.obd.android.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import car.mazda.obd.android.ui.command.MainViewCommand
import car.mazda.obd.android.ui.compose.screen.LogsScreen
import car.mazda.obd.android.ui.compose.screen.MainScreen
import car.mazda.obd.android.ui.theme.MazdaOBDAndroidTheme
import car.mazda.obd.android.ui.utils.sound.SoundPatterns
import car.mazda.obd.android.ui.utils.sound.SoundPlayer
import car.mazda.obd.android.ui.utils.sound.SpeechPlayer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModelFactory = MainViewModelFactory()

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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    var screen by rememberSaveable { mutableStateOf("main") }

                    when (screen) {
                        "main" -> MainScreen(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = viewModel,
                            onOpenLogs = { screen = "logs" }
                        )

                        "logs" -> LogsScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBack = { screen = "main" }
                        )
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
