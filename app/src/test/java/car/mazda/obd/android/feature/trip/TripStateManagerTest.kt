package car.mazda.obd.android.feature.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class TripStateManagerTest {
    @Test
    fun `trip starts and stops only through explicit commands`() {
        val manager = TripStateManager()

        assertEquals(TripState.Idle, manager.tripState.value)
        manager.startTrip()
        assertEquals(TripState.Active, manager.tripState.value)
        manager.stopTrip()
        assertEquals(TripState.Idle, manager.tripState.value)
    }

    @Test
    fun `repeated commands are idempotent`() {
        val manager = TripStateManager()

        manager.startTrip()
        manager.startTrip()
        assertEquals(TripState.Active, manager.tripState.value)
        manager.stopTrip()
        manager.stopTrip()
        assertEquals(TripState.Idle, manager.tripState.value)
    }
}
