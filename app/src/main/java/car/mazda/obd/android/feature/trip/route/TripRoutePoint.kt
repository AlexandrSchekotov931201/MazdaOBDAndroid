package car.mazda.obd.android.feature.trip.route

data class TripRoutePoint(
    val id: Long = 0L,
    val tripStartedAtMs: Long,
    val recordedAtMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val engineRpm: Int,
    val coolantTempCelsius: Int?,
    val segment: Int,
)
