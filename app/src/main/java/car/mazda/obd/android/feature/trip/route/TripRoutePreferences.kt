package car.mazda.obd.android.feature.trip.route

import android.content.Context

class TripRoutePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var recordingEnabled: Boolean
        get() = preferences.getBoolean(KEY_RECORDING_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_RECORDING_ENABLED, value).apply()

    private companion object {
        const val FILE_NAME = "trip_route_preferences"
        const val KEY_RECORDING_ENABLED = "recording_enabled"
    }
}
