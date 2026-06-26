package car.mazda.obd.android.feature.trip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TripStateManagerTest {

    @Test
    fun finishesTripSoonWhenRpmDataStaysUnavailable() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = TripStateManager(
            scope = scope,
            engineOffDelayMs = 20L,
        )

        try {
            manager.onRpmSample(EngineRpmSample.Value(900))
            manager.onRpmSample(EngineRpmSample.NoData)

            assertEquals(TripState.Finishing, manager.tripState.value)

            delay(40L)

            assertEquals(TripState.Idle, manager.tripState.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun resumesTripWhenRpmRecoversBeforeFinishDelay() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = TripStateManager(
            scope = scope,
            engineOffDelayMs = 50L,
        )

        try {
            manager.onRpmSample(EngineRpmSample.Value(900))
            manager.onRpmSample(EngineRpmSample.NoData)
            manager.onRpmSample(EngineRpmSample.Value(850))

            delay(70L)

            assertEquals(TripState.Active, manager.tripState.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun finishesTripAfterEngineOffDelayWhenConnectionErrorHidesRpm() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = TripStateManager(
            scope = scope,
            engineOffDelayMs = 20L,
        )

        try {
            manager.onRpmSample(EngineRpmSample.Value(900))
            manager.onRpmSample(EngineRpmSample.ConnectionError(RuntimeException("timeout")))

            assertEquals(TripState.Finishing, manager.tripState.value)

            delay(40L)

            assertEquals(TripState.Idle, manager.tripState.value)
        } finally {
            scope.cancel()
        }
    }
}
