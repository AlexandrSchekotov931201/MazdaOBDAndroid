package car.mazda.obd.android.feature.trip.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.feature.dashboard.MainViewModel
import car.mazda.obd.android.ui.AppToolbar
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TripsScreen(
    viewModel: MainViewModel,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeTrip by viewModel.activeTripSummaryState.collectAsStateWithLifecycle()
    val recentTrips by viewModel.recentTripSummariesState.collectAsStateWithLifecycle()

    TripsContent(
        activeTrip = activeTrip,
        recentTrips = recentTrips,
        onOpenMenu = onOpenMenu,
        modifier = modifier,
    )
}

@Composable
private fun TripsContent(
    activeTrip: ActiveTripSummary?,
    recentTrips: List<TripSummary>,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(activeTrip?.startedAtMs) {
        while (activeTrip != null) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppToolbar(
            onOpenMenu = onOpenMenu,
            title = "Trips",
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionTitle(text = "Current trip")
                if (activeTrip == null) {
                    EmptyState(text = "No active trip")
                } else {
                    ActiveTripCard(
                        activeTrip = activeTrip,
                        nowMs = nowMs,
                    )
                }
            }

            item {
                SectionTitle(text = "Last trip")
                val lastTrip = recentTrips.firstOrNull()
                if (lastTrip == null) {
                    EmptyState(text = "No completed trips yet")
                } else {
                    TripSummaryCard(
                        trip = lastTrip,
                        emphasize = true,
                    )
                }
            }

            if (recentTrips.size > 1) {
                item {
                    SectionTitle(text = "History")
                }
                items(
                    items = recentTrips.drop(1),
                    key = { trip -> trip.id },
                ) { trip ->
                    TripSummaryCard(trip = trip)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF20242A),
    )
}

@Composable
private fun EmptyState(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Color(0xFFECEFF1),
        contentColor = Color(0xFF455A64),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ActiveTripCard(
    activeTrip: ActiveTripSummary,
    nowMs: Long,
) {
    SummarySurface(
        containerColor = Color(0xFFE8F5E9),
        contentColor = Color(0xFF1B5E20),
    ) {
        SummaryHeader(
            title = "Running",
            subtitle = "Started ${activeTrip.startedAtMs.formatTime()}",
            status = "Active",
        )
        MetricGrid(
            metrics = listOf(
                "Duration" to activeTrip.durationMs(nowMs).formatDuration(),
                "Max RPM" to activeTrip.maxRpm.toString(),
                "Max temp" to activeTrip.maxCoolantTempCelsius.formatTemp(),
            )
        )
    }
}

@Composable
private fun TripSummaryCard(
    trip: TripSummary,
    emphasize: Boolean = false,
) {
    SummarySurface(
        containerColor = if (emphasize) Color(0xFFF7F7FA) else Color.White,
        contentColor = Color(0xFF20242A),
    ) {
        SummaryHeader(
            title = trip.startedAtMs.formatDateTime(),
            subtitle = "${trip.startedAtMs.formatTime()} - ${trip.finishedAtMs.formatTime()}",
            status = "Completed",
            statusColor = Color(0xFF2E7D32),
        )
        MetricGrid(
            metrics = listOf(
                "Duration" to trip.durationMs.formatDuration(),
                "Max RPM" to trip.maxRpm.toString(),
                "Max temp" to trip.maxCoolantTempCelsius.formatTemp(),
            )
        )
    }
}

@Composable
private fun SummarySurface(
    containerColor: Color,
    contentColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun SummaryHeader(
    title: String,
    subtitle: String,
    status: String,
    statusColor: Color = Color(0xFF2E7D32),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF626772),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun MetricGrid(metrics: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { rowMetrics ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowMetrics.forEach { (label, value) ->
                    MetricItem(
                        label = label,
                        value = value,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowMetrics.size == 1) {
                    Column(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF6B7078),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Int?.formatTemp(): String =
    this?.let { "${it}C" } ?: "--"

private fun Long.formatDuration(): String {
    val totalSeconds = this / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m ${seconds}s"
    }
}

private fun Long.formatDateTime(): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date(this))

private fun Long.formatTime(): String =
    SimpleDateFormat("HH:mm", Locale.US).format(Date(this))
