package car.mazda.obd.android.feature.trip.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.feature.dashboard.MainViewModel
import car.mazda.obd.android.feature.monitor.MonitorConnectionStatus
import car.mazda.obd.android.ui.AppToolbar
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripsScreen(
    viewModel: MainViewModel,
    onOpenMenu: () -> Unit,
    onOpenTripRoute: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTrip by viewModel.activeTripSummaryState.collectAsStateWithLifecycle()
    val recentTrips by viewModel.recentTripSummariesState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatusState.collectAsStateWithLifecycle()
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(activeTrip?.startedAtMs) {
        while (activeTrip != null) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppToolbar(onOpenMenu = onOpenMenu, title = "Trips")
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle("Current trip")
                if (activeTrip == null) {
                    EmptyState(
                        if (connectionStatus == MonitorConnectionStatus.Offline) {
                            "Offline mode. Connect an adapter in Settings before starting a trip."
                        } else {
                            "No active trip. Start one when you are ready to record it."
                        }
                    )
                    Button(
                        onClick = viewModel::startTrip,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        enabled = connectionStatus != MonitorConnectionStatus.Offline,
                    ) { Text("Start trip") }
                } else {
                    TripCard(
                        title = "Running",
                        subtitle = "Started ${activeTrip!!.startedAtMs.formatTime()}",
                        durationMs = activeTrip!!.durationMs(nowMs),
                        maxRpm = activeTrip!!.maxRpm,
                        maxTemp = activeTrip!!.maxEngineTempCelsius,
                        active = true,
                        onClick = { onOpenTripRoute(activeTrip!!.startedAtMs) },
                    )
                    OutlinedButton(
                        onClick = viewModel::stopTrip,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) { Text("Finish trip") }
                }
            }
            item { SectionTitle("History") }
            if (recentTrips.isEmpty()) item { EmptyState("No completed trips yet") }
            else items(recentTrips, key = { it.id }) { trip ->
                TripCard(
                    title = trip.startedAtMs.formatDateTime(),
                    subtitle = "${trip.startedAtMs.formatTime()} – ${trip.finishedAtMs.formatTime()}",
                    durationMs = trip.durationMs,
                    maxRpm = trip.maxRpm,
                    maxTemp = trip.maxEngineTempCelsius,
                    onClick = { onOpenTripRoute(trip.startedAtMs) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyState(text: String) {
    Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TripCard(
    title: String,
    subtitle: String,
    durationMs: Long,
    maxRpm: Int,
    maxTemp: Int?,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (active) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Metric("Duration", durationMs.formatDuration())
                Metric("Max RPM", maxRpm.toString())
                Metric("Max coolant", maxTemp?.let { "${it}°C" } ?: "—")
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun Long.formatDuration(): String {
    val seconds = this / 1_000L
    return if (seconds >= 3_600) "${seconds / 3_600}h ${(seconds % 3_600) / 60}m" else "${seconds / 60}m ${seconds % 60}s"
}

private fun Long.formatDateTime() = SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(this))
private fun Long.formatTime() = SimpleDateFormat("HH:mm", Locale.US).format(Date(this))
