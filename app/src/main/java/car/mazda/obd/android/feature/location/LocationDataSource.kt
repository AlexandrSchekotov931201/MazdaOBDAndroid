package car.mazda.obd.android.feature.location

import kotlinx.coroutines.flow.Flow

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float?,
    val recordedAtMs: Long,
)

interface LocationDataSource {
    fun locations(): Flow<DeviceLocation>
}
