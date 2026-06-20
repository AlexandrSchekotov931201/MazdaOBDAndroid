package car.mazda.obd.android.ui.trip

import car.mazda.obd.android.logs.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TripStateManager(
    private val scope: CoroutineScope,
    private val engineOffDelayMs: Long = 5000L,
) {
    private val _tripState = MutableStateFlow<TripState>(TripState.Idle)
    val tripState: StateFlow<TripState> = _tripState

    private var finishJob: Job? = null

    fun onRpmChanged(rpm: Int) {
        if (rpm > 0) {
            onEngineRunning(rpm)
        } else {
            onEngineStoppedCandidate()
        }
    }

    private fun onEngineRunning(rpm: Int) {
        finishJob?.cancel()
        finishJob = null

        when (_tripState.value) {
            is TripState.Active -> Unit
            is TripState.Finishing -> {
                _tripState.value = TripState.Active
                AppLogger.log("Trip resumed, rpm=$rpm")
            }
            is TripState.Idle -> {
                _tripState.value = TripState.Active
                AppLogger.log("Trip started, rpm=$rpm")
            }
        }
    }

    private fun onEngineStoppedCandidate() {
        if (_tripState.value !is TripState.Active) return
        if (finishJob?.isActive == true) return

        _tripState.value = TripState.Finishing
        AppLogger.log("Trip finish candidate")
        finishJob = scope.launch {
            delay(engineOffDelayMs)
            _tripState.value = TripState.Idle
            AppLogger.log("Trip finished")
        }
    }
}
