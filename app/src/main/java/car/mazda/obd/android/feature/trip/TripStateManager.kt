package car.mazda.obd.android.feature.trip

import car.mazda.obd.android.core.logs.AppLogger
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

    fun onRpmSample(sample: EngineRpmSample) {
        when (sample) {
            is EngineRpmSample.Value -> {
                if (sample.rpm > 0) {
                    onEngineRunning(sample.rpm)
                } else {
                    onEngineStoppedCandidate()
                }
            }
            is EngineRpmSample.NoData -> onRpmUnavailableCandidate()
            is EngineRpmSample.ConnectionError -> onRpmUnavailableCandidate(sample.throwable)
        }
    }

    private fun onRpmUnavailableCandidate(t: Throwable? = null) {
        if (_tripState.value !is TripState.Active) return
        if (finishJob?.isActive == true) return

        val reason = t?.let { "connection problem (${it::class.simpleName})" } ?: "RPM data unavailable"
        val handledMessage = t?.let {
            "Handled RPM connection problem; finishing trip unless RPM recovers (${it::class.simpleName})"
        } ?: "Handled missing RPM sample; finishing trip unless RPM recovers"

        AppLogger.handledError(handledMessage)
        _tripState.value = TripState.Finishing
        AppLogger.log("Trip finish candidate: $reason")
        finishJob = scope.launch {
            delay(engineOffDelayMs)
            _tripState.value = TripState.Idle
            AppLogger.log("Trip finished")
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
        AppLogger.log("Trip finish candidate: engine stopped")
        finishJob = scope.launch {
            delay(engineOffDelayMs)
            _tripState.value = TripState.Idle
            AppLogger.log("Trip finished")
        }
    }
}
