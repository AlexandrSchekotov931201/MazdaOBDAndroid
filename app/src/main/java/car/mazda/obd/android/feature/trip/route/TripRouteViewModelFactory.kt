package car.mazda.obd.android.feature.trip.route

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TripRouteViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TripRouteViewModel::class.java)) {
            return modelClass.cast(TripRouteViewModel(context.applicationContext))
                ?: error("Could not create TripRouteViewModel")
        }
        error("Unknown ViewModel class")
    }
}
