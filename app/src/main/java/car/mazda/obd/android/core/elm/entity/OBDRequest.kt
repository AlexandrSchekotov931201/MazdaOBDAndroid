package car.mazda.obd.android.core.elm.entity

enum class OBDService(val requestCode: Int) {
    CurrentData(0x01),
}

enum class SupportedPidRange(val basePid: Int) {
    Pids01To20(0x00),
    Pids21To40(0x20),
    Pids41To60(0x40),
    Pids61To80(0x60),
    Pids81ToA0(0x80),
    PidsA1ToC0(0xA0),
    PidsC1ToE0(0xC0),
    PidsE1ToFF(0xE0),
    ;

    companion object {
        fun containing(pid: Int): SupportedPidRange = entries.first { range ->
            pid in (range.basePid + 1)..minOf(range.basePid + 0x20, 0xFF)
        }
    }
}

sealed class OBDRequest(
    val service: OBDService,
    val pid: Int,
) {
    val value: String = service.requestCode.hexByte() + pid.hexByte()
    val responsePidHex: String = pid.hexByte()

    data class SupportedPids(val range: SupportedPidRange) : OBDRequest(
        service = OBDService.CurrentData,
        pid = range.basePid,
    )

    data object EngineCoolantTemperature : OBDRequest(OBDService.CurrentData, 0x05)
    data object EngineRpm : OBDRequest(OBDService.CurrentData, 0x0C)
}

private fun Int.hexByte(): String = toString(16).uppercase().padStart(2, '0')
