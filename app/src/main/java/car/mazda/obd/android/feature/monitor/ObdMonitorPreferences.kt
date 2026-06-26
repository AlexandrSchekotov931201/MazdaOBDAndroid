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

    private companion object {
        const val PREFS_NAME = "obd_monitor_preferences"
        const val KEY_FLOATING_WIDGET_ENABLED = "floating_widget_enabled"
        const val KEY_AUTO_START_ENABLED = "auto_start_enabled"
        const val KEY_FLOATING_WIDGET_SIZE = "floating_widget_size"
    }
}
