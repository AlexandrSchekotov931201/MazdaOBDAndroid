package car.mazda.obd.android.core.elm.mapper

import car.mazda.obd.android.core.elm.entity.OBDResponse
import car.mazda.obd.android.core.elm.entity.OBDData

class OBDDataMapper {

    private companion object {
        private const val OBD_CURRENT_DATA_RESPONSE = "41"
        private const val OBD_SERVICE_SEARCHING = "SEARCHING"
        private const val OBD_SERVICE_NO_DATA = "NO DATA"
        private const val OBD_SERVICE_CAN_ERROR = "CAN ERROR"
        private const val OBD_SERVICE_OK = "OK"
        private const val OBD_SERVICE_ERROR = "ERROR"

        private const val ELM_PROMPT = ">"

    }

    fun map(raw: String): OBDResponse {
        return try {
            val hasSearching = raw.contains("SEARCHING", ignoreCase = true)
            val hasNoData = raw.contains("NO DATA", ignoreCase = true)
            val hasCanError = raw.contains("CAN ERROR", ignoreCase = true)

            val responses = parseResponses(raw)

            when {
                responses.isNotEmpty() -> {
                    OBDResponse.Data(responses)
                }

                hasCanError -> {
                    OBDResponse.NoData.CanError(raw)
                }

                hasSearching -> {
                    OBDResponse.NoData.Searching(raw)
                }

                hasNoData -> {
                    OBDResponse.NoData.Empty(raw)
                }

                else -> {
                    OBDResponse.NoData.Unrecognized(raw)
                }
            }
        } catch (t: Throwable) {
            OBDResponse.NoData.Error(raw, t)
        }
    }

    private fun parseResponses(raw: String): List<OBDData> {
        val result = mutableListOf<OBDData>()

        raw.lineSequence().forEach { line ->
            val ln = line.trim()
            if (ln.isEmpty()) return@forEach

            val upper = ln.uppercase()

            if (upper.startsWith(OBD_SERVICE_SEARCHING)) return@forEach
            if (upper == OBD_SERVICE_NO_DATA) return@forEach
            if (upper == OBD_SERVICE_CAN_ERROR) return@forEach
            if (upper == OBD_SERVICE_OK) return@forEach
            if (upper.contains(OBD_SERVICE_ERROR)) return@forEach
            if (ln == ELM_PROMPT) return@forEach

            val parts = ln.split(Regex("\\s+"))
                .filter { it.matches(Regex("[0-9A-Fa-f]{2,8}")) }
            val modeIndex = parts.indexOfFirst { it.equals(OBD_CURRENT_DATA_RESPONSE, ignoreCase = true) }
            if (modeIndex < 0 || modeIndex + 1 >= parts.size) return@forEach

            val pid = parts[modeIndex + 1].uppercase()
            val declaredLength = parts.getOrNull(modeIndex - 1)
                ?.takeIf { it.length == 2 }
                ?.toIntOrNull(16)
            val payloadByteCount = declaredLength
                ?.minus(2)
                ?.takeIf { it >= 0 }
            val availableData = parts.drop(modeIndex + 2)
            val data = if (payloadByteCount == null) {
                availableData
            } else {
                availableData.take(payloadByteCount)
            }
            val canId = parts.firstOrNull()
                ?.takeIf { modeIndex >= 2 && (it.length == 3 || it.length == 8) }
                ?.uppercase()
                .orEmpty()

            result += OBDData(
                canId = canId,
                pid = pid,
                data = data
            )
        }

        return result
    }

}


