package car.mazda.obd.android.feature.trip.summary

data class TripSummary(
    val id: Long = 0L,
    val startedAtMs: Long,
    val finishedAtMs: Long,
    val maxRpm: Int,
    val maxEngineTempCelsius: Int?,
    val debugEvents: List<TripDebugEvent> = emptyList(),
) {
    val durationMs: Long
        get() = (finishedAtMs - startedAtMs).coerceAtLeast(0L)
}

data class TripDebugEvent(
    val id: Long = 0L,
    val tripId: Long = 0L,
    val occurredAtMs: Long,
    val level: String,
    val message: String,
) {
    val line: String
        get() = "$level $message"
}

data class ActiveTripSummary(
    val startedAtMs: Long,
    val maxRpm: Int,
    val maxEngineTempCelsius: Int?,
) {
    fun durationMs(nowMs: Long): Long =
        (nowMs - startedAtMs).coerceAtLeast(0L)
}
