package car.mazda.obd.android.feature.trip.summary

data class TripSummary(
    val id: Long = 0L,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val maxRpm: Int,
    val maxEngineTempCelsius: Int?,
) {
    val durationMs: Long
        get() = (finishedAtMs - startedAtMs).coerceAtLeast(0L)
}

data class ActiveTripSummary(
    val startedAtMs: Long,
    val maxRpm: Int,
    val maxEngineTempCelsius: Int?,
) {
    fun durationMs(nowMs: Long): Long =
        (nowMs - startedAtMs).coerceAtLeast(0L)
}
