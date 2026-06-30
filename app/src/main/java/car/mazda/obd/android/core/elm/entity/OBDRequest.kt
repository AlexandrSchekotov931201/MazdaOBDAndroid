package car.mazda.obd.android.core.elm.entity

sealed class OBDRequest(val value: String, val pid: Int) {
    val responsePidHex: String = pid.toString(16).uppercase().padStart(2, '0')

    init {
        require(pid in 0x00..0xFF) { "OBD PID must fit in one byte" }
    }

    data class SupportedPids(val basePid: Int) : OBDRequest(
        "01${basePid.toString(16).uppercase().padStart(2, '0')}",
        basePid,
    ) {
        init {
            require(basePid in 0x00..0xE0 && basePid % 0x20 == 0) {
                "Supported PID base must be 00, 20, ..., E0"
            }
        }
    }
    data object EngineCoolantTemperature : OBDRequest("0105", 0x05)
    data object EngineRpm : OBDRequest("010C", 0x0C)
}
