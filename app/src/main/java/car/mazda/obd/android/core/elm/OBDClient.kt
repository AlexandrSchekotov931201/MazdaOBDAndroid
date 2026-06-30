package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.CanIds
import car.mazda.obd.android.core.elm.entity.ElmCommand
import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.elm.entity.SupportedPidRange
import car.mazda.obd.android.core.elm.exception.ElmPromptTimeoutException
import car.mazda.obd.android.core.elm.exception.ElmCommandInterruptedException
import car.mazda.obd.android.core.elm.exception.ProtocolException
import car.mazda.obd.android.core.elm.exception.ResponseDesynchronizationException
import car.mazda.obd.android.core.elm.mapper.OBDDataMapper
import car.mazda.obd.android.core.elm.transport.ElmTransport
import car.mazda.obd.android.core.elm.transport.ElmTransportReadTimeoutException
import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.core.logs.AppLogger.Direction
import car.mazda.obd.android.core.logs.AppLogger.Layer
import car.mazda.obd.android.core.logs.AppLogger.Level
import kotlin.coroutines.cancellation.CancellationException
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
        try {
            for (cmd in ElmCommand.defaultInitSequence) {
                unlockedRequestElm(cmd)
            }
        } catch (t: Throwable) {
            invalidateTransportOnFailure(t)
            throw t
        }
    }

    suspend fun requestObd(
        request: OBDRequest,
        preferredEcu: String? = null,
    ): OBDResponse = mutex.withLock {
        try {
            unlockedRequestObdWithRetry(request, preferredEcu)
        } catch (t: Throwable) {
            invalidateTransportOnFailure(t)
            throw t
        }
    }

    suspend fun discoverCapabilities(requiredPids: Set<Int>): VehicleCapabilities = mutex.withLock {
        try {
            val discoveredRanges = mutableSetOf<SupportedPidRange>()
            val supportedByEcu = mutableMapOf<String, MutableSet<Int>>()

            val requiredRanges = requiredPids.map(SupportedPidRange::containing)
                .distinct()
                .sortedBy { it.basePid }
            for (range in requiredRanges) {
                val response = unlockedRequestObdWithRetry(OBDRequest.SupportedPids(range))
                if (response !is OBDResponse.Data) continue

                SupportedPidBitmapDecoder.decodeByEcu(range, response.data).forEach { (ecu, pids) ->
                    supportedByEcu.getOrPut(ecu) { mutableSetOf() } += pids
                }
                discoveredRanges += range
            }

            VehicleCapabilities(
                discoveredRanges = discoveredRanges,
                supportedPidsByEcu = supportedByEcu.mapValues { it.value.toSet() },
            )
        } catch (t: Throwable) {
            invalidateTransportOnFailure(t)
            throw t
        }
    }

    private suspend fun unlockedRequestElm(cmd: ElmCommand): OBDResponse {
        val response = unlockedRequest(cmd.value)
        if (!cmd.required && response.isUnsupportedElmCommand()) {
            AppLogger.event(
                level = Level.HandledError,
                layer = Layer.Elm,
                operation = cmd.value.operationName(),
                message = "Optional ELM command is unsupported; continuing initialization",
                raw = (response as OBDResponse.NoData).raw,
            )
            return response
        }
        if (!cmd.accepts(response)) {
            val raw = (response as? OBDResponse.NoData)?.raw.orEmpty()
            AppLogger.event(
                level = Level.Error,
                layer = Layer.Elm,
                operation = cmd.value.operationName(),
                message = "ELM response did not match command",
                raw = raw,
            )
            throw ProtocolException("Unexpected response to ${cmd.value}: ${raw.visibleForError()}")
        }
        return response
    }

    private suspend fun unlockedRequestObdWithRetry(
        request: OBDRequest,
        preferredEcu: String? = null,
    ): OBDResponse {
        val firstResponse = unlockedRequestObd(request, preferredEcu)
        if (firstResponse !is OBDResponse.NoData.Mismatched) return firstResponse

        AppLogger.event(
            level = Level.HandledError,
            layer = Layer.Parser,
            operation = request.value.operationName(),
            message = "Discarding stale/mismatched response and retrying request once",
            raw = firstResponse.raw,
        )
        val retryResponse = unlockedRequestObd(request, preferredEcu)
        if (retryResponse !is OBDResponse.NoData.Mismatched) return retryResponse

        AppLogger.event(
            level = Level.Error,
            layer = Layer.Parser,
            operation = request.value.operationName(),
            message = "Repeated mismatched response; invalidating ELM transport",
            raw = retryResponse.raw,
        )
        throw ResponseDesynchronizationException(
            "Repeated response mismatch for ${request.value}: ${retryResponse.actualSources.joinToString()}",
        )
    }

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

        if (raw.contains("STOPPED", ignoreCase = true)) {
            throw ElmCommandInterruptedException()
        }

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

    private fun invalidateTransportOnFailure(t: Throwable) {
        if (t is CancellationException) return
        AppLogger.event(
            level = Level.HandledError,
            layer = Layer.Network,
            operation = "transport-reset",
            message = "Invalidating ELM transport after ${t::class.simpleName}",
        )
        unlockedRelease()
    }

    private fun ElmCommand.accepts(response: OBDResponse): Boolean {
        if (response is OBDResponse.Data) return false
        val raw = (response as? OBDResponse.NoData)?.raw?.uppercase().orEmpty()
        if (
            raw.contains("STOPPED") ||
            raw.contains("CAN ERROR") ||
            raw.contains("NO DATA") ||
            raw.contains("?")
        ) {
            return false
        }

        return when (this) {
            ElmCommand.Reset,
            ElmCommand.Identify -> raw.contains("ELM") || raw.contains("OBDII")
            ElmCommand.EchoOff,
            ElmCommand.LineFeedsOff,
            ElmCommand.SpacesOn,
            ElmCommand.HeadersOn,
            ElmCommand.AdaptiveTiming,
            ElmCommand.AutoProtocol,
            is ElmCommand.SetHeader -> raw.contains("OK")
            ElmCommand.DeviceDescription -> raw.removeSuffix(">").trim().length > 2 && !raw.contains("OK")
            ElmCommand.ReadVoltage -> Regex("\\b\\d{1,2}(?:\\.\\d+)?V\\b").containsMatchIn(raw)
            ElmCommand.DescribeProtocol -> raw.contains("ISO") || raw.contains("CAN") || raw.contains("SAE")
            ElmCommand.DescribeProtocolNumber -> Regex("(?:^|\\s)A?[0-9A-C](?:\\s|>|$)").containsMatchIn(raw)
            ElmCommand.CanStatus -> Regex("T:[0-9A-F]{2}\\s+R:[0-9A-F]{2}").containsMatchIn(raw)
        }
    }

    private fun OBDResponse.isUnsupportedElmCommand(): Boolean =
        this is OBDResponse.NoData && raw.contains("?")

    private fun String.visibleForError(): String =
        replace("\r", "\\r").replace("\n", "\\n").take(200)

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
