package car.mazda.obd.android.elm.entity

sealed class OBDRequest(val value: String) {
    data object SupportedPids : OBDRequest("0100")
    data object EngineRpm : OBDRequest("010C")
}
