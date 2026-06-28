package car.mazda.obd.android.feature.trip.route

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteStatistics(
    val distanceMeters: Double,
    val maximumSpeedMetersPerSecond: Float?,
    val recordedPointCount: Int,
)

object RouteStatisticsCalculator {
    fun calculate(points: List<TripRoutePoint>): RouteStatistics {
        val distance = points.zipWithNext().sumOf { (first, second) ->
            if (first.segment != second.segment) 0.0
            else distanceMeters(first.latitude, first.longitude, second.latitude, second.longitude).toDouble()
        }
        return RouteStatistics(
            distanceMeters = distance,
            maximumSpeedMetersPerSecond = points.mapNotNull { it.speedMetersPerSecond }.maxOrNull(),
            recordedPointCount = points.size,
        )
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val latDelta = Math.toRadians(lat2 - lat1)
        val lonDelta = Math.toRadians(lon2 - lon1)
        val startLat = Math.toRadians(lat1)
        val endLat = Math.toRadians(lat2)
        val a = sin(latDelta / 2) * sin(latDelta / 2) +
            cos(startLat) * cos(endLat) * sin(lonDelta / 2) * sin(lonDelta / 2)
        return (EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))).toFloat()
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
