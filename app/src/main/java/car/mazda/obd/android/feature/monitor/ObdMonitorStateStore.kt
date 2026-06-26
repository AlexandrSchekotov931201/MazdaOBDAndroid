package car.mazda.obd.android.feature.monitor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object ObdMonitorStateStore {
    private val _state = MutableStateFlow(ObdMonitorState())
    val state: StateFlow<ObdMonitorState> = _state

    fun update(transform: (ObdMonitorState) -> ObdMonitorState) {
        _state.update(transform)
    }

    fun stop() {
        _state.update { current ->
            ObdMonitorState(
                overlayEnabled = current.overlayEnabled,
                floatingWidgetEnabled = current.floatingWidgetEnabled,
                floatingWidgetSize = current.floatingWidgetSize,
                autoStartEnabled = current.autoStartEnabled,
                connectionSettings = current.connectionSettings,
                isAppForeground = current.isAppForeground,
                tripSummaryVersion = current.tripSummaryVersion,
            )
        }
    }
}
