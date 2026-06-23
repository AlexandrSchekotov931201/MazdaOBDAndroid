package car.mazda.obd.android.feature.trip.summary

import car.mazda.obd.android.feature.trip.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TripSummaryTrackerTest {

    @Test
    fun startsActiveTripWhenTripBecomesActive() {
        var now = 1_000L
        val tracker = TripSummaryTracker(clock = { now })

        val finished = tracker.onTripStateChanged(TripState.Active)

        assertNull(finished)
        assertNotNull(tracker.activeTrip.value)
        assertEquals(1_000L, tracker.activeTrip.value?.startedAtMs)
    }

    @Test
    fun tracksMaxValuesUntilTripFinishes() {
        var now = 1_000L
        val tracker = TripSummaryTracker(clock = { now })

        tracker.onTripStateChanged(TripState.Active)
        tracker.onRpmChanged(850)
        tracker.onRpmChanged(3_200)
        tracker.onRpmChanged(2_100)
        tracker.onEngineTemperatureChanged(40)
        tracker.onEngineTemperatureChanged(89)
        tracker.onEngineTemperatureChanged(75)

        now = 121_000L
        val summary = tracker.onTripStateChanged(TripState.Idle)

        assertNotNull(summary)
        assertEquals(1_000L, summary?.startedAtMs)
        assertEquals(121_000L, summary?.finishedAtMs)
        assertEquals(120_000L, summary?.durationMs)
        assertEquals(3_200, summary?.maxRpm)
        assertEquals(89, summary?.maxEngineTempCelsius)
        assertNull(tracker.activeTrip.value)
    }

    @Test
    fun doesNotFinishTripWhileTripIsFinishing() {
        var now = 1_000L
        val tracker = TripSummaryTracker(clock = { now })

        tracker.onTripStateChanged(TripState.Active)
        now = 5_000L
        val finishingSummary = tracker.onTripStateChanged(TripState.Finishing)

        assertNull(finishingSummary)
        assertNotNull(tracker.activeTrip.value)

        now = 6_000L
        val finishedSummary = tracker.onTripStateChanged(TripState.Idle)

        assertNotNull(finishedSummary)
        assertEquals(5_000L, finishedSummary?.durationMs)
    }
}
