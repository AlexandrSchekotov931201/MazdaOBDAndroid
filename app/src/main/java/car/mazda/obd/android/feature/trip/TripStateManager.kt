package car.mazda.obd.android.feature.trip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TripStateManager {
    private val _tripState = MutableStateFlow<TripState>(TripState.Idle)
    val tripState: StateFlow<TripState> = _tripState

    fun startTrip() {
        if (_tripState.value is TripState.Idle) _tripState.value = TripState.Active
    }

    fun stopTrip() {
        if (_tripState.value is TripState.Active) _tripState.value = TripState.Idle
    }
}
