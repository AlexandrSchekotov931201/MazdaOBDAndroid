package car.mazda.obd.android.feature.trip.route

import android.content.Context
import androidx.lifecycle.ViewModel
import car.mazda.obd.android.feature.monitor.ObdMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TripRouteSettingsViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val preferences = TripRoutePreferences(appContext)
    private val _recordingEnabled = MutableStateFlow(preferences.recordingEnabled)
    val recordingEnabled: StateFlow<Boolean> = _recordingEnabled

    fun setRecordingEnabled(enabled: Boolean) {
        preferences.recordingEnabled = enabled
        _recordingEnabled.value = enabled
        ObdMonitorService.refreshRouteRecording(appContext)
    }
}
