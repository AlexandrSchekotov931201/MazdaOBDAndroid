package car.mazda.obd.android.feature.trip.route

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.BuildConfig
import car.mazda.obd.android.ui.AppToolbar
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun TripRouteScreen(
    viewModel: TripRouteViewModel,
    isActiveTrip: Boolean,
    onOpenDrawer: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        AppToolbar(
            onOpenMenu = onBack,
            title = if (isActiveTrip) "Current trip map" else "Trip map",
            isBackNavigation = true,
            modifier = Modifier.openDrawerOnRightSwipe(onOpenDrawer),
        )
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
            state.points.isEmpty() -> NoRouteState(isActiveTrip, Modifier.padding(16.dp))
            else -> RouteContent(state, isActiveTrip, onOpenDrawer, viewModel::deleteSelectedRoute)
        }
    }
}

@Composable
private fun RouteContent(
    state: TripRouteUiState,
    active: Boolean,
    onOpenDrawer: () -> Unit,
    onDelete: () -> Unit,
) {
    val camera = rememberCameraPositionState()
    var mapLoaded by remember { mutableStateOf(false) }
    var colorMode by remember { mutableStateOf(RouteColorMode.CoolantTemperature) }
    var temperatureSettings by remember { mutableStateOf(TemperatureColorSettings()) }
    var rpmSettings by remember { mutableStateOf(RpmColorSettings()) }
    var showRangeSettings by remember { mutableStateOf(false) }

    LaunchedEffect(mapLoaded, state.points.firstOrNull()?.id, state.points.lastOrNull()?.id) {
        if (!mapLoaded || !BuildConfig.MAPS_API_KEY_CONFIGURED) return@LaunchedEffect
        val coordinates = state.points.map { LatLng(it.latitude, it.longitude) }
        when (coordinates.size) {
            0 -> Unit
            1 -> camera.animate(CameraUpdateFactory.newLatLngZoom(coordinates.first(), 16f))
            else -> {
                val bounds = LatLngBounds.builder().also { builder -> coordinates.forEach(builder::include) }.build()
                camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, 96))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (BuildConfig.MAPS_API_KEY_CONFIGURED) {
            GoogleMap(
                modifier = Modifier.fillMaxWidth().weight(1f),
                cameraPositionState = camera,
                onMapLoaded = { mapLoaded = true },
            ) {
                state.points.groupBy { it.segment }.values.forEach { segment ->
                    segment.zipWithNext().forEach { (from, to) ->
                        Polyline(
                            points = listOf(LatLng(from.latitude, from.longitude), LatLng(to.latitude, to.longitude)),
                            color = to.colorBand(colorMode, temperatureSettings, rpmSettings).displayColor(),
                            width = 11f,
                        )
                    }
                }
                state.points.firstOrNull()?.let {
                    Marker(MarkerState(LatLng(it.latitude, it.longitude)), title = "Start")
                }
                state.points.lastOrNull()?.let {
                    Marker(
                        MarkerState(LatLng(it.latitude, it.longitude)),
                        title = if (active) "Current position" else "Finish",
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    "Route data is recorded. Add MAPS_API_KEY to Gradle properties to display the map tiles.",
                    Modifier.padding(24.dp),
                )
            }
        }

        Column(Modifier.fillMaxWidth()) {
            RouteMetrics(
                state.statistics,
                Modifier
                    .openDrawerOnRightSwipe(onOpenDrawer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            RouteColorControls(
                mode = colorMode,
                temperatureSettings = temperatureSettings,
                rpmSettings = rpmSettings,
                onModeSelected = { colorMode = it },
                onOpenSettings = { showRangeSettings = true },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (!active) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete route data") }
            }
        }
    }

    if (showRangeSettings) {
        RouteRangeSettingsDialog(
            mode = colorMode,
            temperatureSettings = temperatureSettings,
            rpmSettings = rpmSettings,
            onTemperatureSettingsChanged = { temperatureSettings = it },
            onRpmSettingsChanged = { rpmSettings = it },
            onReset = {
                temperatureSettings = TemperatureColorSettings()
                rpmSettings = RpmColorSettings()
            },
            onDismiss = { showRangeSettings = false },
        )
    }
}

private fun Modifier.openDrawerOnRightSwipe(onOpenDrawer: () -> Unit): Modifier =
    pointerInput(onOpenDrawer) {
        var draggedRightPx = 0f
        val thresholdPx = 64.dp.toPx()
        detectHorizontalDragGestures(
            onDragStart = { draggedRightPx = 0f },
            onHorizontalDrag = { change, dragAmount ->
                if (dragAmount > 0f) draggedRightPx += dragAmount else draggedRightPx = 0f
                change.consume()
            },
            onDragEnd = {
                if (draggedRightPx >= thresholdPx) onOpenDrawer()
                draggedRightPx = 0f
            },
            onDragCancel = { draggedRightPx = 0f },
        )
    }

@Composable
private fun NoRouteState(active: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Text(
            if (active) "Waiting for a GPS point. The OBD trip continues even if location is unavailable."
            else "No route was recorded for this trip. OBD statistics are still available.",
            Modifier.padding(18.dp),
        )
    }
}

@Composable
private fun RouteMetrics(statistics: RouteStatistics, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Metric("Distance", "%.1f km".format(statistics.distanceMeters / 1_000.0))
        Metric(
            "Max GPS speed",
            statistics.maximumSpeedMetersPerSecond?.let { "%.0f km/h".format(it * 3.6f) } ?: "—",
        )
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
