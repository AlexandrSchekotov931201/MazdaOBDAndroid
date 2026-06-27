package car.mazda.obd.android.feature.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preferences = ObdMonitorPreferences(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            preferences.autoStartEnabled &&
            preferences.continueAfterAppClosed
        ) {
            ObdMonitorService.start(context)
        }
    }
}
