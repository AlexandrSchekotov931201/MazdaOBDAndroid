package car.mazda.obd.android.core.elm.entity

sealed class ElmCommand(
    val value: String,
    val required: Boolean = true,
) {
    data object Reset : ElmCommand("ATZ")
    data object EchoOff : ElmCommand("ATE0")
    data object LineFeedsOff : ElmCommand("ATL0")
    data object SpacesOn : ElmCommand("ATS1", required = false)
    data object HeadersOn : ElmCommand("ATH1")
    data object AdaptiveTiming : ElmCommand("ATAT1", required = false)
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
            SpacesOn,
            HeadersOn,
            AdaptiveTiming,
            AutoProtocol,
        )
    }
}
