package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.elm.exception.AdapterUnreachableException
import car.mazda.obd.android.core.elm.exception.LostConnectionException
import car.mazda.obd.android.core.elm.exception.NetworkUnavailableException
import car.mazda.obd.android.feature.monitor.ObdConnectionSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class OBDDataReader(
    private val client: OBDClient,
    private val sessionManager: OBDSessionManager,
    private val settings: ObdConnectionSettings = ObdConnectionSettings.Default,
) {
    private val reconnectStateLock = Any()
    private var consecutiveAdapterTimeouts = 0

    fun rpmFlow(periodMs: Long): Flow<OBDResponse> =
        requestFlow(periodMs, OBDRequest.EngineRpm)

    fun coolantTemperatureFlow(periodMs: Long): Flow<OBDResponse> =
        requestFlow(periodMs, OBDRequest.EngineCoolantTemperature)

    private fun requestFlow(periodMs: Long, request: OBDRequest): Flow<OBDResponse> =
        sessionManager.sessionState
            .flatMapLatest { state ->
                if (state is OBDSessionState.Ready) {
                    tickerFlow(periodMs).map { safeRequest(request) }
                } else {
                    emptyFlow()
                }
            }

    private suspend fun safeRequest(request: OBDRequest): OBDResponse {
        return try {
            client.requestObd(request).also { resetReconnectState() }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (shouldReconnect(t)) {
                sessionManager.requestReconnect(t)
            }
            OBDResponse.NoData.Error("", t)
        }
    }

    private fun shouldReconnect(t: Throwable): Boolean =
        when (t) {
            is AdapterUnreachableException -> registerAdapterTimeout()
            is LostConnectionException,
            is NetworkUnavailableException -> true
            else -> false
        }

    private fun registerAdapterTimeout(): Boolean =
        synchronized(reconnectStateLock) {
            consecutiveAdapterTimeouts += 1
            consecutiveAdapterTimeouts >= settings.adapterTimeoutReconnectThreshold
        }

    private fun resetReconnectState() {
        synchronized(reconnectStateLock) {
            consecutiveAdapterTimeouts = 0
        }
    }

    private fun tickerFlow(periodMs: Long): Flow<Unit> = flow {
        while (currentCoroutineContext().isActive) {
            emit(Unit)
            delay(periodMs)
        }
    }
}

