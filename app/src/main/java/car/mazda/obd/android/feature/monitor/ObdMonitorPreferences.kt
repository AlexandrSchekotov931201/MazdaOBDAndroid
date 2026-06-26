package car.mazda.obd.android.feature.monitor

import android.content.Context

class ObdMonitorPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var floatingWidgetEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_WIDGET_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_FLOATING_WIDGET_ENABLED, value).apply()
        }

    var autoStartEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_START_ENABLED, value).apply()
        }

    var floatingWidgetSize: FloatingWidgetSize
        get() = FloatingWidgetSize.fromKey(
            prefs.getString(KEY_FLOATING_WIDGET_SIZE, FloatingWidgetSize.Small.name)
                ?: FloatingWidgetSize.Small.name
        )
        set(value) {
            prefs.edit().putString(KEY_FLOATING_WIDGET_SIZE, value.name).apply()
        }

    var connectionSettings: ObdConnectionSettings
        get() = ObdConnectionSettings(
            rpmPollPeriodMs = prefs.getLong(
                KEY_RPM_POLL_PERIOD_MS,
                ObdConnectionSettings.DEFAULT_RPM_POLL_PERIOD_MS,
            ),
            coolantPollPeriodMs = prefs.getLong(
                KEY_COOLANT_POLL_PERIOD_MS,
                ObdConnectionSettings.DEFAULT_COOLANT_POLL_PERIOD_MS,
            ),
            connectTimeoutMs = prefs.getInt(
                KEY_CONNECT_TIMEOUT_MS,
                ObdConnectionSettings.DEFAULT_CONNECT_TIMEOUT_MS,
            ),
            readTimeoutMs = prefs.getInt(
                KEY_READ_TIMEOUT_MS,
                ObdConnectionSettings.DEFAULT_READ_TIMEOUT_MS,
            ),
            networkRequestTimeoutMs = prefs.getLong(
                KEY_NETWORK_REQUEST_TIMEOUT_MS,
                ObdConnectionSettings.DEFAULT_NETWORK_REQUEST_TIMEOUT_MS,
            ),
            adapterTimeoutReconnectThreshold = prefs.getInt(
                KEY_ADAPTER_TIMEOUT_RECONNECT_THRESHOLD,
                ObdConnectionSettings.DEFAULT_ADAPTER_TIMEOUT_RECONNECT_THRESHOLD,
            ),
            engineOffDelayMs = prefs.getLong(
                KEY_ENGINE_OFF_DELAY_MS,
                ObdConnectionSettings.DEFAULT_ENGINE_OFF_DELAY_MS,
            ),
        ).normalized()
        set(value) {
            val settings = value.normalized()
            prefs.edit()
                .putLong(KEY_RPM_POLL_PERIOD_MS, settings.rpmPollPeriodMs)
                .putLong(KEY_COOLANT_POLL_PERIOD_MS, settings.coolantPollPeriodMs)
                .putInt(KEY_CONNECT_TIMEOUT_MS, settings.connectTimeoutMs)
                .putInt(KEY_READ_TIMEOUT_MS, settings.readTimeoutMs)
                .putLong(KEY_NETWORK_REQUEST_TIMEOUT_MS, settings.networkRequestTimeoutMs)
                .putInt(KEY_ADAPTER_TIMEOUT_RECONNECT_THRESHOLD, settings.adapterTimeoutReconnectThreshold)
                .putLong(KEY_ENGINE_OFF_DELAY_MS, settings.engineOffDelayMs)
                .apply()
        }

    fun resetConnectionSettings() {
        connectionSettings = ObdConnectionSettings.Default
    }

    private companion object {
        const val PREFS_NAME = "obd_monitor_preferences"
        const val KEY_FLOATING_WIDGET_ENABLED = "floating_widget_enabled"
        const val KEY_AUTO_START_ENABLED = "auto_start_enabled"
        const val KEY_FLOATING_WIDGET_SIZE = "floating_widget_size"
        const val KEY_RPM_POLL_PERIOD_MS = "rpm_poll_period_ms"
        const val KEY_COOLANT_POLL_PERIOD_MS = "coolant_poll_period_ms"
        const val KEY_CONNECT_TIMEOUT_MS = "connect_timeout_ms"
        const val KEY_READ_TIMEOUT_MS = "read_timeout_ms"
        const val KEY_NETWORK_REQUEST_TIMEOUT_MS = "network_request_timeout_ms"
        const val KEY_ADAPTER_TIMEOUT_RECONNECT_THRESHOLD = "adapter_timeout_reconnect_threshold"
        const val KEY_ENGINE_OFF_DELAY_MS = "engine_off_delay_ms"
    }
}
