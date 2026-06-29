package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.OBDData
import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.elm.mapper.OBDDataMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OBDDataMapperTest {
    private val mapper = OBDDataMapper()

    @Test
    fun parses11BitCanResponseWithHeader() {
        val response = mapper.map("7E8 04 41 0C 1A F8\r>") as OBDResponse.Data

        assertEquals(OBDData("7E8", "0C", listOf("1A", "F8")), response.data.single())
    }

    @Test
    fun parses29BitCanResponseWithHeader() {
        val response = mapper.map("18DAF110 04 41 0C 1A F8\r>") as OBDResponse.Data

        assertEquals(OBDData("18DAF110", "0C", listOf("1A", "F8")), response.data.single())
    }

    @Test
    fun parsesHeaderlessAndLegacyProtocolResponses() {
        val headerless = mapper.map("41 0C 1A F8\r>") as OBDResponse.Data
        val legacy = mapper.map("48 6B 10 41 05 7B A1\r>") as OBDResponse.Data

        assertEquals(OBDData("", "0C", listOf("1A", "F8")), headerless.data.single())
        assertEquals("05", legacy.data.single().pid)
        assertEquals("7B", legacy.data.single().data.first())
    }

    @Test
    fun decodesSupportedPidBitmapAndBuildsRangeRequest() {
        val supported = SupportedPidDecoder.decode(
            basePid = 0x00,
            responses = listOf(OBDData("7E8", "00", listOf("08", "10", "00", "01"))),
        )

        assertEquals(setOf(0x05, 0x0C, 0x20), supported)
        assertEquals("0120", OBDRequest.SupportedPids(0x20).value)
        assertTrue(VehicleCapabilities(true, supported).supports(0x0C))
    }
}
