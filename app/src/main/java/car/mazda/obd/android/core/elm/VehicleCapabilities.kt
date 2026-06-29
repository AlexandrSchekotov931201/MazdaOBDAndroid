package car.mazda.obd.android.core.elm

import car.mazda.obd.android.core.elm.entity.OBDData

data class VehicleCapabilities(
    val discoveredRanges: Set<Int> = emptySet(),
    val supportedPidsByEcu: Map<String, Set<Int>> = emptyMap(),
) {
    val supportedPids: Set<Int>
        get() = supportedPidsByEcu.values.flatten().toSet()

    fun hasDiscoveryFor(pid: Int): Boolean = rangeFor(pid) in discoveredRanges

    fun supports(pid: Int): Boolean = !hasDiscoveryFor(pid) || pid in supportedPids

    fun preferredEcuFor(pid: Int): String? = supportedPidsByEcu.entries
        .asSequence()
        .filter { pid in it.value }
        .sortedBy { it.key }
        .map { it.key }
        .firstOrNull()

    companion object {
        val Unknown = VehicleCapabilities()

        fun rangeFor(pid: Int): Int {
            require(pid in 0x01..0xFF)
            return ((pid - 1) / 0x20) * 0x20
        }
    }
}

internal object SupportedPidDecoder {
    fun decodeByEcu(basePid: Int, responses: List<OBDData>): Map<String, Set<Int>> =
        responses.groupBy { it.canId.uppercase() }
            .mapValues { (_, ecuResponses) ->
                ecuResponses.flatMapTo(mutableSetOf()) { decode(basePid, it) }
            }

    fun decode(basePid: Int, response: OBDData): Set<Int> {
        val bytes = response.data.take(4).mapNotNull { it.toIntOrNull(16) }
        if (bytes.size != 4) return emptySet()

        return buildSet {
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
