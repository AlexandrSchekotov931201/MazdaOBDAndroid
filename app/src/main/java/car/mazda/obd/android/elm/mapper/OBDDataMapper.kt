package car.mazda.obd.android.elm.mapper

import car.mazda.obd.android.elm.entity.OBDResponse
import car.mazda.obd.android.elm.entity.OBDData

class OBDDataMapper {

    private companion object {
        private const val OBD_CAN_ID_INDEX = 0
        private const val OBD_LEN_INDEX = 1
        private const val OBD_MODE_INDEX = 2
        private const val OBD_PID_INDEX = 3

        private const val OBD_HEADER_TOKENS = 4
        private const val OBD_MODE_BYTES = 1
        private const val OBD_PID_BYTES = 1

        private const val OBD_MIN_PAYLOAD_BYTES = OBD_MODE_BYTES + OBD_PID_BYTES

        private const val OBD_RESPONSE_MODE_PREFIX = "4"
        private const val OBD_SERVICE_SEARCHING = "SEARCHING"
        private const val OBD_SERVICE_NO_DATA = "NO DATA"
        private const val OBD_SERVICE_OK = "OK"
        private const val OBD_SERVICE_ERROR = "ERROR"

        private const val ELM_PROMPT = ">"

    }

    fun map(raw: String): OBDResponse {
        return try {
            val hasSearching = raw.contains("SEARCHING", ignoreCase = true)
            val hasNoData = raw.contains("NO DATA", ignoreCase = true)

            val responses = parseResponses(raw)

            when {
                responses.isNotEmpty() -> {
                    OBDResponse.Data(responses)
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
            if (upper == OBD_SERVICE_OK) return@forEach
            if (upper.contains(OBD_SERVICE_ERROR)) return@forEach
            if (ln == ELM_PROMPT) return@forEach

            val parts = ln.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (parts.size < OBD_HEADER_TOKENS) return@forEach

            val canId = parts[OBD_CAN_ID_INDEX]

            val len = parts[OBD_LEN_INDEX].toIntOrNull(16) ?: return@forEach
            if (len < OBD_MIN_PAYLOAD_BYTES) return@forEach

            val mode = parts[OBD_MODE_INDEX]
            if (!mode.startsWith(OBD_RESPONSE_MODE_PREFIX)) return@forEach

            val pid = parts[OBD_PID_INDEX]

            val payloadBytesCount = len - OBD_MODE_BYTES - OBD_PID_BYTES

            val availablePayloadBytes = parts.size - OBD_HEADER_TOKENS
            if (availablePayloadBytes < payloadBytesCount) return@forEach

            val data = parts
                .drop(OBD_HEADER_TOKENS)
                .take(payloadBytesCount)

            result += OBDData(
                canId = canId,
                pid = pid,
                data = data
            )
        }

        return result
    }

}


