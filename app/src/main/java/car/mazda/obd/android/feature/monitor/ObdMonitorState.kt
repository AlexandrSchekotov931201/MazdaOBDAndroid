package car.mazda.obd.android.feature.monitor

import car.mazda.obd.android.feature.trip.summary.ActiveTripSummary

data class ObdMonitorState(
    val isRunning: Boolean = false,
    val connectionStatus: MonitorConnectionStatus = MonitorConnectionStatus.Offline,
    val connectionError: String? = null,
    val rpm: Int = 0,
    val coolantTemp: Int? = null,
    val warmupText: String = "Coolant temp: --",
    val overlayEnabled: Boolean = false,
    val floatingWidgetEnabled: Boolean = true,
    val floatingWidgetSize: FloatingWidgetSize = FloatingWidgetSize.Small,
    val continueAfterAppClosed: Boolean = false,
    val isAppForeground: Boolean = false,
    val activeTrip: ActiveTripSummary? = null,
    val tripSummaryVersion: Long = 0L,
    val tripRouteVersion: Long = 0L,
)

enum class MonitorConnectionStatus(val label: String) {
    Offline("Offline"),
    Connecting("Connecting"),
    Reconnecting("Reconnecting"),
    Ready("Ready"),
}

val ObdMonitorState.connectionText: String
    get() = connectionStatus.label
