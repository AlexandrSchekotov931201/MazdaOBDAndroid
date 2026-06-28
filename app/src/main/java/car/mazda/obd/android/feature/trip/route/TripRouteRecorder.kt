package car.mazda.obd.android.feature.trip.route

import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.feature.location.LocationDataSource
import car.mazda.obd.android.feature.trip.TripState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class RouteTelemetry(val rpm: Int, val coolantTempCelsius: Int?)

class TripRouteRecorder(
    private val scope: CoroutineScope,
    private val locationDataSource: LocationDataSource,
    private val repository: TripRouteRepository,
    private val telemetry: () -> RouteTelemetry,
    private val onPointSaved: () -> Unit,
) {
    private var recordingJob: Job? = null
    private var recordingTripStartedAtMs: Long? = null

    fun onTripStateChanged(state: TripState, activeTripStartedAtMs: Long?) {
        when (state) {
            TripState.Active -> activeTripStartedAtMs?.let(::startIfNeeded)
            TripState.Finishing -> Unit
            TripState.Idle -> stop()
        }
    }

    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
        recordingTripStartedAtMs = null
    }

    private fun startIfNeeded(startedAtMs: Long) {
        if (recordingJob?.isActive == true && recordingTripStartedAtMs == startedAtMs) return
        stop()
        recordingTripStartedAtMs = startedAtMs
        recordingJob = scope.launch {
            val segment = repository.nextSegment(startedAtMs)
            val locationFilter = TripRouteLocationFilter()
            locationDataSource.locations()
                .catch { error ->
                    if (error is CancellationException) throw error
                    AppLogger.handledError("Route location stream stopped: ${error.message}")
                }
                .collect { location ->
                    if (!locationFilter.accept(location)) return@collect
                    val snapshot = telemetry()
                    repository.append(
                        TripRoutePoint(
                            tripStartedAtMs = startedAtMs,
                            recordedAtMs = location.recordedAtMs,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracyMeters = location.accuracyMeters,
                            speedMetersPerSecond = location.speedMetersPerSecond,
                            engineRpm = snapshot.rpm,
                            coolantTempCelsius = snapshot.coolantTempCelsius,
                            segment = segment,
                        )
                    )
                    onPointSaved()
                }
        }
    }
}
