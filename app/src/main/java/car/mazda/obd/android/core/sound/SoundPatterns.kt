package car.mazda.obd.android.core.sound

object SoundPatterns {
    val LongAlert = BeepPattern(
        count = 1,
        toneDurationMs = 500,
        pauseMs = 0
    )

    val TripleShortAlert = BeepPattern(
        count = 3,
        toneDurationMs = 150,
        pauseMs = 150
    )

    val TripleLongAlert = BeepPattern(
        count = 3,
        toneDurationMs = 500,
        pauseMs = 200
    )

    val RapidAlert = BeepPattern(
        count = 10,
        toneDurationMs = 60,
        pauseMs = 70
    )
}
