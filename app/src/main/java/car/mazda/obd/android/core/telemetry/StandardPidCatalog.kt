package car.mazda.obd.android.core.telemetry

import car.mazda.obd.android.core.elm.entity.OBDRequest
import car.mazda.obd.android.core.elm.entity.StandardPid

object StandardPidCatalog {
    val EngineRpm = PollingTarget(
        metric = TelemetryMetric.EngineRpm,
        request = OBDRequest.CurrentData(StandardPid.ENGINE_RPM),
        periodMs = 250L,
    )

    val CoolantTemperature = PollingTarget(
        metric = TelemetryMetric.CoolantTemperature,
        request = OBDRequest.CurrentData(StandardPid.ENGINE_COOLANT_TEMPERATURE),
        periodMs = 1_000L,
    )

    val Default: List<PollingTarget> = listOf(
        EngineRpm,
        CoolantTemperature,
    )
}
