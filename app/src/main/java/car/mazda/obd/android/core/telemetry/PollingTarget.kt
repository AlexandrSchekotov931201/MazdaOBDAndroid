package car.mazda.obd.android.core.telemetry

import car.mazda.obd.android.core.elm.entity.OBDRequest

data class PollingTarget(
    val metric: TelemetryMetric,
    val request: OBDRequest,
    val periodMs: Long,
)
