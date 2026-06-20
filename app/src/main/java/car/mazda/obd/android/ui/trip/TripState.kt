package car.mazda.obd.android.ui.trip

sealed class TripState {
    data object Idle : TripState()
    data object Active : TripState()
    data object Finishing : TripState()
}
