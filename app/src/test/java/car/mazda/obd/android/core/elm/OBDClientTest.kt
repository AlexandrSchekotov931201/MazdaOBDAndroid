package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.elm.entity.StandardPid
import car.mazda.obd.android.core.elm.exception.ElmPromptTimeoutException
import car.mazda.obd.android.core.elm.exception.ElmCommandInterruptedException
import car.mazda.obd.android.core.elm.exception.ResponseDesynchronizationException
import car.mazda.obd.android.core.elm.transport.ElmTransport
import car.mazda.obd.android.core.elm.transport.ElmTransportReadTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class OBDClientTest {
    @Test
    fun retriesOnceWhenStaleResponseHasWrongPid() = runBlocking {
        val transport = FakeElmTransport(
            "7E8 04 41 0C 1A F8\r>",
            "7E8 03 41 05 7B\r>",
        )
        val client = OBDClient(transport)
        client.connect()

        val response = client.requestObd(
            OBDRequest.CurrentData(StandardPid.ENGINE_COOLANT_TEMPERATURE),
        ) as OBDResponse.Data

        assertEquals(listOf("0105", "0105"), transport.commands)
        assertEquals("05", response.data.single().pid)
        assertEquals(listOf(15_000, 5_000), transport.timeouts)
    }

    @Test
    fun discoversOnlyRequiredRangeAndKeepsCapabilitiesPerEcu() = runBlocking {
        val transport = FakeElmTransport(
            "7E8 06 41 00 08 10 00 00\r7E9 06 41 00 08 00 00 00\r>",
        )
        val client = OBDClient(transport)
        client.connect()

        val capabilities = client.discoverCapabilities(setOf(0x05, 0x0C))

        assertEquals(listOf("0100"), transport.commands)
        assertEquals(setOf(0x05, 0x0C), capabilities.supportedPidsByEcu.getValue("7E8"))
        assertEquals(setOf(0x05), capabilities.supportedPidsByEcu.getValue("7E9"))
        assertTrue(capabilities.hasDiscoveryFor(0x0C))
    }

    @Test
    fun disconnectsTransportBeforePropagatingPromptTimeout() = runBlocking {
        val transport = TimeoutElmTransport()
        val client = OBDClient(transport)
        client.connect()

        val error = runCatching {
            client.requestObd(OBDRequest.CurrentData(StandardPid.ENGINE_RPM))
        }.exceptionOrNull()

        assertTrue(error is ElmPromptTimeoutException)
        assertEquals(1, transport.disconnectCount)
        assertTrue(!transport.connected)
    }

    @Test
    fun disconnectsAfterSecondMismatchedResponse() = runBlocking {
        val transport = FakeElmTransport(
            "7E8 04 41 0C 1A F8\r>",
            "7E8 04 41 0C 1A F9\r>",
        )
        val client = OBDClient(transport)
        client.connect()

        val error = runCatching {
            client.requestObd(OBDRequest.CurrentData(StandardPid.ENGINE_COOLANT_TEMPERATURE))
        }.exceptionOrNull()

        assertTrue(error is ResponseDesynchronizationException)
        assertEquals(listOf("0105", "0105"), transport.commands)
        assertEquals(1, transport.disconnectCount)
    }

    @Test
    fun reportsStoppedAdapterCommandAsTemporaryInterruption() = runBlocking {
        val transport = FakeElmTransport("STOPPED\r>")
        val client = OBDClient(transport)
        client.connect()

        val error = runCatching { client.initializingEcu() }.exceptionOrNull()

        assertTrue(error is ElmCommandInterruptedException)
        assertEquals(listOf("ATZ"), transport.commands)
        assertEquals(1, transport.disconnectCount)
    }

    @Test
    fun acceptsExpectedInitializationResponses() = runBlocking {
        val transport = FakeElmTransport(
            "OBDII v1.5\r>",
            "OK\r>",
            "OK\r>",
            "OK\r>",
            "OK\r>",
            "OK\r>",
            "OK\r>",
        )
        val client = OBDClient(transport)
        client.connect()

        client.initializingEcu()

        assertEquals(7, transport.commands.size)
        assertEquals(0, transport.disconnectCount)
    }

    @Test
    fun continuesWhenOptionalInitializationCommandsAreUnsupported() = runBlocking {
        val transport = FakeElmTransport(
            "OBDII v1.5\r>",
            "OK\r>",
            "OK\r>",
            "?\r>",
            "OK\r>",
            "?\r>",
            "OK\r>",
        )
        val client = OBDClient(transport)
        client.connect()

        client.initializingEcu()

        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS1", "ATH1", "ATAT1", "ATSP0"), transport.commands)
        assertEquals(0, transport.disconnectCount)
    }

    private class FakeElmTransport(vararg responses: String) : ElmTransport {
        private val responses = ArrayDeque(responses.toList())
        val commands = mutableListOf<String>()
        val timeouts = mutableListOf<Int>()
        var disconnectCount = 0
            private set
        private var connected = false

        override suspend fun connect() {
            connected = true
        }

        override suspend fun exchange(command: String, readTimeoutMs: Int): String {
            check(connected)
            commands += command
            timeouts += readTimeoutMs
            return responses.removeFirst()
        }

        override fun disconnect() {
            if (connected) disconnectCount++
            connected = false
        }
    }

    private class TimeoutElmTransport : ElmTransport {
        var connected = false
            private set
        var disconnectCount = 0
            private set

        override suspend fun connect() {
            connected = true
        }

        override suspend fun exchange(command: String, readTimeoutMs: Int): String {
            check(connected)
            throw ElmTransportReadTimeoutException(
                partialRaw = "7E8 04 41 0C 1A F8\r",
                cause = java.net.SocketTimeoutException("test timeout"),
            )
        }

        override fun disconnect() {
            if (connected) disconnectCount++
            connected = false
        }
    }
}
