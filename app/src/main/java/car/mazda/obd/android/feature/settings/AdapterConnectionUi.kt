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

@Composable
fun AdapterOnboardingScreen(
    onSave: (AdapterEndpoint) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            )
        }
    }
}

@Composable
fun AdapterConnectionSettings(
    endpoint: AdapterEndpoint,
    onSave: (AdapterEndpoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("OBD adapter", style = MaterialTheme.typography.titleLarge)
        Text(
            "Saving a new address immediately disconnects the current adapter and reconnects using the new values.",
            style = MaterialTheme.typography.bodyMedium,
        )
        AdapterConnectionForm(
            initialEndpoint = endpoint,
            buttonLabel = "Save adapter address",
            onSave = onSave,
        )
    }
}

@Composable
private fun AdapterConnectionForm(
    initialEndpoint: AdapterEndpoint?,
    buttonLabel: String,
    onSave: (AdapterEndpoint) -> Unit,
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
        OutlinedTextField(
            value = host,
            onValueChange = { host = it; message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("IP address or host name") },
            placeholder = { Text("192.168.0.10") },
            singleLine = true,
            isError = isError,
        )
        OutlinedTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit); message = null },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Port") },
            placeholder = { Text("35000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = isError,
        )
        message?.let {
            Text(
                text = it,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = {
                AdapterEndpointValidator.validate(host, port).fold(
                    onSuccess = {
                        onSave(it)
                        message = "Adapter address saved"
                        isError = false
                    },
                    onFailure = {
                        message = it.message
                        isError = true
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(buttonLabel)
        }
    }
}
