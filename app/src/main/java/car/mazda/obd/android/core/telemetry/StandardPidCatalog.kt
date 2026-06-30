package car.mazda.obd.android.core.telemetry

import car.mazda.obd.android.core.elm.entity.OBDRequest

object StandardPidCatalog {
    val EngineRpm = PollingTarget(
        metric = TelemetryMetric.EngineRpm,
        request = OBDRequest.EngineRpm,
        periodMs = 250L,
    )

    val CoolantTemperature = PollingTarget(
        metric = TelemetryMetric.CoolantTemperature,
        request = OBDRequest.EngineCoolantTemperature,
        periodMs = 1_000L,
    )

    val Default: List<PollingTarget> = listOf(
        EngineRpm,
        CoolantTemperature,
    )
}
