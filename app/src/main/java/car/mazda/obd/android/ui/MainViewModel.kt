package car.mazda.obd.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import car.mazda.obd.android.elm.OBDClient
import car.mazda.obd.android.elm.OBDDataReader
import car.mazda.obd.android.elm.OBDSessionManager
import car.mazda.obd.android.elm.OBDSessionState
import car.mazda.obd.android.logs.AppLogger
import car.mazda.obd.android.ui.command.MainViewCommand
import car.mazda.obd.android.ui.mapper.MainViewMapper
import car.mazda.obd.android.ui.trip.EngineRpmSample
import car.mazda.obd.android.ui.trip.TripState
import car.mazda.obd.android.ui.trip.TripStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainViewModel : ViewModel() {

    private val client = OBDClient()
    private val sessionManager = OBDSessionManager(client, viewModelScope)
    private val dataReader = OBDDataReader(
        client = client,
        sessionManager = sessionManager
    )

    private val viewMapper = MainViewMapper()
    private val tripStateManager = TripStateManager(viewModelScope)

    private val _mainViewCommands = MutableSharedFlow<MainViewCommand>()
    val mainViewCommands: SharedFlow<MainViewCommand> = _mainViewCommands

    private val _connectionTextState = MutableStateFlow("Connecting to adapter...")
    val connectionTextState: StateFlow<String> = _connectionTextState

    private val _rpmState = MutableStateFlow(0)
    val rpmState: StateFlow<Int> = _rpmState

    private var started = false

    override fun onCleared() {
        runBlocking(Dispatchers.IO + NonCancellable) {
            sessionManager.stopSession()
        }
        super.onCleared()
    }

    fun onCreate() {
        if (started) return
        started = true

        observeSessionState()
        observeEngineRpmState()
        observeTripState()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { sessionManager.startSession() }
                .exceptionOrNull()
                ?.let { sessionManager.requestReconnect(it) }
        }
    }

    private fun observeSessionState() {
        viewModelScope.launch(Dispatchers.IO) {
            sessionManager.sessionState
                .map { state ->
                    when (state) {
                        is OBDSessionState.Idle,
                        is OBDSessionState.ConnectingSocket -> "Connecting to socket..."

                        is OBDSessionState.InitializingEcu -> "Initializing ECU..."
                        is OBDSessionState.Ready -> "Ready"
                        is OBDSessionState.Error -> {
                            "Connection error: ${state.throwable.message ?: state.throwable.toString()}"
                        }
                    }
                }
                .distinctUntilChanged()
                .collect {
                    _connectionTextState.value = it
                }
        }
    }

    private fun observeTripState() {
        viewModelScope.launch(Dispatchers.IO) {
            var previousState: TripState = TripState.Idle

            tripStateManager.tripState
                .collect { state ->
                    when (state) {
                        is TripState.Active -> {
                            if (previousState is TripState.Idle) {
                                AppLogger.log("Play greeting sound")
                                _mainViewCommands.emit(MainViewCommand.SoundGreeting)
                            }
                        }
                        is TripState.Idle -> {
                            if (previousState is TripState.Finishing) {
                                AppLogger.log("Play goodbye sound")
                                _mainViewCommands.emit(MainViewCommand.SoundGoodbye)
                            }
                        }
                        is TripState.Finishing -> Unit
                    }
                    previousState = state
                }
        }
    }

    private fun observeEngineRpmState() {
        viewModelScope.launch(Dispatchers.IO) {
            dataReader.rpmFlow(periodMs = 100)
                .map(viewMapper::mapEngineRpm)
                .distinctUntilChanged()
                .catch { t ->
                    AppLogger.log("rpmFlow error: ${t.message}")
                }
                .collect { sample ->
                    _rpmState.value = sample.displayRpm()
                    tripStateManager.onRpmSample(sample)
                }
        }
    }

    private fun EngineRpmSample.displayRpm(): Int =
        when (this) {
            is EngineRpmSample.Value -> rpm
            is EngineRpmSample.NoData,
            is EngineRpmSample.ConnectionError -> 0
        }
}
