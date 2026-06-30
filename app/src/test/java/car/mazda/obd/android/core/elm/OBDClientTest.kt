package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.elm.entity.StandardPid
import car.mazda.obd.android.core.elm.transport.ElmTransport
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

    private class FakeElmTransport(vararg responses: String) : ElmTransport {
        private val responses = ArrayDeque(responses.toList())
        val commands = mutableListOf<String>()
        val timeouts = mutableListOf<Int>()
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
            connected = false
        }
    }
}
