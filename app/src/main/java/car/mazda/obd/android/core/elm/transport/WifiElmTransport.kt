package car.mazda.obd.android.core.elm.transport

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import car.mazda.obd.android.core.elm.exception.AdapterUnreachableException
import car.mazda.obd.android.core.elm.exception.LostConnectionException
import car.mazda.obd.android.core.elm.exception.NetworkUnavailableException
import car.mazda.obd.android.core.elm.exception.OBDException
import car.mazda.obd.android.core.elm.exception.UnknownObdException
import kotlinx.coroutines.suspendCancellableCoroutine
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

class WifiElmTransport(
    private val connectivityManager: ConnectivityManager,
    private val endpoint: AdapterEndpoint,
) : ElmTransport {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStreamWriter? = null

    override suspend fun connect() {
        disconnect()
        try {
            val connectedSocket = createSocket()
            connectedSocket.connect(InetSocketAddress(endpoint.host, endpoint.port), CONNECT_TIMEOUT_MS)
            socket = connectedSocket
            reader = BufferedReader(InputStreamReader(connectedSocket.getInputStream()))
            writer = OutputStreamWriter(connectedSocket.getOutputStream())
        } catch (e: UnknownHostException) {
            disconnect()
            throw NetworkUnavailableException("Network/DNS unavailable: ${e.message}", e)
        } catch (e: NoRouteToHostException) {
            disconnect()
            throw NetworkUnavailableException("No route to ${endpoint.host}. Check the adapter Wi-Fi network.", e)
        } catch (e: SocketTimeoutException) {
            disconnect()
            throw AdapterUnreachableException("Connection timeout to $endpoint. The adapter may be unreachable.", e)
        } catch (e: ConnectException) {
            disconnect()
            throw AdapterUnreachableException("Could not connect to $endpoint: ${e.message}", e)
        } catch (e: SocketException) {
            disconnect()
            throw NetworkUnavailableException("Could not route OBD socket over Wi-Fi: ${e.message}", e)
        } catch (e: SecurityException) {
            disconnect()
            throw NetworkUnavailableException("Missing permissions or network restriction: ${e.message}", e)
        } catch (e: OBDException) {
            disconnect()
            throw e
        } catch (e: Exception) {
            disconnect()
            throw UnknownObdException("Connection error: ${e.message}", e)
        }
    }

    override suspend fun exchange(command: String, readTimeoutMs: Int): String {
        val activeSocket = socket ?: throw NetworkUnavailableException("Socket is closed")
        val activeWriter = writer ?: throw NetworkUnavailableException("Writer is unavailable")
        val activeReader = reader ?: throw NetworkUnavailableException("Reader is unavailable")
        activeSocket.soTimeout = readTimeoutMs

        try {
            activeWriter.write(command + "\r")
            activeWriter.flush()
        } catch (e: Throwable) {
            throw LostConnectionException("Write failed: ${e.message}", e)
        }

        val raw = StringBuilder()
        while (true) {
            val ch = try {
                activeReader.read()
            } catch (e: SocketTimeoutException) {
                throw ElmTransportReadTimeoutException(raw.toString(), e)
            } catch (e: Throwable) {
                throw LostConnectionException("Read failed: ${e.message}", e)
            }

            if (ch == -1) throw LostConnectionException("Socket closed by remote side")

            val character = ch.toChar()
            raw.append(character)
            if (character == '>') return raw.toString()
        }
    }

    override fun disconnect() {
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { socket?.close() }
        writer = null
        reader = null
        socket = null
    }

    private suspend fun createSocket(): Socket {
        val wifiNetwork = requestWifiNetwork() ?: findWifiNetwork()
        if (wifiNetwork != null) return wifiNetwork.socketFactory.createSocket()
        throw NetworkUnavailableException(
            "No Wi-Fi network available for OBD adapter. Connect to adapter Wi-Fi and allow using it without internet.",
        )
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
                    override fun onAvailable(network: Network) = complete(this, network)
                    override fun onUnavailable() = complete(this, null)
                }

                continuation.invokeOnCancellation {
                    if (completed.compareAndSet(false, true)) {
                        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                    }
                }

                try {
                    connectivityManager.requestNetwork(request, callback)
                } catch (_: Throwable) {
                    complete(callback, null)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun findWifiNetwork(): Network? = connectivityManager.allNetworks.firstOrNull { network ->
        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val NETWORK_REQUEST_TIMEOUT_MS = 3_000L
    }
}
