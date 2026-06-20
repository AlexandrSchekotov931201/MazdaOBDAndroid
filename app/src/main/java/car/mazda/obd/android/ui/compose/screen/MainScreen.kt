package car.mazda.obd.android.ui.compose.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.ui.MainViewModel
import car.mazda.obd.android.ui.command.MainViewCommand
import car.mazda.obd.android.ui.compose.MazdaStyleTachometer
import car.mazda.obd.android.ui.utils.sound.SpeechPlayer

@Composable
internal fun MainScreen(
    viewModel: MainViewModel,
    speechPlayer: SpeechPlayer,
    onOpenLogs: () -> Unit,
    modifier: Modifier
) {
    val connectionTextState by viewModel.connectionTextState.collectAsStateWithLifecycle()
    val rpmState by viewModel.rpmState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.mainViewCommands.collect { cmd ->
            when (cmd) {
                MainViewCommand.SoundGreeting -> {
                    speechPlayer.greetingSound()
                }
            }
        }
    }

    MainContent(
        connectionText = connectionTextState,
        rpm = rpmState,
        onOpenLogs = onOpenLogs,
        modifier = modifier,
    )
}

@Composable
private fun MainContent(
    connectionText: String,
    rpm: Int,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Status: $connectionText",
        )
        Spacer(Modifier.height(16.dp))
        MazdaStyleTachometer(
            rpm = rpm,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpenLogs) {
            Text("Open logs")
        }
    }
}
