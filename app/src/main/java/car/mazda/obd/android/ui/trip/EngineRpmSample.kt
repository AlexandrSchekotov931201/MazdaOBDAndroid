package car.mazda.obd.android.ui.trip

sealed class EngineRpmSample {
    data class Value(val rpm: Int) : EngineRpmSample()
    data object NoData : EngineRpmSample()
    data class ConnectionError(val throwable: Throwable) : EngineRpmSample()
}
