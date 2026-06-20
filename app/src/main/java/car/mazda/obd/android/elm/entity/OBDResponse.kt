package car.mazda.obd.android.elm.entity

sealed class OBDResponse {
    data class Data(val data: List<OBDData>) : OBDResponse()
    sealed class NoData(open val raw: String) : OBDResponse() {
        data class Searching(override val raw: String) : NoData(raw)
        data class Empty(override val raw: String) : NoData(raw)
        data class Unrecognized(override val raw: String) : NoData(raw)
        data class Error(override val raw: String, val throwable: Throwable) : NoData(raw)
    }
}