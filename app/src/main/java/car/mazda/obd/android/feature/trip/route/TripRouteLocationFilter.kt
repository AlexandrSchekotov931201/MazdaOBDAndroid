package car.mazda.obd.android.feature.trip.route

import car.mazda.obd.android.feature.location.DeviceLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class TripRouteLocationFilter(
    private val maximumAccuracyMeters: Float = 35f,
    private val stationarySpeedMetersPerSecond: Float = 1f,
    private val minimumDisplacementMeters: Double = 20.0,
    private val maximumPlausibleSpeedMetersPerSecond: Double = 70.0,
) {
    private var lastAccepted: DeviceLocation? = null
    private var wasMoving = false

    fun accept(location: DeviceLocation): Boolean {
        if (!location.hasValidCoordinates()) return false
        if (location.accuracyMeters < 0f || location.accuracyMeters > maximumAccuracyMeters) return false
        if (location.speedMetersPerSecond?.let { it < 0f || it > maximumPlausibleSpeedMetersPerSecond } == true) return false

        val previous = lastAccepted
        if (previous == null) {
            lastAccepted = location
            wasMoving = location.speedMetersPerSecond?.let { it >= stationarySpeedMetersPerSecond } == true
            return true
        }

        val elapsedSeconds = (location.recordedAtMs - previous.recordedAtMs) / 1_000.0
        if (elapsedSeconds <= 0.0) return false
        val distanceMeters = distanceMeters(previous, location)
        val requiredDisplacement = max(
            minimumDisplacementMeters,
            previous.accuracyMeters + location.accuracyMeters.toDouble(),
        )
        if (distanceMeters < requiredDisplacement) return false
        if (distanceMeters / elapsedSeconds > maximumPlausibleSpeedMetersPerSecond) return false

        val isStationary = location.speedMetersPerSecond?.let { it < stationarySpeedMetersPerSecond } == true
        if (isStationary && !wasMoving) return false

        lastAccepted = location
        wasMoving = !isStationary
        return true
    }

    private fun DeviceLocation.hasValidCoordinates(): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    private fun distanceMeters(first: DeviceLocation, second: DeviceLocation): Double {
        val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
        val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitude) * cos(secondLatitude) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
