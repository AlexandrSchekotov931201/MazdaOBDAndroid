package car.mazda.obd.android.feature.warmup

import car.mazda.obd.android.core.logs.AppLogger

class WarmupWarningManager(
    private val warmupRepeatIntervalMs: Long = 1_000L,
    private val overheatRepeatIntervalMs: Long = 1_500L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastWarmupWarningAt = 0L
    private var lastOverheatWarningAt = 0L

    fun onEngineData(rpm: Int, coolantTempCelsius: Int?): WarmupWarning? {
        val temp = coolantTempCelsius ?: run {
            resetWarnings()
            return null
        }

        val stage = EngineWarmupGuidance.stageFor(temp)

        if (stage == EngineWarmupStage.Critical) {
            lastWarmupWarningAt = 0L
            return warningIfRepeatIntervalPassed(
                lastAt = lastOverheatWarningAt,
                updateLastAt = { lastOverheatWarningAt = it },
                repeatIntervalMs = overheatRepeatIntervalMs,
                warning = WarmupWarning.Overheat(temp),
            )
        }

        val rpmLimit = stage.recommendedRpmLimit
        if (rpmLimit != null && rpm > rpmLimit) {
            lastOverheatWarningAt = 0L
            return warningIfRepeatIntervalPassed(
                lastAt = lastWarmupWarningAt,
                updateLastAt = { lastWarmupWarningAt = it },
                repeatIntervalMs = warmupRepeatIntervalMs,
                warning = WarmupWarning.HighRpmForTemperature(
                    rpm = rpm,
                    coolantTempCelsius = temp,
                    rpmLimit = rpmLimit,
                    stageTitle = stage.title,
                ),
            )
        }

        resetWarnings()
        return null
    }

    private fun warningIfRepeatIntervalPassed(
        lastAt: Long,
        updateLastAt: (Long) -> Unit,
        repeatIntervalMs: Long,
        warning: WarmupWarning,
    ): WarmupWarning? {
        val now = clock()
        if (lastAt != 0L && now - lastAt < repeatIntervalMs) return null

        updateLastAt(now)
        AppLogger.log(warning.logMessage)
        return warning
    }

    private fun resetWarnings() {
        lastWarmupWarningAt = 0L
        lastOverheatWarningAt = 0L
    }
}

sealed class WarmupWarning(val logMessage: String) {
    data class HighRpmForTemperature(
        val rpm: Int,
        val coolantTempCelsius: Int,
        val rpmLimit: Int,
        val stageTitle: String,
    ) : WarmupWarning(
        "Warmup warning: rpm=$rpm above $rpmLimit while coolant=${coolantTempCelsius}C, stage=$stageTitle"
    )

    data class Overheat(
        val coolantTempCelsius: Int,
    ) : WarmupWarning(
        "Critical temperature warning: coolant=${coolantTempCelsius}C"
    )
}
