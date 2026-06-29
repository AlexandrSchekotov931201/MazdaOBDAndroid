package car.mazda.obd.android.core.elm.entity

sealed class ElmCommand(val value: String) {
    data object Reset : ElmCommand("ATZ")
    data object EchoOff : ElmCommand("ATE0")
    data object LineFeedsOff : ElmCommand("ATL0")
    data object HeadersOn : ElmCommand("ATH1")
    data object AutoProtocol : ElmCommand("ATSP0")
    data class SetHeader(val canId: String) : ElmCommand("ATSH$canId")
    data object Identify : ElmCommand("ATI")
    data object DeviceDescription : ElmCommand("AT@1")
    data object ReadVoltage : ElmCommand("ATRV")
    data object DescribeProtocol : ElmCommand("ATDP")
    data object DescribeProtocolNumber : ElmCommand("ATDPN")
    data object CanStatus : ElmCommand("ATCS")

    companion object {
        val defaultInitSequence = listOf(
            Reset,
            EchoOff,
            LineFeedsOff,
            HeadersOn,
            AutoProtocol,
        )
    }
}
