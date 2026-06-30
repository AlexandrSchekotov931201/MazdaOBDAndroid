package car.mazda.obd.android.core.telemetry

import car.mazda.obd.android.core.elm.OBDClient
import car.mazda.obd.android.core.elm.OBDSessionManager
import car.mazda.obd.android.core.elm.OBDSessionState
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
class TelemetryPollingEngine(
    private val client: OBDClient,
    private val sessionManager: OBDSessionManager,
) {
    data class PollResult(
        val target: PollingTarget,
        val response: OBDResponse,
    )

    fun telemetryFlow(targets: List<PollingTarget>): Flow<PollResult> {
        require(targets.isNotEmpty())
        require(targets.map { it.metric }.distinct().size == targets.size) {
            "Polling targets must have unique metrics"
        }

        return sessionManager.sessionState.flatMapLatest { state ->
            if (state is OBDSessionState.Ready) pollingFlow(targets) else emptyFlow()
        }
    }

    private fun pollingFlow(targets: List<PollingTarget>): Flow<PollResult> = flow {
        val startedAtMs = monotonicTimeMs()
        val nextPollAtMs = targets.associateWithTo(linkedMapOf()) { startedAtMs }

        while (currentCoroutineContext().isActive) {
            val target = targets.minBy { nextPollAtMs.getValue(it) }
            val scheduledAtMs = nextPollAtMs.getValue(target)
            val waitMs = scheduledAtMs - monotonicTimeMs()
            if (waitMs > 0) delay(waitMs)

            val capabilities = sessionManager.capabilities.value
            if (capabilities.supports(target.request.pidCode)) {
                emit(PollResult(target, safeRequest(target, capabilities.preferredEcuFor(target.request.pidCode))))
            }

            val completedAtMs = monotonicTimeMs()
            val nextScheduledAtMs = scheduledAtMs + target.periodMs
            nextPollAtMs[target] = if (nextScheduledAtMs <= completedAtMs) {
                completedAtMs + target.periodMs
            } else {
                nextScheduledAtMs
            }
        }
    }

    private suspend fun safeRequest(target: PollingTarget, preferredEcu: String?): OBDResponse = try {
        client.requestObd(target.request, preferredEcu).also { response ->
            if (response is OBDResponse.Data) sessionManager.onValidObdData()
        }
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        sessionManager.requestReconnect(t)
        OBDResponse.NoData.Error("", t)
    }

    private fun monotonicTimeMs(): Long = System.nanoTime() / 1_000_000
}
