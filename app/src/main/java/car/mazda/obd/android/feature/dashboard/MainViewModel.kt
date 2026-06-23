package car.mazda.obd.android.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import car.mazda.obd.android.core.elm.OBDClient
import car.mazda.obd.android.core.elm.OBDDataReader
import car.mazda.obd.android.core.elm.OBDSessionManager
import car.mazda.obd.android.core.elm.OBDSessionState
import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.feature.dashboard.command.MainViewCommand
import car.mazda.obd.android.feature.dashboard.mapper.MainViewMapper
import car.mazda.obd.android.feature.trip.EngineRpmSample
import car.mazda.obd.android.feature.trip.TripState
import car.mazda.obd.android.feature.trip.TripStateManager
import car.mazda.obd.android.feature.trip.summary.ActiveTripSummary
import car.mazda.obd.android.feature.trip.summary.TripSummary
import car.mazda.obd.android.feature.trip.summary.TripSummaryRepository
import car.mazda.obd.android.feature.trip.summary.TripSummaryTracker
import car.mazda.obd.android.feature.warmup.EngineTemperatureSample
import car.mazda.obd.android.feature.warmup.WarmupWarning
import car.mazda.obd.android.feature.warmup.WarmupWarningManager
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

class MainViewModel(
    private val tripSummaryRepository: TripSummaryRepository,
) : ViewModel() {

    private val client = OBDClient()
    private val sessionManager = OBDSessionManager(client, viewModelScope)
    private val dataReader = OBDDataReader(
        client = client,
        sessionManager = sessionManager
    )

    private val viewMapper = MainViewMapper()
    private val tripStateManager = TripStateManager(viewModelScope)
    private val warmupWarningManager = WarmupWarningManager()
    private val tripSummaryTracker = TripSummaryTracker()

    private val _mainViewCommands = MutableSharedFlow<MainViewCommand>(extraBufferCapacity = 8)
    val mainViewCommands: SharedFlow<MainViewCommand> = _mainViewCommands

    private val _connectionTextState = MutableStateFlow("Connecting to adapter...")
    val connectionTextState: StateFlow<String> = _connectionTextState

    private val _rpmState = MutableStateFlow(0)
    val rpmState: StateFlow<Int> = _rpmState

    private val _oilTempState = MutableStateFlow<Int?>(null)
    val oilTempState: StateFlow<Int?> = _oilTempState

    private val _warmupTextState = MutableStateFlow("Oil temp: --")
    val warmupTextState: StateFlow<String> = _warmupTextState

    val activeTripSummaryState: StateFlow<ActiveTripSummary?> = tripSummaryTracker.activeTrip
    val recentTripSummariesState: StateFlow<List<TripSummary>> = tripSummaryRepository.recentTrips

    private var latestRpm = 0
    private var latestOilTemp: Int? = null

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
        observeOilTemperatureState()
        observeTripState()
        viewModelScope.launch {
            tripSummaryRepository.refreshRecentTrips()
        }
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
                    tripSummaryTracker.onTripStateChanged(state)?.let { summary ->
                        tripSummaryRepository.saveTrip(summary)
                    }

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
                .catch { t ->
                    AppLogger.log("rpmFlow error: ${t.message}")
                }
                .collect { sample ->
                    latestRpm = sample.displayRpm()
                    _rpmState.value = latestRpm
                    tripStateManager.onRpmSample(sample)
                    tripSummaryTracker.onTripStateChanged(tripStateManager.tripState.value)
                    tripSummaryTracker.onRpmChanged(latestRpm)
                    checkWarmupWarning()
                }
        }
    }

    private fun observeOilTemperatureState() {
        viewModelScope.launch(Dispatchers.IO) {
            dataReader.oilTemperatureFlow(periodMs = 1_000)
                .map(viewMapper::mapEngineOilTemperature)
                .catch { t ->
                    AppLogger.log("oilTemperatureFlow error: ${t.message}")
                }
                .collect { sample ->
                    latestOilTemp = sample.displayTemperature()
                    _oilTempState.value = latestOilTemp
                    tripSummaryTracker.onEngineTemperatureChanged(latestOilTemp)
                    _warmupTextState.value = sample.warmupText()
                    checkWarmupWarning()
                }
        }
    }

    private suspend fun checkWarmupWarning() {
        val warning = warmupWarningManager.onEngineData(latestRpm, latestOilTemp)

        when (warning) {
            is WarmupWarning.HighRpmWhileCold -> {
                AppLogger.log("Play warmup warning sound")
                _mainViewCommands.emit(MainViewCommand.SoundWarmupWarning)
            }
            is WarmupWarning.Overheat -> {
                AppLogger.log("Play overheat warning sound")
                _mainViewCommands.emit(MainViewCommand.SoundOverheatWarning)
            }
            null -> Unit
        }
    }

    private fun EngineRpmSample.displayRpm(): Int =
        when (this) {
            is EngineRpmSample.Value -> rpm
            is EngineRpmSample.NoData,
            is EngineRpmSample.ConnectionError -> 0
        }

    private fun EngineTemperatureSample.displayTemperature(): Int? =
        when (this) {
            is EngineTemperatureSample.Value -> celsius
            is EngineTemperatureSample.NoData,
            is EngineTemperatureSample.ConnectionError -> null
        }

    private fun EngineTemperatureSample.warmupText(): String =
        when (this) {
            is EngineTemperatureSample.Value -> {
                val status = when {
                    celsius >= 105 -> "Critical temperature"
                    celsius >= 75 -> "Engine warm"
                    else -> "Engine warming up"
                }
                "Oil temp: ${celsius}C - $status"
            }
            is EngineTemperatureSample.NoData -> "Oil temp: --"
            is EngineTemperatureSample.ConnectionError -> "Oil temp: connection error"
        }
}
