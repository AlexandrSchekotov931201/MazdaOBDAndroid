package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.OBDData

data class VehicleCapabilities(
    val discoveryComplete: Boolean = false,
    val supportedPids: Set<Int> = emptySet(),
) {
    fun supports(pid: Int): Boolean = !discoveryComplete || pid in supportedPids

    companion object {
        val Unknown = VehicleCapabilities()
    }
}

internal object SupportedPidDecoder {
    fun decode(basePid: Int, responses: List<OBDData>): Set<Int> = buildSet {
        responses.forEach { response ->
            val bytes = response.data.take(4).mapNotNull { it.toIntOrNull(16) }
            if (bytes.size != 4) return@forEach

            bytes.forEachIndexed { byteIndex, value ->
                for (bitIndex in 0 until 8) {
                    if (value and (0x80 shr bitIndex) != 0) {
                        add(basePid + byteIndex * 8 + bitIndex + 1)
                    }
                }
            }
        }
    }
}
