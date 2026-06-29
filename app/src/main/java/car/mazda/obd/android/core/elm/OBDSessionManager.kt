package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.exception.AdapterUnreachableException
import car.mazda.obd.android.core.elm.exception.LostConnectionException
import car.mazda.obd.android.core.elm.exception.NetworkUnavailableException
import car.mazda.obd.android.core.logs.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class OBDSessionManager(
    private val client: OBDClient,
    private val scope: CoroutineScope,
) {
    private companion object {
        const val RECONNECT_DELAY_MS = 5000L
    }

    private val _sessionState = MutableStateFlow<OBDSessionState>(OBDSessionState.Idle)
    val sessionState: StateFlow<OBDSessionState> = _sessionState
    private val _capabilities = MutableStateFlow(VehicleCapabilities.Unknown)
    val capabilities: StateFlow<VehicleCapabilities> = _capabilities

    private val reconnectLock = Any()
    private var reconnectJob: Job? = null

    suspend fun startSession() {
        try {
            AppLogger.log("Connecting to OBD adapter")
            _sessionState.value = OBDSessionState.ConnectingSocket
            client.connect()

            AppLogger.log("Initializing ECU")
            _sessionState.value = OBDSessionState.InitializingEcu
            client.initializingEcu()

            AppLogger.log("Discovering standard OBD-II capabilities")
            _capabilities.value = client.discoverCapabilities()

            AppLogger.log("OBD session ready")
            _sessionState.value = OBDSessionState.Ready
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppLogger.log("startSession error: ${t::class.simpleName}: ${t.message}")
            client.release()
            _sessionState.value = OBDSessionState.Error(t)
            throw t
        }
    }

    suspend fun stopSession(cancelReconnect: Boolean = true) {
        if (cancelReconnect) {
            synchronized(reconnectLock) {
                reconnectJob?.cancel()
                reconnectJob = null
            }
        }
        client.release()
        _capabilities.value = VehicleCapabilities.Unknown
        _sessionState.value = OBDSessionState.Idle
    }

    fun requestReconnect(t: Throwable) {
        if (t is CancellationException) return
        if (!t.isReconnectable()) return

        synchronized(reconnectLock) {
            if (reconnectJob?.isActive == true) return

            reconnectJob = scope.launch(Dispatchers.IO) {
                reconnectLoop()
            }
        }
    }

    private suspend fun reconnectLoop() {
        AppLogger.log("Try reconnect")
        try {
            while (coroutineContext.isActive) {
                stopSession(cancelReconnect = false)

                val result = runCatching { startSession() }
                if (result.isSuccess) {
                    AppLogger.log("Reconnect success")
                    return
                }

                val err = result.exceptionOrNull()
                if (err is CancellationException) throw err
                if (err == null || !err.isReconnectable()) return

                delay(RECONNECT_DELAY_MS)
                AppLogger.log("Continue reconnect")
            }
        } finally {
            val currentJob = coroutineContext[Job]
            synchronized(reconnectLock) {
                if (reconnectJob == currentJob) reconnectJob = null
            }
        }
    }

    private fun Throwable.isReconnectable(): Boolean =
        this is LostConnectionException ||
                this is NetworkUnavailableException ||
                this is AdapterUnreachableException
}
