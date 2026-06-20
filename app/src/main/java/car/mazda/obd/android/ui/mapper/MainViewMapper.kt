package car.mazda.obd.android.ui.mapper

import car.mazda.obd.android.elm.entity.CanIds
import car.mazda.obd.android.elm.entity.OBDData
import car.mazda.obd.android.elm.entity.OBDResponse
import car.mazda.obd.android.logs.AppLogger
import car.mazda.obd.android.ui.trip.EngineRpmSample

class MainViewMapper {

    private companion object {
        private const val ENGINE_RPM_PID = "0C"
        private const val RPM_DIVISOR = 4
    }

    fun mapEngineRpm(response: OBDResponse): EngineRpmSample {
        return when (response) {
            is OBDResponse.Data -> EngineRpmSample.Value(mapEngineRpm(response.data))
            is OBDResponse.NoData -> {
                when (response) {
                    is OBDResponse.NoData.Empty,
                    is OBDResponse.NoData.Searching -> EngineRpmSample.NoData

                    is OBDResponse.NoData.Unrecognized -> {
                        AppLogger.log("Unrecognized OBD response: ${response.raw}")
                        EngineRpmSample.NoData
                    }

                    is OBDResponse.NoData.Error -> {
                        AppLogger.log("OBD response error: ${response.throwable}")
                        EngineRpmSample.ConnectionError(response.throwable)
                    }
                }
            }
        }
    }

    private fun mapEngineRpm(dataList: List<OBDData>): Int {
        val rpmData = dataList.firstOrNull {
            it.canId == CanIds.ENGINE_ECU_RESPONSE && it.pid == ENGINE_RPM_PID
        } ?: return 0

        return try {
            val a = rpmData.data[0].toInt(16)
            val b = rpmData.data[1].toInt(16)
            (a * 256 + b) / RPM_DIVISOR
        } catch (t: Throwable) {
            0
        }
    }
}


