package car.mazda.obd.android.core.elm.entity

sealed class OBDRequest(val value: String) {
    data object SupportedPids : OBDRequest("0100")
    data object EngineCoolantTemperature : OBDRequest("0105")
    data object EngineOilTemperature : OBDRequest("015C")
    data object EngineRpm : OBDRequest("010C")
}
