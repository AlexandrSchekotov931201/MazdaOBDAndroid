package car.mazda.obd.android.feature.trip

sealed class TripState {
    data object Idle : TripState()
    data object Active : TripState()
    data object Finishing : TripState()
}
