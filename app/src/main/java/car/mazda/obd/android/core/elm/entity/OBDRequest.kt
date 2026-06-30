package car.mazda.obd.android.core.elm.entity

enum class OBDService(val requestCode: Int) {
    CURRENT_DATA(0x01),
}

enum class StandardPid(val code: Int) {
    ENGINE_COOLANT_TEMPERATURE(0x05),
    ENGINE_RPM(0x0C),
}

enum class SupportedPidRange(val basePid: Int) {
    PIDS_01_TO_20(0x00),
    PIDS_21_TO_40(0x20),
    PIDS_41_TO_60(0x40),
    PIDS_61_TO_80(0x60),
    PIDS_81_TO_A0(0x80),
    PIDS_A1_TO_C0(0xA0),
    PIDS_C1_TO_E0(0xC0),
    PIDS_E1_TO_FF(0xE0),
    ;

    companion object {
        fun containing(pid: Int): SupportedPidRange = entries.first { range ->
            pid in (range.basePid + 1)..minOf(range.basePid + 0x20, 0xFF)
        }
    }
}

sealed class OBDRequest(
    val service: OBDService,
    val pidCode: Int,
) {
    val value: String = service.requestCode.hexByte() + pidCode.hexByte()
    val responsePidHex: String = pidCode.hexByte()

    data class SupportedPids(val range: SupportedPidRange) : OBDRequest(
        service = OBDService.CURRENT_DATA,
        pidCode = range.basePid,
    )

    data class CurrentData(val pid: StandardPid) : OBDRequest(
        service = OBDService.CURRENT_DATA,
        pidCode = pid.code,
    )
}

private fun Int.hexByte(): String = toString(16).uppercase().padStart(2, '0')
