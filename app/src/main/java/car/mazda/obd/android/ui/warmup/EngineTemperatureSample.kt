package car.mazda.obd.android.ui.warmup

sealed class EngineTemperatureSample {
    data class Value(val celsius: Int) : EngineTemperatureSample()
    data object NoData : EngineTemperatureSample()
    data class ConnectionError(val throwable: Throwable) : EngineTemperatureSample()
}
