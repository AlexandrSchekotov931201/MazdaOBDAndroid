package car.mazda.obd.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.feature.dashboard.MainViewModel
import car.mazda.obd.android.feature.monitor.FloatingWidgetSize
import car.mazda.obd.android.feature.monitor.ObdConnectionSettings
import car.mazda.obd.android.ui.AppToolbar

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onOpenMenu: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayPermissionGranted by viewModel.overlayEnabledState.collectAsStateWithLifecycle()
    val floatingWidgetEnabled by viewModel.floatingWidgetEnabledState.collectAsStateWithLifecycle()
    val floatingWidgetSize by viewModel.floatingWidgetSizeState.collectAsStateWithLifecycle()
    val autoStartEnabled by viewModel.autoStartEnabledState.collectAsStateWithLifecycle()
    val connectionSettings by viewModel.connectionSettingsState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        AppToolbar(
            onOpenMenu = onOpenMenu,
            title = "Settings",
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsSwitchCard(
                title = "Floating widget",
                subtitle = if (overlayPermissionGranted) {
                    "Shows RPM, connection status, and coolant temperature"
                } else {
                    "Overlay permission required to show RPM, connection, and coolant"
                },
                checked = floatingWidgetEnabled,
                onCheckedChange = viewModel::setFloatingWidgetEnabled,
                enabled = overlayPermissionGranted,
            ) {
                WidgetSizeSelector(
                    selectedSize = floatingWidgetSize,
                    onSelectSize = viewModel::setFloatingWidgetSize,
                    enabled = floatingWidgetEnabled && overlayPermissionGranted,
                )
                OutlinedButton(
                    onClick = onRequestOverlayPermission,
                    enabled = !overlayPermissionGranted,
                ) {
                    Text(if (overlayPermissionGranted) "Permission granted" else "Allow overlay")
                }
            }

            SettingsSwitchCard(
                title = "Start after phone boot",
                subtitle = if (autoStartEnabled) "Enabled" else "Disabled",
                checked = autoStartEnabled,
                onCheckedChange = viewModel::setAutoStartEnabled,
            )

            SettingsCard(
                title = "OBD connection",
                subtitle = "Saved values are used when monitoring starts",
            ) {
                ObdConnectionSettingsEditor(
                    settings = connectionSettings,
                    onSettingsChange = viewModel::setConnectionSettings,
                    onApply = viewModel::applyConnectionSettings,
                    onReset = viewModel::resetConnectionSettings,
                )
            }
        }
    }
}

@Composable
private fun ObdConnectionSettingsEditor(
    settings: ObdConnectionSettings,
    onSettingsChange: (ObdConnectionSettings) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsNumberField(
            label = "RPM poll, ms",
            value = settings.rpmPollPeriodMs,
            min = ObdConnectionSettings.MIN_POLL_PERIOD_MS,
            max = ObdConnectionSettings.MAX_POLL_PERIOD_MS,
            onValueChange = { onSettingsChange(settings.copy(rpmPollPeriodMs = it)) },
        )
        SettingsNumberField(
            label = "Coolant poll, ms",
            value = settings.coolantPollPeriodMs,
            min = ObdConnectionSettings.MIN_POLL_PERIOD_MS,
            max = ObdConnectionSettings.MAX_POLL_PERIOD_MS,
            onValueChange = { onSettingsChange(settings.copy(coolantPollPeriodMs = it)) },
        )
        SettingsNumberField(
            label = "Connect timeout, ms",
            value = settings.connectTimeoutMs.toLong(),
            min = ObdConnectionSettings.MIN_TIMEOUT_MS.toLong(),
            max = ObdConnectionSettings.MAX_TIMEOUT_MS.toLong(),
            onValueChange = { onSettingsChange(settings.copy(connectTimeoutMs = it.toSafeInt())) },
        )
        SettingsNumberField(
            label = "Read timeout, ms",
            value = settings.readTimeoutMs.toLong(),
            min = ObdConnectionSettings.MIN_TIMEOUT_MS.toLong(),
            max = ObdConnectionSettings.MAX_TIMEOUT_MS.toLong(),
            onValueChange = { onSettingsChange(settings.copy(readTimeoutMs = it.toSafeInt())) },
        )
        SettingsNumberField(
            label = "Wi-Fi request timeout, ms",
            value = settings.networkRequestTimeoutMs,
            min = ObdConnectionSettings.MIN_TIMEOUT_MS.toLong(),
            max = ObdConnectionSettings.MAX_TIMEOUT_MS.toLong(),
            onValueChange = { onSettingsChange(settings.copy(networkRequestTimeoutMs = it)) },
        )
        SettingsNumberField(
            label = "Timeouts before reconnect",
            value = settings.adapterTimeoutReconnectThreshold.toLong(),
            min = ObdConnectionSettings.MIN_RECONNECT_THRESHOLD.toLong(),
            max = ObdConnectionSettings.MAX_RECONNECT_THRESHOLD.toLong(),
            onValueChange = { onSettingsChange(settings.copy(adapterTimeoutReconnectThreshold = it.toSafeInt())) },
        )
        SettingsNumberField(
            label = "Engine off delay, ms",
            value = settings.engineOffDelayMs,
            min = ObdConnectionSettings.MIN_ENGINE_OFF_DELAY_MS,
            max = ObdConnectionSettings.MAX_ENGINE_OFF_DELAY_MS,
            onValueChange = { onSettingsChange(settings.copy(engineOffDelayMs = it)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onReset,
            ) {
                Text("Reset defaults")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onApply,
            ) {
                Text("Apply")
            }
        }
    }
}

private fun Long.toSafeInt(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

@Composable
private fun SettingsNumberField(
    label: String,
    value: Long,
    min: Long,
    max: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toLongOrNull()
    val errorText = when {
        text.isBlank() -> "Required"
        parsed == null -> "Numbers only"
        parsed < min -> "Minimum: $min"
        parsed > max -> "Maximum: $max"
        else -> null
    }

    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = text,
        onValueChange = { raw ->
            text = raw.filter(Char::isDigit)
            text.toLongOrNull()
                ?.takeIf { it in min..max }
                ?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        isError = errorText != null,
        supportingText = { Text(errorText ?: "Range: $min-$max") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun WidgetSizeSelector(
    selectedSize: FloatingWidgetSize,
    onSelectSize: (FloatingWidgetSize) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Widget size",
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FloatingWidgetSize.entries.forEach { size ->
                FilterChip(
                    selected = selectedSize == size,
                    onClick = { onSelectSize(size) },
                    enabled = enabled,
                    label = { Text(size.label) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    action: @Composable (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled,
                )
            }

            action?.invoke()
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            content()
        }
    }
}
