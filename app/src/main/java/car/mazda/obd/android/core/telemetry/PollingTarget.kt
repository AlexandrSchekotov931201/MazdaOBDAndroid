package car.mazda.obd.android.core.telemetry

import car.mazda.obd.android.core.elm.entity.OBDRequest

data class PollingTarget(
    val metric: TelemetryMetric,
    val request: OBDRequest,
    val pid: Int,
    val periodMs: Long,
) {
    init {
        require(pid in 0x01..0xFF)
        require(periodMs > 0)
        require(request.responsePid.toInt(16) == pid) {
            "Polling target PID must match request response PID"
        }
    }
}
