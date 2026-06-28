package car.mazda.obd.android.feature.trip.route

import org.junit.Assert.assertEquals
import org.junit.Test

class TripRouteColorRulesTest {
    @Test
    fun `temperature defaults classify all boundaries`() {
        val settings = TemperatureColorSettings()

        assertEquals(RouteColorBand.Unknown, settings.bandFor(null))
        assertEquals(RouteColorBand.Blue, settings.bandFor(49))
        assertEquals(RouteColorBand.Yellow, settings.bandFor(50))
        assertEquals(RouteColorBand.Green, settings.bandFor(70))
        assertEquals(RouteColorBand.Red, settings.bandFor(105))
    }

    @Test
    fun `temperature uses configured boundaries`() {
        val settings = TemperatureColorSettings(
            coldBelowCelsius = 40,
            normalFromCelsius = 65,
            criticalFromCelsius = 100,
        )

        assertEquals(RouteColorBand.Yellow, settings.bandFor(40))
        assertEquals(RouteColorBand.Green, settings.bandFor(65))
        assertEquals(RouteColorBand.Red, settings.bandFor(100))
    }

    @Test
    fun `rpm defaults classify all boundaries`() {
        val settings = RpmColorSettings()

        assertEquals(RouteColorBand.Yellow, settings.bandFor(2_499))
        assertEquals(RouteColorBand.Green, settings.bandFor(2_500))
        assertEquals(RouteColorBand.Red, settings.bandFor(4_000))
    }

    @Test
    fun `rpm uses configured boundaries`() {
        val settings = RpmColorSettings(greenFromRpm = 2_000, dangerFromRpm = 3_500)

        assertEquals(RouteColorBand.Green, settings.bandFor(2_000))
        assertEquals(RouteColorBand.Red, settings.bandFor(3_500))
    }
}
