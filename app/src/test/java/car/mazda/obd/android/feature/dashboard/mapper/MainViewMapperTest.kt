package car.mazda.obd.android.feature.dashboard.mapper

import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.feature.trip.EngineRpmSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewMapperTest {

    private val mapper = MainViewMapper()

    @Test
    fun mapsCanErrorRpmResponseAsNoData() {
        val sample = mapper.mapEngineRpm(OBDResponse.NoData.CanError("010C\rCAN ERROR\r>"))

        assertTrue(sample is EngineRpmSample.NoData)
    }

    @Test
    fun mapsValidRpmResponse() {
        val sample = mapper.mapEngineRpm(
            OBDResponse.Data(
                listOf(
                    car.mazda.obd.android.core.elm.entity.OBDData(
                        canId = "7E8",
                        pid = "0C",
                        data = listOf("0D", "48"),
                    )
                )
            )
        )

        assertEquals(850, (sample as EngineRpmSample.Value).rpm)
    }
}
