package car.mazda.obd.android.feature.monitor

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import car.mazda.obd.android.R
import car.mazda.obd.android.ui.MainActivity

class ObdStatusWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = ObdMonitorStateStore.state.value
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context, state))
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ObdStatusWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val views = buildViews(context, ObdMonitorStateStore.state.value)
            ids.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
        }

        private fun buildViews(context: Context, state: ObdMonitorState): RemoteViews {
            val openIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val coolant = state.coolantTemp?.let { "${it}C" } ?: "--"
            return RemoteViews(context.packageName, R.layout.obd_status_widget).apply {
                setOnClickPendingIntent(R.id.obd_widget_root, openIntent)
                setTextViewText(R.id.obd_widget_connection, state.connectionText)
                setTextViewText(R.id.obd_widget_rpm, state.rpm.toString())
                setTextViewText(R.id.obd_widget_coolant, "Coolant $coolant")
                setTextViewText(R.id.obd_widget_warmup, state.warmupText)
                setProgressBar(R.id.obd_widget_rpm_progress, 8000, state.rpm.coerceIn(0, 8000), false)
            }
        }
    }
}
