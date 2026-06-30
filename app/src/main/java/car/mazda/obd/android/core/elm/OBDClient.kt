package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.CanIds
import car.mazda.obd.android.core.elm.entity.ElmCommand
import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.elm.entity.SupportedPidRange
import car.mazda.obd.android.core.elm.exception.ElmPromptTimeoutException
import car.mazda.obd.android.core.elm.exception.ProtocolException
import car.mazda.obd.android.core.elm.mapper.OBDDataMapper
import car.mazda.obd.android.core.elm.transport.ElmTransport
import car.mazda.obd.android.core.elm.transport.ElmTransportReadTimeoutException
import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.core.logs.AppLogger.Direction
import car.mazda.obd.android.core.logs.AppLogger.Layer
import car.mazda.obd.android.core.logs.AppLogger.Level
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OBDClient(
    private val transport: ElmTransport,
) {

    private companion object {
        private const val INITIAL_READ_TIMEOUT_MS = 15_000
        private const val NORMAL_READ_TIMEOUT_MS = 5_000
    }

    private var protocolEstablished = false

    private val dataMapper = OBDDataMapper()

    private val mutex = Mutex()

    suspend fun connect() = mutex.withLock {
        AppLogger.event(layer = Layer.Network, operation = "connect", message = "Connecting to ELM transport")
        try {
            unlockedRelease()
            transport.connect()
            AppLogger.event(layer = Layer.Network, operation = "connect", message = "ELM transport connected")
        } catch (e: Throwable) {
            logConnectionFailure(e)
            unlockedRelease()
            throw e
        }
    }

    suspend fun initializingEcu() = mutex.withLock {
        for (cmd in ElmCommand.defaultInitSequence) {
            unlockedRequestElm(cmd)
        }
    }

    suspend fun requestObd(
        request: OBDRequest,
        preferredEcu: String? = null,
    ): OBDResponse = mutex.withLock {
        val firstResponse = unlockedRequestObd(request, preferredEcu)
        if (firstResponse !is OBDResponse.NoData.Mismatched) return@withLock firstResponse

        AppLogger.event(
            level = Level.HandledError,
            layer = Layer.Parser,
            operation = request.value.operationName(),
            message = "Discarding stale/mismatched response and retrying request once",
            raw = firstResponse.raw,
        )
        unlockedRequestObd(request, preferredEcu)
    }

    suspend fun discoverCapabilities(requiredPids: Set<Int>): VehicleCapabilities = mutex.withLock {
        val discoveredRanges = mutableSetOf<SupportedPidRange>()
        val supportedByEcu = mutableMapOf<String, MutableSet<Int>>()

        val requiredRanges = requiredPids.map(SupportedPidRange::containing)
            .distinct()
            .sortedBy { it.basePid }
        for (range in requiredRanges) {
            val response = unlockedRequestObd(OBDRequest.SupportedPids(range))
            if (response !is OBDResponse.Data) continue

            SupportedPidDecoder.decodeByEcu(range, response.data).forEach { (ecu, pids) ->
                supportedByEcu.getOrPut(ecu) { mutableSetOf() } += pids
            }
            discoveredRanges += range
        }

        VehicleCapabilities(
            discoveredRanges = discoveredRanges,
            supportedPidsByEcu = supportedByEcu.mapValues { it.value.toSet() },
        )
    }

    private suspend fun unlockedRequestElm(cmd: ElmCommand): OBDResponse =
        unlockedRequest(cmd.value)

    private suspend fun unlockedRequestObd(
        req: OBDRequest,
        preferredEcu: String? = null,
    ): OBDResponse {
        val response = unlockedRequest(req.value)
        if (response is OBDResponse.Data) {
            if (!protocolEstablished) {
                protocolEstablished = true
            }

            return OBDResponseCorrelator.correlate(response, req, preferredEcu)
        }
        return response
    }

    private suspend fun unlockedRequest(request: String): OBDResponse {
        val exchangeId = AppLogger.newExchangeId()
        val startedAtNs = System.nanoTime()
        val isElmCommand = request.startsWith("AT", ignoreCase = true)
        val layer = if (isElmCommand) Layer.Elm else Layer.Obd
        AppLogger.event(
            layer = layer,
            direction = Direction.Tx,
            operation = request.operationName(),
            message = "Sending command",
            exchangeId = exchangeId,
            raw = "$request\r",
        )

        val timeoutMs = if (protocolEstablished) NORMAL_READ_TIMEOUT_MS else INITIAL_READ_TIMEOUT_MS
        val raw = try {
            transport.exchange(request, timeoutMs)
        } catch (e: ElmTransportReadTimeoutException) {
            val containedObdData = dataMapper.map(e.partialRaw) is OBDResponse.Data
            val message = if (containedObdData) {
                "ELM returned OBD frames without the terminating prompt; reconnect required"
            } else {
                "Timed out waiting for ELM prompt"
            }
            AppLogger.event(Level.Error, Layer.Network, Direction.Rx, "transport-read", message, exchangeId, e.partialRaw, e)
            throw ElmPromptTimeoutException(e.partialRaw, containedObdData, e)
        }
        val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000
        AppLogger.event(
            layer = layer,
            direction = Direction.Rx,
            operation = request.operationName(),
            message = "Received ELM response in ${elapsedMs}ms",
            exchangeId = exchangeId,
            raw = raw,
        )

        if (isElmCommand) {
            AppLogger.event(
                layer = Layer.Elm,
                operation = request.operationName(),
                message = "Adapter command completed",
                exchangeId = exchangeId,
            )
            return dataMapper.map(raw)
        }

        return try {
            dataMapper.map(raw).also { response ->
                AppLogger.event(
                    level = response.logLevel(),
                    layer = Layer.Parser,
                    operation = request.operationName(),
                    message = response.diagnosticSummary(),
                    exchangeId = exchangeId,
                    raw = raw.takeIf { response !is OBDResponse.Data },
                    throwable = (response as? OBDResponse.NoData.Error)?.throwable,
                )
            }
        } catch (e: Throwable) {
            AppLogger.event(Level.Error, Layer.Parser, operation = request.operationName(), message = "Parser threw an exception", exchangeId = exchangeId, raw = raw, throwable = e)
            throw ProtocolException("Failed to parse response for request=$request", e)
        }
    }

    suspend fun release() = mutex.withLock { unlockedRelease() }

    private fun unlockedRelease() {
        transport.disconnect()
        protocolEstablished = false
    }

    private fun logConnectionFailure(t: Throwable) {
        AppLogger.event(Level.Error, Layer.Network, operation = "connect", message = "Connection failed", throwable = t)
    }

    private fun String.operationName(): String = when (uppercase()) {
        "010C" -> "engine-rpm"
        "0105" -> "coolant-temperature"
        "ATZ" -> "adapter-reset"
        "ATE0" -> "echo-off"
        "ATL0" -> "linefeeds-off"
        "ATS1" -> "spaces-on"
        "ATH1" -> "headers-on"
        "ATAT1" -> "adaptive-timing"
        "ATSP0" -> "auto-protocol"
        "ATSH${CanIds.ENGINE_ECU_REQUEST}" -> "engine-ecu-header"
        "ATI" -> "adapter-identification"
        "AT@1" -> "adapter-description"
        "ATRV" -> "adapter-voltage"
        "ATDP" -> "active-protocol"
        "ATDPN" -> "active-protocol-number"
        "ATCS" -> "can-error-counters"
        else -> uppercase()
    }

    private fun OBDResponse.logLevel(): Level = when (this) {
        is OBDResponse.Data -> Level.Info
        is OBDResponse.NoData.Searching -> Level.Info
        is OBDResponse.NoData.CanError,
        is OBDResponse.NoData.Empty,
        is OBDResponse.NoData.Unrecognized,
        is OBDResponse.NoData.Mismatched -> Level.HandledError
        is OBDResponse.NoData.Error -> Level.Error
    }

    private fun OBDResponse.diagnosticSummary(): String = when (this) {
        is OBDResponse.Data -> "Parsed ${data.size} OBD response(s): " + data.joinToString { "CAN=${it.canId} PID=${it.pid} bytes=${it.data.size}" }
        is OBDResponse.NoData.CanError -> "Adapter reported CAN ERROR"
        is OBDResponse.NoData.Searching -> "Adapter is searching for a protocol"
        is OBDResponse.NoData.Empty -> "Adapter reported NO DATA"
        is OBDResponse.NoData.Unrecognized -> "Response did not match any known ELM/OBD format"
        is OBDResponse.NoData.Mismatched -> "Response did not match requested PID ${expectedPid}: ${actualSources.joinToString()}"
        is OBDResponse.NoData.Error -> "Response parsing failed"
    }

}
