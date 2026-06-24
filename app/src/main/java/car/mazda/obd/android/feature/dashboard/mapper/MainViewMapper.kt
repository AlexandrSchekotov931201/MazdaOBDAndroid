package car.mazda.obd.android.feature.dashboard.mapper

import car.mazda.obd.android.core.elm.entity.CanIds
import car.mazda.obd.android.core.elm.entity.OBDData
import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.feature.trip.EngineRpmSample
import car.mazda.obd.android.feature.warmup.EngineTemperatureSample

class MainViewMapper {

    private companion object {
        private const val ENGINE_COOLANT_TEMP_PID = "05"
        private const val ENGINE_OIL_TEMP_PID = "5C"
        private const val ENGINE_RPM_PID = "0C"
        private const val TEMPERATURE_OFFSET = 40
        private const val RPM_DIVISOR = 4
    }

    fun mapEngineRpm(response: OBDResponse): EngineRpmSample {
        return when (response) {
            is OBDResponse.Data -> {
                mapEngineRpm(response.data)
                    ?.let(EngineRpmSample::Value)
                    ?: EngineRpmSample.NoData
            }
            is OBDResponse.NoData -> {
                when (response) {
                    is OBDResponse.NoData.Empty -> {
                        AppLogger.handledError("Handled RPM no data: pid=010C raw=${response.raw.compactRaw()}")
                        EngineRpmSample.NoData
                    }

                    is OBDResponse.NoData.Searching -> {
                        AppLogger.handledError("Handled RPM searching: pid=010C raw=${response.raw.compactRaw()}")
                        EngineRpmSample.NoData
                    }

                    is OBDResponse.NoData.Unrecognized -> {
                        AppLogger.handledError("Handled unrecognized RPM response: pid=010C raw=${response.raw.compactRaw()}")
                        EngineRpmSample.NoData
                    }

                    is OBDResponse.NoData.Error -> {
                        AppLogger.handledError(
                            "Handled RPM response error: pid=010C error=${response.throwable::class.simpleName}: ${response.throwable.message} raw=${response.raw.compactRaw()}"
                        )
                        EngineRpmSample.ConnectionError(response.throwable)
                    }
                }
            }
        }
    }

    fun mapEngineCoolantTemperature(response: OBDResponse): EngineTemperatureSample {
        return mapTemperatureResponse(
            response = response,
            pid = ENGINE_COOLANT_TEMP_PID,
            unrecognizedLogPrefix = "Unrecognized coolant temperature response",
            errorLogPrefix = "Coolant temperature response error",
        )
    }

    fun mapEngineOilTemperature(response: OBDResponse): EngineTemperatureSample {
        return mapTemperatureResponse(
            response = response,
            pid = ENGINE_OIL_TEMP_PID,
            unrecognizedLogPrefix = "Unrecognized oil temperature response",
            errorLogPrefix = "Oil temperature response error",
        )
    }

    private fun mapTemperatureResponse(
        response: OBDResponse,
        pid: String,
        unrecognizedLogPrefix: String,
        errorLogPrefix: String,
    ): EngineTemperatureSample {
        return when (response) {
            is OBDResponse.Data -> {
                mapTemperature(response.data, pid)
                    ?.let(EngineTemperatureSample::Value)
                    ?: EngineTemperatureSample.NoData
            }
            is OBDResponse.NoData -> {
                when (response) {
                    is OBDResponse.NoData.Empty,
                    is OBDResponse.NoData.Searching -> EngineTemperatureSample.NoData

                    is OBDResponse.NoData.Unrecognized -> {
                        AppLogger.handledError("Handled $unrecognizedLogPrefix: pid=$pid raw=${response.raw.compactRaw()}")
                        EngineTemperatureSample.NoData
                    }

                    is OBDResponse.NoData.Error -> {
                        AppLogger.handledError(
                            "Handled $errorLogPrefix: pid=$pid error=${response.throwable::class.simpleName}: ${response.throwable.message} raw=${response.raw.compactRaw()}"
                        )
                        EngineTemperatureSample.ConnectionError(response.throwable)
                    }
                }
            }
        }
    }

    private fun mapEngineRpm(dataList: List<OBDData>): Int? {
        val rpmData = dataList.firstOrNull {
            it.canId.isEngineEcuResponse() && it.pid == ENGINE_RPM_PID
        } ?: run {
            AppLogger.handledError("Handled RPM response without engine RPM data: pid=010C parsed=${dataList.describe()}")
            return null
        }

        return try {
            val a = rpmData.data[0].toInt(16)
            val b = rpmData.data[1].toInt(16)
            (a * 256 + b) / RPM_DIVISOR
        } catch (t: Throwable) {
            AppLogger.handledError("Handled malformed RPM payload: pid=010C can=${rpmData.canId} payload=${rpmData.data} error=${t.message}")
            null
        }
    }

    private fun mapTemperature(dataList: List<OBDData>, pid: String): Int? {
        val tempData = dataList.firstOrNull {
            it.canId == CanIds.ENGINE_ECU_RESPONSE && it.pid == pid
        } ?: return null

        return try {
            tempData.data[0].toInt(16) - TEMPERATURE_OFFSET
        } catch (t: Throwable) {
            null
        }
    }

    private fun String.isEngineEcuResponse(): Boolean =
        this == CanIds.ENGINE_ECU_RESPONSE || matches(Regex("7E[8-F]", RegexOption.IGNORE_CASE))

    private fun String.compactRaw(): String =
        lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != ">" }
            .joinToString(" | ")
            .take(240)

    private fun List<OBDData>.describe(): String =
        joinToString(limit = 4, truncated = "...") { data ->
            "${data.canId}/${data.pid}/${data.data.joinToString(" ")}"
        }.ifBlank { "empty" }
}


