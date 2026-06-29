package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class OBDDataReader(
    private val client: OBDClient,
    private val sessionManager: OBDSessionManager,
) {
    data class PollResult(
        val request: OBDRequest,
        val response: OBDResponse,
    )

    fun telemetryFlow(
        rpmPeriodMs: Long,
        coolantPeriodMs: Long,
    ): Flow<PollResult> {
        require(rpmPeriodMs > 0)
        require(coolantPeriodMs > 0)

        return sessionManager.sessionState.flatMapLatest { state ->
            if (state is OBDSessionState.Ready) {
                pollingFlow(rpmPeriodMs, coolantPeriodMs)
            } else {
                emptyFlow()
            }
        }
    }

    private fun pollingFlow(rpmPeriodMs: Long, coolantPeriodMs: Long): Flow<PollResult> = flow {
        var nextRpmAtMs = monotonicTimeMs()
        var nextCoolantAtMs = nextRpmAtMs

        while (currentCoroutineContext().isActive) {
            val capabilities = sessionManager.capabilities.value
            val nowMs = monotonicTimeMs()

            if (nowMs < nextRpmAtMs) delay(nextRpmAtMs - nowMs)

            if (capabilities.supports(ENGINE_RPM_PID)) {
                emit(
                    PollResult(
                        OBDRequest.EngineRpm,
                        safeRequest(
                            OBDRequest.EngineRpm,
                            capabilities.preferredEcuFor(ENGINE_RPM_PID),
                        ),
                    ),
                )
            }
            nextRpmAtMs += rpmPeriodMs

            val afterRpmMs = monotonicTimeMs()
            if (afterRpmMs >= nextCoolantAtMs) {
                if (capabilities.supports(COOLANT_TEMPERATURE_PID)) {
                    emit(
                        PollResult(
                            OBDRequest.EngineCoolantTemperature,
                            safeRequest(
                                OBDRequest.EngineCoolantTemperature,
                                capabilities.preferredEcuFor(COOLANT_TEMPERATURE_PID),
                            ),
                        ),
                    )
                }
                nextCoolantAtMs = afterRpmMs + coolantPeriodMs
            }

            val completedAtMs = monotonicTimeMs()
            if (nextRpmAtMs <= completedAtMs) {
                nextRpmAtMs = completedAtMs + rpmPeriodMs
            }
        }
    }

    private suspend fun safeRequest(request: OBDRequest, preferredEcu: String?): OBDResponse = try {
        client.requestObd(request, preferredEcu).also { response ->
            if (response is OBDResponse.Data) sessionManager.onValidObdData()
        }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        sessionManager.requestReconnect(t)
        OBDResponse.NoData.Error("", t)
    }

    private fun monotonicTimeMs(): Long = System.nanoTime() / 1_000_000

    private companion object {
        const val COOLANT_TEMPERATURE_PID = 0x05
        const val ENGINE_RPM_PID = 0x0C
    }
}
