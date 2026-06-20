package car.mazda.obd.android.elm.entity

sealed class ElmCommand(val value: String) {
    data object Reset : ElmCommand("ATZ")
    data object EchoOff : ElmCommand("ATE0")
    data object LineFeedsOff : ElmCommand("ATL0")
    data object HeadersOn : ElmCommand("ATH1")
    data object AutoProtocol : ElmCommand("ATSP0")
    data class SetHeader(val canId: String) : ElmCommand("ATSH$canId")

    companion object {
        val defaultInitSequence = listOf(
            Reset,
            EchoOff,
            LineFeedsOff,
            HeadersOn,
            AutoProtocol,
            SetHeader(CanIds.FUNCTIONAL_REQUEST),
        )
    }
}
