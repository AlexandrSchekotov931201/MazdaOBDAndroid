package car.mazda.obd.android.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterEndpointValidatorTest {
    @Test
    fun `valid endpoint is trimmed and parsed`() {
        val endpoint = AdapterEndpointValidator.validate(" 192.168.0.10 ", " 35000 ").getOrThrow()

        assertEquals("192.168.0.10", endpoint.host)
        assertEquals(35000, endpoint.port)
    }

    @Test
    fun `host with protocol is rejected`() {
        assertTrue(AdapterEndpointValidator.validate("http://192.168.0.10", "35000").isFailure)
    }

    @Test
    fun `port outside tcp range is rejected`() {
        assertTrue(AdapterEndpointValidator.validate("adapter.local", "65536").isFailure)
    }
}
