package car.mazda.obd.android.core.elm.mapper

import car.mazda.obd.android.core.elm.entity.OBDResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OBDDataMapperTest {

    private val mapper = OBDDataMapper()

    @Test
    fun mapsCanErrorWithEchoAsCanErrorNoData() {
        val response = mapper.map("010C\r\rCAN ERROR\r\r>")

        assertTrue(response is OBDResponse.NoData.CanError)
        assertEquals("010C\r\rCAN ERROR\r\r>", (response as OBDResponse.NoData.CanError).raw)
    }

    @Test
    fun mapsHeaderedRpmResponseAsData() {
        val response = mapper.map("7E8 04 41 0C 0D 48\r>")

        assertTrue(response is OBDResponse.Data)
        val data = (response as OBDResponse.Data).data.single()
        assertEquals("7E8", data.canId)
        assertEquals("0C", data.pid)
        assertEquals(listOf("0D", "48"), data.data)
    }
}
