package car.mazda.obd.android.feature.warmup

object EngineWarmupGuidance {
    const val FULLY_WARM_TEMP_CELSIUS = 85
    const val CRITICAL_TEMP_CELSIUS = 105

    fun stageFor(coolantTempCelsius: Int): EngineWarmupStage =
        when {
            coolantTempCelsius >= CRITICAL_TEMP_CELSIUS -> EngineWarmupStage.Critical
            coolantTempCelsius >= FULLY_WARM_TEMP_CELSIUS -> EngineWarmupStage.FullyWarm
            coolantTempCelsius >= 75 -> EngineWarmupStage.NormalCity
            coolantTempCelsius >= 60 -> EngineWarmupStage.Gentle
            else -> EngineWarmupStage.VeryGentle
        }
}

sealed class EngineWarmupStage(
    val title: String,
    val detail: String,
    val recommendedRpmLimit: Int?,
) {
    data object VeryGentle : EngineWarmupStage(
        title = "Cold engine",
        detail = "drive very gently, keep under 2500 rpm",
        recommendedRpmLimit = 2500,
    )

    data object Gentle : EngineWarmupStage(
        title = "Warming up",
        detail = "drive smoothly, avoid kickdown, keep under 3000 rpm",
        recommendedRpmLimit = 3000,
    )

    data object NormalCity : EngineWarmupStage(
        title = "Normal city driving",
        detail = "normal driving is OK, avoid hard launches, keep under 3500 rpm",
        recommendedRpmLimit = 3500,
    )

    data object FullyWarm : EngineWarmupStage(
        title = "Fully warm",
        detail = "normal driving range",
        recommendedRpmLimit = null,
    )

    data object Critical : EngineWarmupStage(
        title = "Critical temperature",
        detail = "coolant temperature is too high",
        recommendedRpmLimit = null,
    )
}
