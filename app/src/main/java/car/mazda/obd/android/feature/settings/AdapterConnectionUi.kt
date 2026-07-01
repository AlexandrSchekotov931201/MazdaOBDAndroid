package car.mazda.obd.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import car.mazda.obd.android.core.elm.transport.AdapterEndpoint
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.feature.monitor.MonitorConnectionStatus
import car.mazda.obd.android.feature.monitor.ObdMonitorStateStore
import androidx.compose.ui.platform.LocalContext

@Composable
fun AdapterOnboardingScreen(
    onSave: (AdapterEndpoint) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val monitorState by ObdMonitorStateStore.state.collectAsStateWithLifecycle()
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Text("Connect your OBD adapter", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Connect the phone to the adapter's Wi-Fi, then enter its network address. You can change it later in Settings.",
                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            AdapterConnectionForm(
                initialEndpoint = null,
                buttonLabel = "Save and continue",
                onSave = onSave,
                connectionStatus = monitorState.connectionStatus,
                connectionError = monitorState.connectionError,
            )
            Text(
                text = "You can continue without an adapter. The app will work offline, and connection details can be added later in Settings.",
                modifier = Modifier.padding(top = 20.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                enabled = monitorState.connectionStatus != MonitorConnectionStatus.Connecting,
            ) {
                Text("Continue offline")
            }
        }
    }
}

@Composable
fun AdapterConnectionSettings(
    endpoint: AdapterEndpoint?,
    onSave: (AdapterEndpoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val monitorState by ObdMonitorStateStore.state.collectAsStateWithLifecycle()
    val endpointVerified = AdapterConnectionPreferences(LocalContext.current).isVerified
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("OBD adapter", style = MaterialTheme.typography.titleLarge)
        Text(
            "Saving replaces the current address and performs one connection check. Automatic reconnect is enabled after a successful check.",
            style = MaterialTheme.typography.bodyMedium,
        )
        AdapterConnectionForm(
            initialEndpoint = endpoint,
            buttonLabel = "Save adapter address",
            onSave = onSave,
            connectionStatus = monitorState.connectionStatus,
            connectionError = monitorState.connectionError ?: if (!endpointVerified) {
                "Adapter address is not verified. Save it to try connecting."
            } else null,
        )
    }
}

@Composable
private fun AdapterConnectionForm(
    initialEndpoint: AdapterEndpoint?,
    buttonLabel: String,
    onSave: (AdapterEndpoint) -> Unit,
    connectionStatus: MonitorConnectionStatus,
    connectionError: String?,
) {
    var host by remember { mutableStateOf(initialEndpoint?.host.orEmpty()) }
    var port by remember { mutableStateOf(initialEndpoint?.port?.toString().orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    LaunchedEffect(initialEndpoint) {
        host = initialEndpoint?.host.orEmpty()
        port = initialEndpoint?.port?.toString().orEmpty()
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val connecting = connectionStatus == MonitorConnectionStatus.Connecting
        OutlinedTextField(
            value = host,
            onValueChange = { host = it; message = null; isError = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("IP address or host name") },
            placeholder = { Text("192.168.0.10") },
            singleLine = true,
            isError = isError,
            enabled = !connecting,
        )
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit); message = null; isError = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Port") },
            placeholder = { Text("35000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = isError,
            enabled = !connecting,
        )
        val statusMessage = when {
            message != null -> message
            connecting -> "Connecting to adapter..."
            connectionError != null -> connectionError
            connectionStatus == MonitorConnectionStatus.Ready -> "Adapter connected"
            else -> null
        }
        statusMessage?.let {
            Text(
                text = it,
                color = if (isError || connectionError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = {
                AdapterEndpointValidator.validate(host, port).fold(
                    onSuccess = {
                        onSave(it)
                        message = null
                        isError = false
                    },
                    onFailure = {
                        message = it.message
                        isError = true
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !connecting,
        ) {
            Text(if (connecting) "Connecting..." else buttonLabel)
        }
    }
}
