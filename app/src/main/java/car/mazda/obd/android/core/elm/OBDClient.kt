package car.mazda.obd.android.core.elm

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import car.mazda.obd.android.BuildConfig
import car.mazda.obd.android.core.elm.entity.CanIds
import car.mazda.obd.android.core.elm.entity.ElmCommand
import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.elm.exception.AdapterUnreachableException
import car.mazda.obd.android.core.elm.exception.LostConnectionException
import car.mazda.obd.android.core.elm.exception.NetworkUnavailableException
import car.mazda.obd.android.core.elm.exception.ProtocolException
import car.mazda.obd.android.core.elm.exception.UnknownObdException
import car.mazda.obd.android.core.elm.mapper.OBDDataMapper
import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.core.logs.AppLogger.Direction
import car.mazda.obd.android.core.logs.AppLogger.Layer
import car.mazda.obd.android.core.logs.AppLogger.Level
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class OBDClient(
    private val connectivityManager: ConnectivityManager,
) {

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 2_000
        private const val NETWORK_REQUEST_TIMEOUT_MS = 3_000L
        private const val PROD_FLAVOR = "prod"
    }

    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStreamWriter? = null

    private val dataMapper = OBDDataMapper()

    private val mutex = Mutex()

    private val host = BuildConfig.OBD_HOST
    private val port = BuildConfig.OBD_PORT

    suspend fun connect() = mutex.withLock {
        AppLogger.event(layer = Layer.Network, operation = "connect", message = "Connecting to $host:$port")
        try {
            unlockedRelease()

            val s = createSocket()
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.soTimeout = READ_TIMEOUT_MS

            socket = s
            reader = BufferedReader(InputStreamReader(s.getInputStream()))
            writer = OutputStreamWriter(s.getOutputStream())
            AppLogger.event(layer = Layer.Network, operation = "connect", message = "Socket connected to $host:$port")
        } catch (e: UnknownHostException) {
            logConnectionFailure(e)
            unlockedRelease()
            throw NetworkUnavailableException(
                message = "Network/DNS unavailable: ${e.message}",
                cause = e,
            )
        } catch (e: NoRouteToHostException) {
            logConnectionFailure(e)
            unlockedRelease()
            throw NetworkUnavailableException(
                message = "No route to $host. Check the adapter Wi-Fi network.",
                cause = e,
            )
        } catch (e: SocketTimeoutException) {
            logConnectionFailure(e)
            unlockedRelease()
            throw AdapterUnreachableException(
                message = "Connection timeout to $host:$port. The adapter may be unreachable.",
                cause = e,
            )
        } catch (e: ConnectException) {
            logConnectionFailure(e)
            unlockedRelease()
            throw AdapterUnreachableException(
                message = "Could not connect to $host:$port: ${e.message}",
                cause = e,
            )
        } catch (e: SocketException) {
            logConnectionFailure(e)
            unlockedRelease()
            throw NetworkUnavailableException(
                message = "Could not route OBD socket over Wi-Fi: ${e.message}",
                cause = e,
            )
        } catch (e: SecurityException) {
            logConnectionFailure(e)
            unlockedRelease()
            throw NetworkUnavailableException(
                message = "Missing permissions or network restriction: ${e.message}",
                cause = e,
            )
        } catch (e: Exception) {
            logConnectionFailure(e)
            unlockedRelease()
            throw UnknownObdException(
                message = "Connection error: ${e.message}",
                cause = e,
            )
        }
    }

    private suspend fun createSocket(): Socket {
        val wifiNetwork = requestWifiNetwork() ?: findWifiNetwork()
        if (wifiNetwork != null) {
            return wifiNetwork.socketFactory.createSocket()
        }

        if (BuildConfig.FLAVOR == PROD_FLAVOR) {
            throw NetworkUnavailableException(
                "No Wi-Fi network available for OBD adapter. Connect to adapter Wi-Fi and allow using it without internet.",
            )
        }

        return Socket()
    }

    private suspend fun requestWifiNetwork(): Network? {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        return withTimeoutOrNull(NETWORK_REQUEST_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val completed = AtomicBoolean(false)

                fun complete(callback: ConnectivityManager.NetworkCallback, network: Network?) {
                    if (!completed.compareAndSet(false, true)) return
                    runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                    continuation.resume(network)
                }

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        complete(this, network)
                    }

                    override fun onUnavailable() {
                        complete(this, null)
                    }
                }

                continuation.invokeOnCancellation {
                    if (completed.compareAndSet(false, true)) {
                        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                    }
                }

                try {
                    connectivityManager.requestNetwork(request, callback)
                } catch (t: Throwable) {
                    complete(callback, null)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun findWifiNetwork(): Network? {
        return connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    suspend fun initializingEcu() = mutex.withLock {
        for (cmd in ElmCommand.defaultInitSequence) {
            unlockedRequestElm(cmd)
        }
    }

    suspend fun requestObd(request: OBDRequest): OBDResponse = mutex.withLock {
        return unlockedRequestObd(request)
    }

    suspend fun discoverCapabilities(): VehicleCapabilities = mutex.withLock {
        val supported = mutableSetOf<Int>()
        var basePid = 0x00
        var receivedAnyData = false

        while (basePid <= 0xE0) {
            val response = unlockedRequestObd(OBDRequest.SupportedPids(basePid))
            if (response !is OBDResponse.Data) break

            val expectedPid = basePid.toString(16).uppercase().padStart(2, '0')
            val matchingResponses = response.data.filter {
                it.pid.equals(expectedPid, ignoreCase = true)
            }
            if (matchingResponses.isEmpty()) break

            receivedAnyData = true
            val range = SupportedPidDecoder.decode(basePid, matchingResponses)
            supported += range
            val nextRangePid = basePid + 0x20
            if (nextRangePid !in range) break
            basePid = nextRangePid
        }

        VehicleCapabilities(
            discoveryComplete = receivedAnyData,
            supportedPids = supported,
        )
    }

    private fun unlockedRequestElm(cmd: ElmCommand): OBDResponse =
        unlockedRequest(cmd.value)

    private fun unlockedRequestObd(req: OBDRequest): OBDResponse {
        return unlockedRequest(req.value)
    }

    private fun unlockedRequest(request: String): OBDResponse {
        val w = writer ?: throw NetworkUnavailableException("Writer is null - socket closed")
        val r = reader ?: throw NetworkUnavailableException("Reader is null - socket closed")

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

        try {
            w.write(request + "\r")
            w.flush()
        } catch (e: Throwable) {
            AppLogger.event(Level.Error, Layer.Network, Direction.Tx, "socket-write", "Write failed", exchangeId, request, e)
            throw LostConnectionException("Write failed: ${e.message}", e)
        }

        val sb = StringBuilder()

        while (true) {
            val ch = try {
                r.read()
            } catch (e: SocketTimeoutException) {
                AppLogger.event(Level.Error, Layer.Network, Direction.Rx, "socket-read", "Timed out waiting for ELM prompt", exchangeId, sb.toString(), e)
                throw AdapterUnreachableException("Read timeout on request: $request", e)
            } catch (e: Throwable) {
                AppLogger.event(Level.Error, Layer.Network, Direction.Rx, "socket-read", "Read failed", exchangeId, sb.toString(), e)
                throw LostConnectionException("Read failed: ${e.message}", e)
            }

            if (ch == -1) {
                AppLogger.event(Level.Error, Layer.Network, Direction.Rx, "socket-read", "Socket closed before ELM prompt", exchangeId, sb.toString())
                throw LostConnectionException("Socket closed by remote side")
            }

            val c = ch.toChar()

            sb.append(c)
            if (c == '>') break
        }

        val raw = sb.toString()
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
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { socket?.close() }
        writer = null
        reader = null
        socket = null
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
        "ATH1" -> "headers-on"
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
        is OBDResponse.NoData.CanError, is OBDResponse.NoData.Empty, is OBDResponse.NoData.Unrecognized -> Level.HandledError
        is OBDResponse.NoData.Error -> Level.Error
    }

    private fun OBDResponse.diagnosticSummary(): String = when (this) {
        is OBDResponse.Data -> "Parsed ${data.size} OBD response(s): " + data.joinToString { "CAN=${it.canId} PID=${it.pid} bytes=${it.data.size}" }
        is OBDResponse.NoData.CanError -> "Adapter reported CAN ERROR"
        is OBDResponse.NoData.Searching -> "Adapter is searching for a protocol"
        is OBDResponse.NoData.Empty -> "Adapter reported NO DATA"
        is OBDResponse.NoData.Unrecognized -> "Response did not match any known ELM/OBD format"
        is OBDResponse.NoData.Error -> "Response parsing failed"
    }

}
