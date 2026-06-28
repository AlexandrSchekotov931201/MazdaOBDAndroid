package car.mazda.obd.android.feature.trip.route

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import car.mazda.obd.android.feature.monitor.ObdMonitorStateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class TripRouteUiState(
    val tripStartedAtMs: Long? = null,
    val points: List<TripRoutePoint> = emptyList(),
    val statistics: RouteStatistics = RouteStatistics(0.0, null, 0),
    val loading: Boolean = false,
)

class TripRouteViewModel(context: Context) : ViewModel() {
    private val repository = TripRouteRepository(context.applicationContext)
    private val _state = MutableStateFlow(TripRouteUiState())
    val state: StateFlow<TripRouteUiState> = _state

    init {
        viewModelScope.launch {
            ObdMonitorStateStore.state
                .map { it.tripRouteVersion }
                .distinctUntilChanged()
                .collect { refresh() }
        }
    }

    fun showTrip(startedAtMs: Long) {
        _state.value = TripRouteUiState(tripStartedAtMs = startedAtMs, loading = true)
        viewModelScope.launch { refresh() }
    }

    fun clearSelection() {
        _state.value = TripRouteUiState()
    }

    fun deleteSelectedRoute() {
        val startedAtMs = _state.value.tripStartedAtMs ?: return
        viewModelScope.launch {
            repository.deleteTripRoute(startedAtMs)
            _state.value = TripRouteUiState(tripStartedAtMs = startedAtMs)
        }
    }

    private suspend fun refresh() {
        val startedAtMs = _state.value.tripStartedAtMs ?: return
        val points = repository.pointsForTrip(startedAtMs)
        if (_state.value.tripStartedAtMs != startedAtMs) return
        _state.value = TripRouteUiState(
            tripStartedAtMs = startedAtMs,
            points = points,
            statistics = RouteStatisticsCalculator.calculate(points),
        )
    }
}
