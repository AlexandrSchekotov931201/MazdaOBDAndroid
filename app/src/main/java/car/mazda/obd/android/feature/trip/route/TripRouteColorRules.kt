package car.mazda.obd.android.feature.trip.route

enum class RouteColorMode {
    CoolantTemperature,
    EngineRpm,
}

enum class RouteColorBand {
    Unknown,
    Blue,
    Green,
    Yellow,
    Red,
}

data class TemperatureColorSettings(
    val coldBelowCelsius: Int = 50,
    val normalFromCelsius: Int = 70,
    val criticalFromCelsius: Int = 105,
) {
    init {
        require(coldBelowCelsius < normalFromCelsius)
        require(normalFromCelsius < criticalFromCelsius)
    }

    fun bandFor(celsius: Int?): RouteColorBand = when {
        celsius == null -> RouteColorBand.Unknown
        celsius < coldBelowCelsius -> RouteColorBand.Blue
        celsius < normalFromCelsius -> RouteColorBand.Yellow
        celsius < criticalFromCelsius -> RouteColorBand.Green
        else -> RouteColorBand.Red
    }
}

data class RpmColorSettings(
    val cautionFromRpm: Int = 2_500,
    val dangerFromRpm: Int = 4_000,
) {
    init {
        require(cautionFromRpm < dangerFromRpm)
    }

    fun bandFor(rpm: Int?): RouteColorBand = when {
        rpm == null -> RouteColorBand.Unknown
        rpm < cautionFromRpm -> RouteColorBand.Green
        rpm < dangerFromRpm -> RouteColorBand.Yellow
        else -> RouteColorBand.Red
    }
}

fun TripRoutePoint.colorBand(
    mode: RouteColorMode,
    temperatureSettings: TemperatureColorSettings,
    rpmSettings: RpmColorSettings,
): RouteColorBand = when (mode) {
    RouteColorMode.CoolantTemperature -> temperatureSettings.bandFor(coolantTempCelsius)
    RouteColorMode.EngineRpm -> rpmSettings.bandFor(engineRpm)
}
