package car.mazda.obd.android.feature.trip.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.ui.AppToolbar
import car.mazda.obd.android.BuildConfig
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        AppToolbar(onOpenMenu = onBack, title = if (isActiveTrip) "Current trip map" else "Trip map", isBackNavigation = true)
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(32.dp))
            state.points.isEmpty() -> NoRouteState(isActiveTrip, Modifier.padding(16.dp))
            else -> RouteContent(state, isActiveTrip, viewModel::deleteSelectedRoute)
        }
    }
}

@Composable
private fun RouteContent(state: TripRouteUiState, active: Boolean, onDelete: () -> Unit) {
    val camera = rememberCameraPositionState()
    LaunchedEffect(state.points.firstOrNull()?.id, state.points.lastOrNull()?.id) {
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
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (BuildConfig.MAPS_API_KEY_CONFIGURED) GoogleMap(modifier = Modifier.fillMaxWidth().height(430.dp), cameraPositionState = camera) {
            state.points.groupBy { it.segment }.values.forEach { segment ->
                segment.zipWithNext().forEach { (from, to) ->
                    Polyline(
                        points = listOf(LatLng(from.latitude, from.longitude), LatLng(to.latitude, to.longitude)),
                        color = temperatureColor(to.coolantTempCelsius),
                        width = 11f,
                    )
                }
            }
            state.points.firstOrNull()?.let { Marker(MarkerState(LatLng(it.latitude, it.longitude)), title = "Start") }
            state.points.lastOrNull()?.let { Marker(MarkerState(LatLng(it.latitude, it.longitude)), title = if (active) "Current position" else "Finish") }
        } else Surface(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                "Route data is recorded. Add MAPS_API_KEY to Gradle properties to display the map tiles.",
                Modifier.padding(24.dp),
            )
        }
        RouteMetrics(state.statistics, Modifier.padding(16.dp))
        if (!active) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete route data") }
        }
    }
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
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Metric("Distance", "%.1f km".format(statistics.distanceMeters / 1_000.0))
        Metric("Max GPS speed", statistics.maximumSpeedMetersPerSecond?.let { "%.0f km/h".format(it * 3.6f) } ?: "—")
        Metric("GPS points", statistics.recordedPointCount.toString())
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

private fun temperatureColor(celsius: Int?): Color = when {
    celsius == null -> Color(0xFF78909C)
    celsius < 50 -> Color(0xFF1976D2)
    celsius < 70 -> Color(0xFFFFA000)
    celsius < 105 -> Color(0xFF2E7D32)
    else -> Color(0xFFC62828)
}
