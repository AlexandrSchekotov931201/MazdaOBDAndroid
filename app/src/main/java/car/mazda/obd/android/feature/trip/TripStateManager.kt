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
    private val connectionLostDelayMs: Long = 10000L,
) {
    private val _tripState = MutableStateFlow<TripState>(TripState.Idle)
    val tripState: StateFlow<TripState> = _tripState

    private var finishJob: Job? = null
    private var rpmUnavailableJob: Job? = null

    fun onRpmSample(sample: EngineRpmSample) {
        when (sample) {
            is EngineRpmSample.Value -> {
                cancelRpmUnavailableCandidate()
                if (sample.rpm > 0) {
                    onEngineRunning(sample.rpm)
                } else {
                    onEngineStoppedCandidate()
                }
            }
            is EngineRpmSample.NoData -> onRpmUnavailableCandidate()
            is EngineRpmSample.ConnectionError -> onConnectionProblemCandidate(sample.throwable)
        }
    }

    private fun onConnectionProblemCandidate(t: Throwable) {
        if (_tripState.value !is TripState.Active) return
        if (finishJob?.isActive == true || rpmUnavailableJob?.isActive == true) return

        AppLogger.handledError(
            "Handled RPM connection problem; keeping trip active while waiting for recovery (${t::class.simpleName})"
        )
        rpmUnavailableJob = scope.launch {
            delay(connectionLostDelayMs)
            onConnectionProblem(t)
        }
    }

    private fun onConnectionProblem(t: Throwable) {
        if (_tripState.value !is TripState.Active) return
        if (finishJob?.isActive == true) return

        _tripState.value = TripState.Finishing
        AppLogger.log("Trip finish candidate: connection problem (${t::class.simpleName})")
        finishJob = scope.launch {
            delay(connectionLostDelayMs)
            _tripState.value = TripState.Idle
            AppLogger.log("Trip finished")
        }
    }

    private fun onRpmUnavailableCandidate() {
        if (_tripState.value !is TripState.Active) return
        if (finishJob?.isActive == true || rpmUnavailableJob?.isActive == true) return

        AppLogger.handledError("Handled missing RPM sample; keeping trip active while waiting for recovery")
        rpmUnavailableJob = scope.launch {
            delay(connectionLostDelayMs)
            if (_tripState.value !is TripState.Active) return@launch

            _tripState.value = TripState.Finishing
            AppLogger.log("Trip finish candidate: RPM data unavailable")
            finishJob = scope.launch {
                delay(engineOffDelayMs)
                _tripState.value = TripState.Idle
                AppLogger.log("Trip finished")
            }
        }
    }

    private fun cancelRpmUnavailableCandidate() {
        rpmUnavailableJob?.cancel()
        rpmUnavailableJob = null
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
