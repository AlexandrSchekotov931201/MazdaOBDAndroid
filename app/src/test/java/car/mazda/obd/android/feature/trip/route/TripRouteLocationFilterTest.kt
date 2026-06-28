package car.mazda.obd.android.feature.trip.route

import car.mazda.obd.android.feature.location.DeviceLocation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRouteLocationFilterTest {
    @Test
    fun `rejects inaccurate first point`() {
        val filter = TripRouteLocationFilter()

        assertFalse(filter.accept(location(accuracy = 50f)))
    }

    @Test
    fun `rejects stationary GPS drift`() {
        val filter = TripRouteLocationFilter()

        assertTrue(filter.accept(location(speed = 0f)))
        assertFalse(filter.accept(location(latitude = 55.75015, timeMs = 5_000L, speed = 0f)))
        assertFalse(filter.accept(location(latitude = 55.75040, timeMs = 10_000L, speed = 0f)))
    }

    @Test
    fun `accepts real movement after stationary start`() {
        val filter = TripRouteLocationFilter()

        assertTrue(filter.accept(location(speed = 0f)))
        assertTrue(filter.accept(location(latitude = 55.75030, timeMs = 5_000L, speed = 7f)))
    }

    @Test
    fun `accepts one final stationary point after movement`() {
        val filter = TripRouteLocationFilter()

        assertTrue(filter.accept(location(speed = 0f)))
        assertTrue(filter.accept(location(latitude = 55.75030, timeMs = 5_000L, speed = 7f)))
        assertTrue(filter.accept(location(latitude = 55.75060, timeMs = 10_000L, speed = 0f)))
        assertFalse(filter.accept(location(latitude = 55.75100, timeMs = 15_000L, speed = 0f)))
    }

    @Test
    fun `rejects implausible jump`() {
        val filter = TripRouteLocationFilter()

        assertTrue(filter.accept(location(speed = 0f)))
        assertFalse(filter.accept(location(latitude = 56.0, timeMs = 5_000L, speed = 10f)))
    }

    private fun location(
        latitude: Double = 55.75,
        longitude: Double = 37.61,
        timeMs: Long = 0L,
        accuracy: Float = 5f,
        speed: Float? = null,
    ) = DeviceLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        recordedAtMs = timeMs,
    )
}
