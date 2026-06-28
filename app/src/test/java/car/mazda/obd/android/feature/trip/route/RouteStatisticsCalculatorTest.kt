package car.mazda.obd.android.feature.trip.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteStatisticsCalculatorTest {
    @Test
    fun `empty route has zero statistics`() {
        val result = RouteStatisticsCalculator.calculate(emptyList())

        assertEquals(0.0, result.distanceMeters, 0.0)
        assertEquals(0, result.recordedPointCount)
        assertNull(result.maximumSpeedMetersPerSecond)
    }

    @Test
    fun `distance does not bridge different GPS segments`() {
        val points = listOf(
            point(latitude = 55.7500, longitude = 37.6100, segment = 0),
            point(latitude = 55.7510, longitude = 37.6100, segment = 0),
            point(latitude = 56.0000, longitude = 38.0000, segment = 1),
        )

        val result = RouteStatisticsCalculator.calculate(points)

        assertEquals(111.2, result.distanceMeters, 1.0)
    }

    @Test
    fun `maximum speed and point count use all recorded points`() {
        val points = listOf(
            point(speed = 3f),
            point(speed = null),
            point(speed = 12.5f),
        )

        val result = RouteStatisticsCalculator.calculate(points)

        assertEquals(3, result.recordedPointCount)
        assertEquals(12.5f, result.maximumSpeedMetersPerSecond)
    }

    private fun point(
        latitude: Double = 55.75,
        longitude: Double = 37.61,
        segment: Int = 0,
        speed: Float? = null,
    ) = TripRoutePoint(
        tripStartedAtMs = 1L,
        recordedAtMs = 1L,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 5f,
        speedMetersPerSecond = speed,
        engineRpm = 1_000,
        coolantTempCelsius = 70,
        segment = segment,
    )
}
