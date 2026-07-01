package car.mazda.obd.android.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import car.mazda.obd.android.feature.monitor.FloatingWidgetSize
import car.mazda.obd.android.feature.monitor.ObdMonitorPreferences
import car.mazda.obd.android.feature.monitor.ObdMonitorService
import car.mazda.obd.android.feature.monitor.ObdMonitorStateStore
import car.mazda.obd.android.feature.monitor.MonitorConnectionStatus
import car.mazda.obd.android.feature.trip.summary.ActiveTripSummary
import car.mazda.obd.android.feature.trip.summary.TripSummary
import car.mazda.obd.android.feature.trip.summary.TripSummaryRepository
import car.mazda.obd.android.feature.settings.AdapterConnectionPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val context: Context,
    private val tripSummaryRepository: TripSummaryRepository,
) : ViewModel() {
    private val preferences = ObdMonitorPreferences(context)

    val connectionStatusState: StateFlow<MonitorConnectionStatus> = ObdMonitorStateStore.state
        .map { it.connectionStatus }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ObdMonitorStateStore.state.value.connectionStatus)

    val rpmState: StateFlow<Int> = ObdMonitorStateStore.state
        .map { it.rpm }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ObdMonitorStateStore.state.value.rpm)

    val coolantTempState: StateFlow<Int?> = ObdMonitorStateStore.state
        .map { it.coolantTemp }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ObdMonitorStateStore.state.value.coolantTemp)

    val warmupTextState: StateFlow<String> = ObdMonitorStateStore.state
        .map { it.warmupText }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ObdMonitorStateStore.state.value.warmupText)

    val overlayEnabledState: StateFlow<Boolean> = ObdMonitorStateStore.state
        .map { it.overlayEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ObdMonitorStateStore.state.value.overlayEnabled)

    val floatingWidgetEnabledState: StateFlow<Boolean> = ObdMonitorStateStore.state
        .map { it.floatingWidgetEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferences.floatingWidgetEnabled)

    val floatingWidgetSizeState: StateFlow<FloatingWidgetSize> = ObdMonitorStateStore.state
        .map { it.floatingWidgetSize }
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferences.floatingWidgetSize)

    val autoStartEnabledState: StateFlow<Boolean> = ObdMonitorStateStore.state
        .map { it.autoStartEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferences.autoStartEnabled)

    val continueAfterAppClosedState: StateFlow<Boolean> = ObdMonitorStateStore.state
        .map { it.continueAfterAppClosed }
        .stateIn(viewModelScope, SharingStarted.Eagerly, preferences.continueAfterAppClosed)

    val activeTripSummaryState: StateFlow<ActiveTripSummary?> = ObdMonitorStateStore.state
        .map { it.activeTrip }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ObdMonitorStateStore.state.value.activeTrip)

    val recentTripSummariesState: StateFlow<List<TripSummary>> = tripSummaryRepository.recentTrips

    init {
        ObdMonitorStateStore.update {
            it.copy(
                floatingWidgetEnabled = preferences.floatingWidgetEnabled,
                floatingWidgetSize = preferences.floatingWidgetSize,
                autoStartEnabled = preferences.autoStartEnabled,
                continueAfterAppClosed = preferences.continueAfterAppClosed,
            )
        }
        if (AdapterConnectionPreferences(context).loadVerified() != null) startMonitoring()
        observeTripSummaryRefreshes()
        viewModelScope.launch {
            tripSummaryRepository.refreshRecentTrips()
        }
    }

    private fun startMonitoring() {
        ObdMonitorService.start(context)
    }

    fun setFloatingWidgetEnabled(enabled: Boolean) {
        preferences.floatingWidgetEnabled = enabled
        ObdMonitorStateStore.update { it.copy(floatingWidgetEnabled = enabled) }
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        preferences.autoStartEnabled = enabled
        ObdMonitorStateStore.update { it.copy(autoStartEnabled = enabled) }
    }

    fun setContinueAfterAppClosed(enabled: Boolean) {
        preferences.continueAfterAppClosed = enabled
        ObdMonitorStateStore.update { it.copy(continueAfterAppClosed = enabled) }
        // Re-deliver the start command so Android records the updated
        // START_STICKY/START_NOT_STICKY policy. The running monitor is not restarted.
        ObdMonitorService.start(context)
    }

    fun setFloatingWidgetSize(size: FloatingWidgetSize) {
        preferences.floatingWidgetSize = size
        ObdMonitorStateStore.update { it.copy(floatingWidgetSize = size) }
    }

    fun startTrip() = ObdMonitorService.startTrip(context)

    fun stopTrip() = ObdMonitorService.stopTrip(context)

    private fun observeTripSummaryRefreshes() {
        viewModelScope.launch {
            ObdMonitorStateStore.state
                .map { it.tripSummaryVersion }
                .distinctUntilChanged()
                .collect {
                    tripSummaryRepository.refreshRecentTrips()
                }
        }
    }
}
