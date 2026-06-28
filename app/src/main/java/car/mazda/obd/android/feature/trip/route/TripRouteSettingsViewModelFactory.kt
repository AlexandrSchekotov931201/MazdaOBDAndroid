package car.mazda.obd.android.feature.trip.route

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TripRouteSettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TripRouteSettingsViewModel::class.java)) {
            return modelClass.cast(TripRouteSettingsViewModel(context.applicationContext))
                ?: error("Could not create TripRouteSettingsViewModel")
        }
        error("Unknown ViewModel class")
    }
}
