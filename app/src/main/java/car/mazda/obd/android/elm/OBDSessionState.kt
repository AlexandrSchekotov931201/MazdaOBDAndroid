package car.mazda.obd.android.elm

sealed class OBDSessionState {
    object Idle : OBDSessionState()
    object ConnectingSocket : OBDSessionState()
    object InitializingEcu : OBDSessionState()
    object Ready : OBDSessionState()
    data class Error(val throwable: Throwable) : OBDSessionState()
}