package car.mazda.obd.android.feature.warmup

import car.mazda.obd.android.core.logs.AppLogger

class WarmupWarningManager(
    private val warmupReadyTempCelsius: Int = 75,
    private val warmupRpmLimit: Int = 2000,
    private val overheatTempCelsius: Int = 105,
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

        if (temp >= overheatTempCelsius) {
            lastWarmupWarningAt = 0L
            return warningIfRepeatIntervalPassed(
                lastAt = lastOverheatWarningAt,
                updateLastAt = { lastOverheatWarningAt = it },
                repeatIntervalMs = overheatRepeatIntervalMs,
                warning = WarmupWarning.Overheat(temp),
            )
        }

        if (temp < warmupReadyTempCelsius && rpm > warmupRpmLimit) {
            lastOverheatWarningAt = 0L
            return warningIfRepeatIntervalPassed(
                lastAt = lastWarmupWarningAt,
                updateLastAt = { lastWarmupWarningAt = it },
                repeatIntervalMs = warmupRepeatIntervalMs,
                warning = WarmupWarning.HighRpmWhileCold(
                    rpm = rpm,
                    coolantTempCelsius = temp,
                    rpmLimit = warmupRpmLimit,
                    readyTempCelsius = warmupReadyTempCelsius,
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
    data class HighRpmWhileCold(
        val rpm: Int,
        val coolantTempCelsius: Int,
        val rpmLimit: Int,
        val readyTempCelsius: Int,
    ) : WarmupWarning(
        "Warmup warning: rpm=$rpm above $rpmLimit while coolant=${coolantTempCelsius}C, ready at ${readyTempCelsius}C"
    )

    data class Overheat(
        val coolantTempCelsius: Int,
    ) : WarmupWarning(
        "Critical temperature warning: coolant=${coolantTempCelsius}C"
    )
}
