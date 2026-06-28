package car.mazda.obd.android.feature.trip.route

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun RouteColorControls(
    mode: RouteColorMode,
    temperatureSettings: TemperatureColorSettings,
    rpmSettings: RpmColorSettings,
    onModeSelected: (RouteColorMode) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = mode == RouteColorMode.CoolantTemperature,
                onClick = { onModeSelected(RouteColorMode.CoolantTemperature) },
                label = { Text("Coolant") },
            )
            FilterChip(
                selected = mode == RouteColorMode.EngineRpm,
                onClick = { onModeSelected(RouteColorMode.EngineRpm) },
                label = { Text("RPM") },
            )
            OutlinedButton(onClick = onOpenSettings) { Text("Ranges") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            legendEntries(mode, temperatureSettings, rpmSettings).forEach { entry ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(entry.band.displayColor(), CircleShape)
                    )
                    Text(entry.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun RouteRangeSettingsDialog(
    mode: RouteColorMode,
    temperatureSettings: TemperatureColorSettings,
    rpmSettings: RpmColorSettings,
    onTemperatureSettingsChanged: (TemperatureColorSettings) -> Unit,
    onRpmSettingsChanged: (RpmColorSettings) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == RouteColorMode.CoolantTemperature) "Coolant ranges" else "RPM ranges") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (mode) {
                    RouteColorMode.CoolantTemperature -> {
                        RangeSliderSetting(
                            label = "Blue below ${temperatureSettings.coldBelowCelsius}°C",
                            value = temperatureSettings.coldBelowCelsius.toFloat(),
                            valueRange = -20f..(temperatureSettings.normalFromCelsius - 5).toFloat(),
                            onValueChange = { value ->
                                onTemperatureSettingsChanged(
                                    temperatureSettings.copy(coldBelowCelsius = value.roundToStep(5))
                                )
                            },
                        )
                        RangeSliderSetting(
                            label = "Green from ${temperatureSettings.normalFromCelsius}°C",
                            value = temperatureSettings.normalFromCelsius.toFloat(),
                            valueRange = (temperatureSettings.coldBelowCelsius + 5).toFloat()..
                                (temperatureSettings.criticalFromCelsius - 5).toFloat(),
                            onValueChange = { value ->
                                onTemperatureSettingsChanged(
                                    temperatureSettings.copy(normalFromCelsius = value.roundToStep(5))
                                )
                            },
                        )
                        RangeSliderSetting(
                            label = "Red from ${temperatureSettings.criticalFromCelsius}°C",
                            value = temperatureSettings.criticalFromCelsius.toFloat(),
                            valueRange = (temperatureSettings.normalFromCelsius + 5).toFloat()..130f,
                            onValueChange = { value ->
                                onTemperatureSettingsChanged(
                                    temperatureSettings.copy(criticalFromCelsius = value.roundToStep(5))
                                )
                            },
                        )
                    }
                    RouteColorMode.EngineRpm -> {
                        RangeSliderSetting(
                            label = "Yellow from ${rpmSettings.cautionFromRpm} RPM",
                            value = rpmSettings.cautionFromRpm.toFloat(),
                            valueRange = 500f..(rpmSettings.dangerFromRpm - 250).toFloat(),
                            onValueChange = { value ->
                                onRpmSettingsChanged(
                                    rpmSettings.copy(cautionFromRpm = value.roundToStep(250))
                                )
                            },
                        )
                        RangeSliderSetting(
                            label = "Red from ${rpmSettings.dangerFromRpm} RPM",
                            value = rpmSettings.dangerFromRpm.toFloat(),
                            valueRange = (rpmSettings.cautionFromRpm + 250).toFloat()..8_000f,
                            onValueChange = { value ->
                                onRpmSettingsChanged(
                                    rpmSettings.copy(dangerFromRpm = value.roundToStep(250))
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onReset) { Text("Reset defaults") } },
    )
}

@Composable
private fun RangeSliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

private data class LegendEntry(val band: RouteColorBand, val label: String)

private fun legendEntries(
    mode: RouteColorMode,
    temperature: TemperatureColorSettings,
    rpm: RpmColorSettings,
): List<LegendEntry> = when (mode) {
    RouteColorMode.CoolantTemperature -> listOf(
        LegendEntry(RouteColorBand.Blue, "< ${temperature.coldBelowCelsius}°C"),
        LegendEntry(RouteColorBand.Yellow, "${temperature.coldBelowCelsius}–${temperature.normalFromCelsius - 1}°C"),
        LegendEntry(RouteColorBand.Green, "${temperature.normalFromCelsius}–${temperature.criticalFromCelsius - 1}°C"),
        LegendEntry(RouteColorBand.Red, "≥ ${temperature.criticalFromCelsius}°C"),
        LegendEntry(RouteColorBand.Unknown, "No data"),
    )
    RouteColorMode.EngineRpm -> listOf(
        LegendEntry(RouteColorBand.Green, "< ${rpm.cautionFromRpm} RPM"),
        LegendEntry(RouteColorBand.Yellow, "${rpm.cautionFromRpm}–${rpm.dangerFromRpm - 1} RPM"),
        LegendEntry(RouteColorBand.Red, "≥ ${rpm.dangerFromRpm} RPM"),
    )
}

private fun Float.roundToStep(step: Int): Int = (this / step).roundToInt() * step

internal fun RouteColorBand.displayColor(): Color = when (this) {
    RouteColorBand.Unknown -> Color(0xFF78909C)
    RouteColorBand.Blue -> Color(0xFF1976D2)
    RouteColorBand.Green -> Color(0xFF2E7D32)
    RouteColorBand.Yellow -> Color(0xFFFFA000)
    RouteColorBand.Red -> Color(0xFFC62828)
}
