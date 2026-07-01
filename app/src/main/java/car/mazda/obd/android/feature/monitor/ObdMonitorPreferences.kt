package car.mazda.obd.android.feature.monitor

import android.content.Context

class ObdMonitorPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var floatingWidgetEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOATING_WIDGET_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_FLOATING_WIDGET_ENABLED, value).apply()
        }

    var continueAfterAppClosed: Boolean
        get() = prefs.getBoolean(KEY_CONTINUE_AFTER_APP_CLOSED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CONTINUE_AFTER_APP_CLOSED, value).apply()
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
        const val KEY_CONTINUE_AFTER_APP_CLOSED = "continue_after_app_closed"
        const val KEY_FLOATING_WIDGET_SIZE = "floating_widget_size"
    }
}
