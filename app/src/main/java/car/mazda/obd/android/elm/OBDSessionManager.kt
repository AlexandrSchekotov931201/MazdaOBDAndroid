package car.mazda.obd.android.elm

import car.mazda.obd.android.elm.exception.AdapterUnreachableException
import car.mazda.obd.android.elm.exception.LostConnectionException
import car.mazda.obd.android.elm.exception.NetworkUnavailableException
import car.mazda.obd.android.logs.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class OBDSessionManager(
    private val client: OBDClient,
) {
    private val _sessionState = MutableStateFlow<OBDSessionState>(OBDSessionState.Idle)
    val sessionState: StateFlow<OBDSessionState> = _sessionState

    private val reconnectMutex = kotlinx.coroutines.sync.Mutex()
    private var reconnectInProgress = false

    suspend fun startSession() {
        try {
            _sessionState.value = OBDSessionState.ConnectingSocket
            client.connect()

            _sessionState.value = OBDSessionState.InitializingEcu
            client.initializingEcu()

            _sessionState.value = OBDSessionState.Ready
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppLogger.log("startSession error: ${t::class.simpleName}: ${t.message}")
            client.release()
            _sessionState.value = OBDSessionState.Error(t)
            throw t
        }
    }

    suspend fun stopSession() {
        client.release()
        _sessionState.value = OBDSessionState.Idle
    }

    suspend fun requestReconnect(t: Throwable) {
        AppLogger.log("Try reconnect")
        if (t is CancellationException) throw t
        if (!t.isReconnectable()) return

        val acquired = reconnectMutex.withLock {
            if (reconnectInProgress) return@withLock false
            reconnectInProgress = true
            true
        }
        if (!acquired) return

        try {
            val delayMs = 5000L

            while (true) {
                // чистим старое
                stopSession()

                val result = runCatching { startSession() }
                if (result.isSuccess) {
                    AppLogger.log("Reconnect success")
                    return
                }

                val err = result.exceptionOrNull()
                if (err is CancellationException) throw err

                // если ошибка уже не “сетевого” класса — прекращаем цикл
                if (err == null || !err.isReconnectable()) return

                // маленький backoff, чтобы не долбиться
                delay(delayMs)
                AppLogger.log("Continue reconnect")
            }
        } finally {
            reconnectMutex.withLock { reconnectInProgress = false }
        }
    }

    private fun Throwable.isReconnectable(): Boolean =
        this is LostConnectionException ||
                this is NetworkUnavailableException ||
                this is AdapterUnreachableException
}
