package car.mazda.obd.android.feature.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import car.mazda.obd.android.core.logs.AppLogger

@Composable
internal fun DiagnosticSettingsScreen(
    settings: DiagnosticViewSettings,
    hasLogs: Boolean,
    onSettingsChange: (DiagnosticViewSettings) -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to diagnostic logs")
            }
            Text("Log view settings", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection("Layer") {
                FilterChip(
                    selected = settings.selectedLayers.isEmpty(),
                    onClick = { onSettingsChange(settings.copy(selectedLayers = emptySet())) },
                    label = { Text("All layers") },
                )
                AppLogger.Layer.entries.forEach { layer ->
                    FilterChip(
                        selected = layer in settings.selectedLayers,
                        onClick = {
                            val selectedLayers = settings.selectedLayers.toMutableSet().apply {
                                if (!add(layer)) remove(layer)
                            }
                            onSettingsChange(settings.copy(selectedLayers = selectedLayers))
                        },
                        label = { Text(layer.name) },
                    )
                }
            }

            SettingsSection("Content") {
                FilterChip(
                    selected = settings.problemsOnly,
                    onClick = { onSettingsChange(settings.copy(problemsOnly = !settings.problemsOnly)) },
                    label = { Text("Problems only") },
                )
                FilterChip(
                    selected = settings.grouped,
                    onClick = { onSettingsChange(settings.copy(grouped = !settings.grouped)) },
                    label = { Text("Group TX / RX / parser") },
                )
            }

            TextButton(onClick = onClear, enabled = hasLogs) {
                Text("Clear stored journal", color = MaterialTheme.colorScheme.error)
            }
        }

        Button(onClick = onApply, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Apply")
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}
