package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.OBDData
import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.elm.entity.SupportedPidRange
import car.mazda.obd.android.core.elm.mapper.OBDDataMapper
import car.mazda.obd.android.core.telemetry.StandardPidCatalog
import car.mazda.obd.android.feature.monitor.TelemetryResponseMapper
import car.mazda.obd.android.feature.trip.EngineRpmSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun parsesCompactCanResponsesWhenAdapterDoesNotInsertSpaces() {
        val elevenBit = mapper.map("7E804410C1AF8\r>") as OBDResponse.Data
        val twentyNineBit = mapper.map("18DAF11004410C1AF8\r>") as OBDResponse.Data

        assertEquals(OBDData("7E8", "0C", listOf("1A", "F8")), elevenBit.data.single())
        assertEquals(OBDData("18DAF110", "0C", listOf("1A", "F8")), twentyNineBit.data.single())
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
        val supportedByEcu = SupportedPidDecoder.decodeByEcu(
            range = SupportedPidRange.Pids01To20,
            responses = listOf(
                OBDData("7E8", "00", listOf("08", "10", "00", "01")),
                OBDData("7E9", "00", listOf("08", "00", "00", "00")),
            ),
        )

        assertEquals(setOf(0x05, 0x0C, 0x20), supportedByEcu.getValue("7E8"))
        assertEquals(setOf(0x05), supportedByEcu.getValue("7E9"))
        assertEquals("0120", OBDRequest.SupportedPids(SupportedPidRange.Pids21To40).value)
        assertEquals(0x0C, OBDRequest.EngineRpm.pid)
        assertEquals("0C", OBDRequest.EngineRpm.responsePidHex)
        val capabilities = VehicleCapabilities(setOf(SupportedPidRange.Pids01To20), supportedByEcu)
        assertTrue(capabilities.supports(0x0C))
        assertFalse(capabilities.supports(0x0D))
        assertTrue(capabilities.supports(0x21))
        assertEquals("7E8", capabilities.preferredEcuFor(0x0C))
    }

    @Test
    fun acceptsStandardPidFromAnUnfamiliarEcuAddress() {
        val sample = TelemetryResponseMapper().mapEngineRpm(
            OBDResponse.Data(listOf(OBDData("6A0", "0C", listOf("1A", "F8")))),
        )

        assertEquals(EngineRpmSample.Value(1726), sample)
    }

    @Test
    fun rejectsStalePidAndSelectsPreferredEcu() {
        val stale = OBDResponseCorrelator.correlate(
            response = OBDResponse.Data(listOf(OBDData("7E8", "0C", listOf("1A", "F8")))),
            request = OBDRequest.EngineCoolantTemperature,
            preferredEcu = "7E8",
        )
        val selected = OBDResponseCorrelator.correlate(
            response = OBDResponse.Data(
                listOf(
                    OBDData("7E8", "0C", listOf("1A", "F8")),
                    OBDData("7E9", "0C", listOf("10", "00")),
                ),
            ),
            request = OBDRequest.EngineRpm,
            preferredEcu = "7E9",
        ) as OBDResponse.Data

        assertTrue(stale is OBDResponse.NoData.Mismatched)
        assertEquals("7E9", selected.data.single().canId)
    }

    @Test
    fun standardPollingCatalogHasValidStaticConfiguration() {
        val targets = StandardPidCatalog.Default

        assertTrue(targets.all { it.periodMs > 0 })
        assertEquals(targets.size, targets.map { it.metric }.distinct().size)
    }
}
