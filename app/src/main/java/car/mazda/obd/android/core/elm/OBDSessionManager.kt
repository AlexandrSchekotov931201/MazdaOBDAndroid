package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.exception.AdapterUnreachableException
import car.mazda.obd.android.core.elm.exception.ElmPromptTimeoutException
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
    private val requiredPids: Set<Int>,
) {
    private companion object {
        const val RECONNECT_DELAY_MS = 5000L
        const val FAILURES_BEFORE_REDISCOVERY = 3
    }

    private val _sessionState = MutableStateFlow<OBDSessionState>(OBDSessionState.Idle)
    val sessionState: StateFlow<OBDSessionState> = _sessionState
    private val _capabilities = MutableStateFlow(VehicleCapabilities.Unknown)
    val capabilities: StateFlow<VehicleCapabilities> = _capabilities

    private val reconnectLock = Any()
    private var reconnectJob: Job? = null
    private var capabilityDiscoveryAttempted = false
    private var reconnectsWithoutValidData = 0

    suspend fun startSession() {
        try {
            AppLogger.log("Connecting to OBD adapter")
            _sessionState.value = OBDSessionState.ConnectingSocket
            client.connect()

            AppLogger.log("Initializing ECU")
            _sessionState.value = OBDSessionState.InitializingEcu
            client.initializingEcu()

            if (!capabilityDiscoveryAttempted) {
                AppLogger.log("Discovering capabilities for active standard OBD-II PIDs")
                _capabilities.value = client.discoverCapabilities(requiredPids)
                capabilityDiscoveryAttempted = true
            } else {
                AppLogger.log("Reusing cached OBD-II capabilities after transport reconnect")
            }

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
        if (cancelReconnect) {
            _capabilities.value = VehicleCapabilities.Unknown
            capabilityDiscoveryAttempted = false
            reconnectsWithoutValidData = 0
        }
        _sessionState.value = OBDSessionState.Idle
    }

    fun requestReconnect(t: Throwable) {
        if (t is CancellationException) return
        if (!t.isReconnectable()) return

        synchronized(reconnectLock) {
            if (reconnectJob?.isActive == true) return

            reconnectsWithoutValidData++
            if (reconnectsWithoutValidData >= FAILURES_BEFORE_REDISCOVERY) {
                AppLogger.log("Invalidating cached OBD-II capabilities after repeated reconnects without valid data")
                _capabilities.value = VehicleCapabilities.Unknown
                capabilityDiscoveryAttempted = false
                reconnectsWithoutValidData = 0
            }

            reconnectJob = scope.launch(Dispatchers.IO) {
                reconnectLoop()
            }
        }
    }

    fun onValidObdData() {
        synchronized(reconnectLock) {
            reconnectsWithoutValidData = 0
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
                this is AdapterUnreachableException ||
                this is ElmPromptTimeoutException
}
