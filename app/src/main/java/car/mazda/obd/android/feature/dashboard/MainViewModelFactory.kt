package car.mazda.obd.android.feature.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import car.mazda.obd.android.feature.trip.summary.TripSummaryRepository

class MainViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return modelClass.cast(
                MainViewModel(
                    tripSummaryRepository = TripSummaryRepository(context),
                )
            )
                ?: throw IllegalArgumentException("Could not create MainViewModel")
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
