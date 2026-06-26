package car.mazda.obd.android.core.elm

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import car.mazda.obd.android.BuildConfig
import car.mazda.obd.android.core.elm.entity.ElmCommand
import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.elm.exception.AdapterUnreachableException
import car.mazda.obd.android.core.elm.exception.LostConnectionException
import car.mazda.obd.android.core.elm.exception.NetworkUnavailableException
import car.mazda.obd.android.core.elm.exception.ProtocolException
import car.mazda.obd.android.core.elm.exception.UnknownObdException
import car.mazda.obd.android.core.elm.mapper.OBDDataMapper
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
        try {
            unlockedRelease()

            val s = createSocket()
            s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            s.soTimeout = READ_TIMEOUT_MS

            socket = s
            reader = BufferedReader(InputStreamReader(s.getInputStream()))
            writer = OutputStreamWriter(s.getOutputStream())
        } catch (e: UnknownHostException) {
            unlockedRelease()
            throw NetworkUnavailableException(
                message = "Network/DNS unavailable: ${e.message}",
                cause = e,
            )
        } catch (e: NoRouteToHostException) {
            unlockedRelease()
            throw NetworkUnavailableException(
                message = "No route to $host. Check the adapter Wi-Fi network.",
                cause = e,
            )
        } catch (e: SocketTimeoutException) {
            unlockedRelease()
            throw AdapterUnreachableException(
                message = "Connection timeout to $host:$port. The adapter may be unreachable.",
                cause = e,
            )
        } catch (e: ConnectException) {
            unlockedRelease()
            throw AdapterUnreachableException(
                message = "Could not connect to $host:$port: ${e.message}",
                cause = e,
            )
        } catch (e: SocketException) {
            unlockedRelease()
            throw NetworkUnavailableException(
                message = "Could not route OBD socket over Wi-Fi: ${e.message}",
                cause = e,
            )
        } catch (e: SecurityException) {
            unlockedRelease()
            throw NetworkUnavailableException(
                message = "Missing permissions or network restriction: ${e.message}",
                cause = e,
            )
        } catch (e: Exception) {
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

    private fun unlockedRequestElm(cmd: ElmCommand): OBDResponse =
        unlockedRequest(cmd.value)

    private fun unlockedRequestObd(req: OBDRequest): OBDResponse =
        unlockedRequest(req.value)

    private fun unlockedRequest(request: String): OBDResponse {
        val w = writer ?: throw NetworkUnavailableException("Writer is null - socket closed")
        val r = reader ?: throw NetworkUnavailableException("Reader is null - socket closed")

        try {
            w.write(request + "\r")
            w.flush()
        } catch (e: Throwable) {
            throw LostConnectionException("Write failed: ${e.message}", e)
        }

        val sb = StringBuilder()

        while (true) {
            val ch = try {
                r.read()
            } catch (e: SocketTimeoutException) {
                throw AdapterUnreachableException("Read timeout on request: $request", e)
            } catch (e: Throwable) {
                throw LostConnectionException("Read failed: ${e.message}", e)
            }

            if (ch == -1) {
                throw LostConnectionException("Socket closed by remote side")
            }

            val c = ch.toChar()

            sb.append(c)
            if (c == '>') break
        }

        val raw = sb.toString()

        return try {
            dataMapper.map(raw)
        } catch (e: Throwable) {
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
}
