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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.feature.dashboard.MainViewModel
import car.mazda.obd.android.feature.monitor.FloatingWidgetSize
import car.mazda.obd.android.ui.AppToolbar
import car.mazda.obd.android.feature.trip.route.TripRouteSettingsViewModel
import car.mazda.obd.android.core.elm.transport.AdapterEndpoint

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    routeSettingsViewModel: TripRouteSettingsViewModel,
    onOpenMenu: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    locationPermissionGranted: Boolean,
    onSetRouteRecordingEnabled: (Boolean) -> Unit,
    adapterEndpoint: AdapterEndpoint,
    onSaveAdapterEndpoint: (AdapterEndpoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayPermissionGranted by viewModel.overlayEnabledState.collectAsStateWithLifecycle()
    val floatingWidgetEnabled by viewModel.floatingWidgetEnabledState.collectAsStateWithLifecycle()
    val floatingWidgetSize by viewModel.floatingWidgetSizeState.collectAsStateWithLifecycle()
    val autoStartEnabled by viewModel.autoStartEnabledState.collectAsStateWithLifecycle()
    val continueAfterAppClosed by viewModel.continueAfterAppClosedState.collectAsStateWithLifecycle()
    val routeRecordingEnabled by routeSettingsViewModel.recordingEnabled.collectAsStateWithLifecycle()

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
            AdapterConnectionSettings(
                endpoint = adapterEndpoint,
                onSave = onSaveAdapterEndpoint,
            )

            SettingsSwitchCard(
                title = "Trip maps",
                subtitle = when {
                    routeRecordingEnabled && locationPermissionGranted -> "Records the route while a trip is active"
                    !locationPermissionGranted -> "Location permission is required; OBD trips still work without it"
                    else -> "Route recording is disabled"
                },
                checked = routeRecordingEnabled && locationPermissionGranted,
                onCheckedChange = onSetRouteRecordingEnabled,
            )

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
                title = "Keep running after app is closed",
                subtitle = if (continueAfterAppClosed) {
                    "Monitoring and reconnecting continue after the app is removed from recent apps"
                } else {
                    "Monitoring stops when the app is removed from recent apps"
                },
                checked = continueAfterAppClosed,
                onCheckedChange = viewModel::setContinueAfterAppClosed,
            )

            SettingsSwitchCard(
                title = "Start after phone boot",
                subtitle = if (continueAfterAppClosed) {
                    if (autoStartEnabled) "Enabled" else "Disabled"
                } else {
                    "Requires background operation after app close"
                },
                checked = autoStartEnabled,
                onCheckedChange = viewModel::setAutoStartEnabled,
                enabled = continueAfterAppClosed,
            )

        }
    }
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
