package car.mazda.obd.android.core.elm

sealed class OBDSessionState {
    object Idle : OBDSessionState()
    object ConnectingSocket : OBDSessionState()
    object Reconnecting : OBDSessionState()
    object InitializingEcu : OBDSessionState()
    object Ready : OBDSessionState()
    data class Error(val throwable: Throwable) : OBDSessionState()
}
