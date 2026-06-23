package car.mazda.obd.android.feature.trip.summary

import car.mazda.obd.android.feature.trip.TripState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TripSummaryTracker(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _activeTrip = MutableStateFlow<ActiveTripSummary?>(null)
    val activeTrip: StateFlow<ActiveTripSummary?> = _activeTrip

    fun onTripStateChanged(state: TripState): TripSummary? =
        when (state) {
            is TripState.Active -> {
                if (_activeTrip.value == null) {
                    _activeTrip.value = ActiveTripSummary(
                        startedAtMs = clock(),
                        maxRpm = 0,
                        maxCoolantTempCelsius = null,
                    )
                }
                null
            }

            is TripState.Finishing -> null
            is TripState.Idle -> finishTrip()
        }

    fun onRpmChanged(rpm: Int) {
        updateActiveTrip { current ->
            current.copy(maxRpm = maxOf(current.maxRpm, rpm))
        }
    }

    fun onCoolantTemperatureChanged(celsius: Int?) {
        val temp = celsius ?: return
        updateActiveTrip { current ->
            current.copy(
                maxCoolantTempCelsius = maxOfNullable(
                    current.maxCoolantTempCelsius,
                    temp,
                )
            )
        }
    }

    private fun updateActiveTrip(transform: (ActiveTripSummary) -> ActiveTripSummary) {
        val current = _activeTrip.value ?: return
        _activeTrip.value = transform(current)
    }

    private fun finishTrip(): TripSummary? {
        val active = _activeTrip.value ?: return null
        val finished = TripSummary(
            startedAtMs = active.startedAtMs,
            finishedAtMs = clock(),
            maxRpm = active.maxRpm,
            maxCoolantTempCelsius = active.maxCoolantTempCelsius,
        )
        _activeTrip.value = null
        return finished
    }

    private fun maxOfNullable(a: Int?, b: Int): Int =
        a?.let { maxOf(it, b) } ?: b
}
