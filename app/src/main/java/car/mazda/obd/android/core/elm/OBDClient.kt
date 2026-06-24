package car.mazda.obd.android.core.elm

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class OBDClient {

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 2_000
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

            val s = Socket()
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
