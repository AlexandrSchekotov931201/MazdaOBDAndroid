package car.mazda.obd.android.feature.monitor

data class ObdConnectionSettings(
    val rpmPollPeriodMs: Long = DEFAULT_RPM_POLL_PERIOD_MS,
    val coolantPollPeriodMs: Long = DEFAULT_COOLANT_POLL_PERIOD_MS,
    val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    val networkRequestTimeoutMs: Long = DEFAULT_NETWORK_REQUEST_TIMEOUT_MS,
    val adapterTimeoutReconnectThreshold: Int = DEFAULT_ADAPTER_TIMEOUT_RECONNECT_THRESHOLD,
    val engineOffDelayMs: Long = DEFAULT_ENGINE_OFF_DELAY_MS,
) {
    fun normalized(): ObdConnectionSettings =
        copy(
            rpmPollPeriodMs = rpmPollPeriodMs.coerceIn(MIN_POLL_PERIOD_MS, MAX_POLL_PERIOD_MS),
            coolantPollPeriodMs = coolantPollPeriodMs.coerceIn(MIN_POLL_PERIOD_MS, MAX_POLL_PERIOD_MS),
            connectTimeoutMs = connectTimeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS),
            readTimeoutMs = readTimeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS),
            networkRequestTimeoutMs = networkRequestTimeoutMs.coerceIn(MIN_TIMEOUT_MS.toLong(), MAX_TIMEOUT_MS.toLong()),
            adapterTimeoutReconnectThreshold = adapterTimeoutReconnectThreshold.coerceIn(
                MIN_RECONNECT_THRESHOLD,
                MAX_RECONNECT_THRESHOLD,
            ),
            engineOffDelayMs = engineOffDelayMs.coerceIn(MIN_ENGINE_OFF_DELAY_MS, MAX_ENGINE_OFF_DELAY_MS),
        )

    companion object {
        const val DEFAULT_RPM_POLL_PERIOD_MS = 500L
        const val DEFAULT_COOLANT_POLL_PERIOD_MS = 2_000L
        const val DEFAULT_CONNECT_TIMEOUT_MS = 10_000
        const val DEFAULT_READ_TIMEOUT_MS = 2_000
        const val DEFAULT_NETWORK_REQUEST_TIMEOUT_MS = 3_000L
        const val DEFAULT_ADAPTER_TIMEOUT_RECONNECT_THRESHOLD = 3
        const val DEFAULT_ENGINE_OFF_DELAY_MS = 5_000L

        const val MIN_POLL_PERIOD_MS = 250L
        const val MAX_POLL_PERIOD_MS = 10_000L
        const val MIN_TIMEOUT_MS = 500
        const val MAX_TIMEOUT_MS = 30_000
        const val MIN_RECONNECT_THRESHOLD = 1
        const val MAX_RECONNECT_THRESHOLD = 10
        const val MIN_ENGINE_OFF_DELAY_MS = 1_000L
        const val MAX_ENGINE_OFF_DELAY_MS = 60_000L

        val Default = ObdConnectionSettings()
    }
}
