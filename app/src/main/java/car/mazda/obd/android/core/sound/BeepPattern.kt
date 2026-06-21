package car.mazda.obd.android.core.sound

data class BeepPattern(
    val count: Int,
    val toneDurationMs: Int,
    val pauseMs: Long
)
