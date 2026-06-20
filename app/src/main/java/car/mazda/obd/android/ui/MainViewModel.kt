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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel : ViewModel() {

    private val client = OBDClient()
    private val sessionManager = OBDSessionManager(client)
    private val dataReader = OBDDataReader(
        client = client,
        sessionManager = sessionManager
    )

    private val viewMapper = MainViewMapper()

    private val _mainViewCommands = MutableSharedFlow<MainViewCommand>()
    val mainViewCommands: SharedFlow<MainViewCommand> = _mainViewCommands

    private val _connectionTextState = MutableStateFlow("Подключение к адаптеру...")
    val connectionTextState: StateFlow<String> = _connectionTextState

    private val _rpmState = MutableStateFlow(0)
    val rpmState: StateFlow<Int> = _rpmState

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) {
            sessionManager.stopSession()
        }
    }

    fun onCreate() {
        observeSessionState()
        observeEngineRpmState()
        playGreeting()
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
                        is OBDSessionState.ConnectingSocket -> "Подключение к сокету..."

                        is OBDSessionState.InitializingEcu -> "Подключение к ЭБУ..."
                        is OBDSessionState.Ready -> "Готово"
                        is OBDSessionState.Error -> {
                            "Ошибка подключения: ${state.throwable.message ?: state.throwable.toString()}"
                        }
                    }
                }
                .distinctUntilChanged()
                .collect {
                    _connectionTextState.value = it
                }
        }
    }

    private fun playGreeting() {
        viewModelScope.launch(Dispatchers.IO) {
            sessionManager.sessionState
                .filter { it is OBDSessionState.Ready }
                .take(1)
                .collect {
                    _mainViewCommands.emit(MainViewCommand.SoundGreeting)
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
                .collect { _rpmState.value = it }
        }
    }
}
