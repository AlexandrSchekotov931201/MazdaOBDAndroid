package car.mazda.obd.android.feature.monitor

import car.mazda.obd.android.feature.trip.summary.ActiveTripSummary

data class ObdMonitorState(
    val isRunning: Boolean = false,
    val connectionText: String = "Monitoring stopped",
    val rpm: Int = 0,
    val coolantTemp: Int? = null,
    val warmupText: String = "Coolant temp: --",
    val overlayEnabled: Boolean = false,
    val floatingWidgetEnabled: Boolean = true,
    val floatingWidgetSize: FloatingWidgetSize = FloatingWidgetSize.Small,
    val autoStartEnabled: Boolean = true,
    val continueAfterAppClosed: Boolean = false,
    val isAppForeground: Boolean = false,
    val activeTrip: ActiveTripSummary? = null,
    val tripSummaryVersion: Long = 0L,
    val tripRouteVersion: Long = 0L,
)
