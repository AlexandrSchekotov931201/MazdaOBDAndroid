package car.mazda.obd.android.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return modelClass.cast(MainViewModel())
                ?: throw IllegalArgumentException("Could not create MainViewModel")
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
