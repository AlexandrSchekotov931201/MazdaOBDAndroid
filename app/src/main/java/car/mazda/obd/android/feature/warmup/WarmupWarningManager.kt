package car.mazda.obd.android.feature.warmup

import car.mazda.obd.android.core.logs.AppLogger

class WarmupWarningManager(
    private val warmupReadyTempCelsius: Int = 75,
    private val warmupRpmLimit: Int = 2000,
    private val overheatTempCelsius: Int = 105,
    private val warningCooldownMs: Long = 20_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastWarmupWarningAt = 0L
    private var lastOverheatWarningAt = 0L

    fun onEngineData(rpm: Int, coolantTempCelsius: Int?): WarmupWarning? {
        val temp = coolantTempCelsius ?: return null

        if (temp >= overheatTempCelsius) {
            return warningIfCooldownPassed(
                lastAt = lastOverheatWarningAt,
                updateLastAt = { lastOverheatWarningAt = it },
                warning = WarmupWarning.Overheat(temp),
            )
        }

        if (temp < warmupReadyTempCelsius && rpm > warmupRpmLimit) {
            return warningIfCooldownPassed(
                lastAt = lastWarmupWarningAt,
                updateLastAt = { lastWarmupWarningAt = it },
                warning = WarmupWarning.HighRpmWhileCold(
                    rpm = rpm,
                    coolantTempCelsius = temp,
                    rpmLimit = warmupRpmLimit,
                    readyTempCelsius = warmupReadyTempCelsius,
                ),
            )
        }

        return null
    }

    private fun warningIfCooldownPassed(
        lastAt: Long,
        updateLastAt: (Long) -> Unit,
        warning: WarmupWarning,
    ): WarmupWarning? {
        val now = clock()
        if (lastAt != 0L && now - lastAt < warningCooldownMs) return null

        updateLastAt(now)
        AppLogger.log(warning.logMessage)
        return warning
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
