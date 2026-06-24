package car.mazda.obd.android.feature.warmup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmupWarningManagerTest {

    @Test
    fun mapsCoolantTemperatureToWarmupStages() {
        assertEquals(EngineWarmupStage.VeryGentle, EngineWarmupGuidance.stageFor(59))
        assertEquals(EngineWarmupStage.Gentle, EngineWarmupGuidance.stageFor(60))
        assertEquals(EngineWarmupStage.NormalCity, EngineWarmupGuidance.stageFor(75))
        assertEquals(EngineWarmupStage.FullyWarm, EngineWarmupGuidance.stageFor(85))
        assertEquals(EngineWarmupStage.Critical, EngineWarmupGuidance.stageFor(105))
    }

    @Test
    fun warnsWhenRpmExceedsCurrentTemperatureStageLimit() {
        var now = 1_000L
        val manager = WarmupWarningManager(clock = { now })

        assertNull(manager.onEngineData(rpm = 2_500, coolantTempCelsius = 55))

        val coldWarning = manager.onEngineData(rpm = 2_501, coolantTempCelsius = 55)
        assertTrue(coldWarning is WarmupWarning.HighRpmForTemperature)
        assertEquals(2_500, (coldWarning as WarmupWarning.HighRpmForTemperature).rpmLimit)

        now += 1_000L
        val cityWarning = manager.onEngineData(rpm = 3_501, coolantTempCelsius = 78)
        assertTrue(cityWarning is WarmupWarning.HighRpmForTemperature)
        assertEquals(3_500, (cityWarning as WarmupWarning.HighRpmForTemperature).rpmLimit)
    }

    @Test
    fun doesNotWarnForFullyWarmTemperatureBelowCritical() {
        val manager = WarmupWarningManager(clock = { 1_000L })

        assertNull(manager.onEngineData(rpm = 4_500, coolantTempCelsius = 90))
    }

    @Test
    fun keepsCriticalTemperatureWarningSeparate() {
        val manager = WarmupWarningManager(clock = { 1_000L })

        val warning = manager.onEngineData(rpm = 900, coolantTempCelsius = 105)

        assertTrue(warning is WarmupWarning.Overheat)
    }
}
