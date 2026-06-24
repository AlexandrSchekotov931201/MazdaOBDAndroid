package car.mazda.obd.android.feature.trip.summary

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.BuildConfig
import car.mazda.obd.android.core.logs.AppLogger
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
    val logEntries by AppLogger.entries.collectAsStateWithLifecycle()
    var selectedDebugTripId by remember { mutableStateOf<Long?>(null) }
    var selectedActiveDebug by remember { mutableStateOf(false) }
    val activeDebugEvents = if (BuildConfig.DEBUG) {
        activeTrip?.let { trip ->
            logEntries
                .filter { entry -> entry.level != AppLogger.Level.Info }
                .filter { entry -> entry.timestampMs >= trip.startedAtMs }
                .mapIndexed { index, entry ->
                    TripDebugEvent(
                        id = entry.timestampMs * 1_000 + index,
                        occurredAtMs = entry.timestampMs,
                        level = entry.level.name,
                        message = entry.message,
                    )
                }
        }.orEmpty()
    } else {
        emptyList()
    }
    val selectedDebugTrip = if (BuildConfig.DEBUG) {
        recentTrips.firstOrNull { it.id == selectedDebugTripId }
    } else {
        null
    }

    TripsContent(
        activeTrip = activeTrip,
        activeDebugEvents = activeDebugEvents,
        recentTrips = recentTrips,
        selectedDebugTrip = selectedDebugTrip,
        selectedActiveDebug = selectedActiveDebug,
        onOpenActiveDebug = { selectedActiveDebug = true },
        onOpenDebugTrip = { trip -> selectedDebugTripId = trip.id },
        onCloseDebugTrip = {
            selectedDebugTripId = null
            selectedActiveDebug = false
        },
        onOpenMenu = onOpenMenu,
        modifier = modifier,
    )
}

@Composable
private fun TripsContent(
    activeTrip: ActiveTripSummary?,
    activeDebugEvents: List<TripDebugEvent>,
    recentTrips: List<TripSummary>,
    selectedDebugTrip: TripSummary?,
    selectedActiveDebug: Boolean,
    onOpenActiveDebug: () -> Unit,
    onOpenDebugTrip: (TripSummary) -> Unit,
    onCloseDebugTrip: () -> Unit,
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

    if (BuildConfig.DEBUG && selectedDebugTrip != null) {
        TripDebugDetailsScreen(
            title = "Trip debug",
            subtitle = "${selectedDebugTrip.startedAtMs.formatDateTime()} - ${selectedDebugTrip.finishedAtMs.formatTime()}",
            metrics = listOf(
                "Duration" to selectedDebugTrip.durationMs.formatDuration(),
                "Max RPM" to selectedDebugTrip.maxRpm.toString(),
                "Max coolant temp" to selectedDebugTrip.maxEngineTempCelsius.formatTemp(),
            ),
            events = selectedDebugTrip.debugEvents,
            emptyText = "No handled OBD errors for this trip",
            onBack = onCloseDebugTrip,
            modifier = modifier,
        )
        return
    }

    if (BuildConfig.DEBUG && selectedActiveDebug && activeTrip != null) {
        TripDebugDetailsScreen(
            title = "Current trip debug",
            subtitle = "Started ${activeTrip.startedAtMs.formatTime()}",
            metrics = listOf(
                "Duration" to activeTrip.durationMs(nowMs).formatDuration(),
                "Max RPM" to activeTrip.maxRpm.toString(),
                "Max coolant temp" to activeTrip.maxEngineTempCelsius.formatTemp(),
            ),
            events = activeDebugEvents,
            emptyText = "No handled OBD errors for the current trip",
            onBack = onCloseDebugTrip,
            modifier = modifier,
        )
        return
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
                        debugEvents = activeDebugEvents,
                        onOpenDebug = onOpenActiveDebug,
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
                        onOpenDebug = onOpenDebugTrip,
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
                    TripSummaryCard(
                        trip = trip,
                        onOpenDebug = onOpenDebugTrip,
                    )
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
    debugEvents: List<TripDebugEvent>,
    onOpenDebug: () -> Unit,
    nowMs: Long,
) {
    val metrics = buildList {
        add("Duration" to activeTrip.durationMs(nowMs).formatDuration())
        add("Max RPM" to activeTrip.maxRpm.toString())
        add("Max coolant temp" to activeTrip.maxEngineTempCelsius.formatTemp())
    }

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
            metrics = metrics
        )
        if (BuildConfig.DEBUG) {
            TripDebugPreview(
                events = debugEvents,
                onOpenDetails = onOpenDebug,
            )
        }
    }
}

@Composable
private fun TripSummaryCard(
    trip: TripSummary,
    emphasize: Boolean = false,
    onOpenDebug: ((TripSummary) -> Unit)? = null,
) {
    val metrics = buildList {
        add("Duration" to trip.durationMs.formatDuration())
        add("Max RPM" to trip.maxRpm.toString())
        add("Max coolant temp" to trip.maxEngineTempCelsius.formatTemp())
    }

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
            metrics = metrics
        )
        if (BuildConfig.DEBUG) {
            TripDebugPreview(
                events = trip.debugEvents,
                onOpenDetails = {
                    onOpenDebug?.invoke(trip)
                },
            )
        }
    }
}

@Composable
private fun TripDebugPreview(
    events: List<TripDebugEvent>,
    onOpenDetails: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (events.isEmpty()) {
            Text(
                text = "No handled OBD errors",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF607D8B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Debug events: ${events.size}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF607D8B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onOpenDetails) {
                    Text(text = "Details")
                }
            }
        }
    }
}

@Composable
private fun TripDebugDetailsScreen(
    title: String,
    subtitle: String,
    metrics: List<Pair<String, String>>,
    events: List<TripDebugEvent>,
    emptyText: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val report = remember(title, subtitle, metrics, events) {
        buildDebugReport(
            title = title,
            subtitle = subtitle,
            metrics = metrics,
            events = events,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to trips",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
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
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, title)
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(
                        Intent.createChooser(intent, "Share trip debug")
                    )
                },
                enabled = events.isNotEmpty(),
            ) {
                Text(text = "Share")
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SummarySurface(
                    containerColor = Color(0xFFF7F7FA),
                    contentColor = Color(0xFF20242A),
                ) {
                    MetricGrid(
                        metrics = metrics
                    )
                }
            }

            if (events.isEmpty()) {
                item {
                    EmptyState(text = emptyText)
                }
            } else {
                items(
                    items = events,
                    key = { event -> event.id },
                ) { event ->
                    TripDebugEventCard(event = event)
                }
            }
        }
    }
}

@Composable
private fun TripDebugEventCard(event: TripDebugEvent) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = Color(0xFFFFF8E1),
        contentColor = Color(0xFF4E342E),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = event.occurredAtMs.formatTimeWithSeconds(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF5D4037),
                    maxLines = 1,
                )
                Text(
                    text = event.level,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF795548),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = Color(0xFFE0CFA9))
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF3E2723),
            )
        }
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

private fun Long.formatTimeWithSeconds(): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(this))

private fun buildDebugReport(
    title: String,
    subtitle: String,
    metrics: List<Pair<String, String>>,
    events: List<TripDebugEvent>,
): String = buildString {
    appendLine(title)
    appendLine(subtitle)
    appendLine()

    appendLine("Summary")
    metrics.forEach { (label, value) ->
        appendLine("$label: $value")
    }
    appendLine("Debug events: ${events.size}")
    appendLine()

    if (events.isEmpty()) {
        appendLine("No handled OBD errors.")
    } else {
        appendLine("Events")
        events.forEachIndexed { index, event ->
            appendLine()
            appendLine("#${index + 1} ${event.occurredAtMs.formatDateTime()} ${event.occurredAtMs.formatTimeWithSeconds()}")
            appendLine("Level: ${event.level}")
            appendLine("Message: ${event.message}")
        }
    }
}
