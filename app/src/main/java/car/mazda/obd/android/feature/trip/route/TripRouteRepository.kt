package car.mazda.obd.android.feature.trip.route

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TripRouteRepository(context: Context) {
    private val helper = TripRouteDatabaseHelper(context.applicationContext)

    suspend fun append(point: TripRoutePoint): Long = withContext(Dispatchers.IO) {
        helper.writableDatabase.insertOrThrow(
            TripRouteDatabaseHelper.TABLE_POINTS,
            null,
            ContentValues().apply {
                put(TripRouteDatabaseHelper.COLUMN_TRIP_STARTED_AT, point.tripStartedAtMs)
                put(TripRouteDatabaseHelper.COLUMN_RECORDED_AT, point.recordedAtMs)
                put(TripRouteDatabaseHelper.COLUMN_LATITUDE, point.latitude)
                put(TripRouteDatabaseHelper.COLUMN_LONGITUDE, point.longitude)
                put(TripRouteDatabaseHelper.COLUMN_ACCURACY, point.accuracyMeters)
                point.speedMetersPerSecond?.let { put(TripRouteDatabaseHelper.COLUMN_SPEED, it) }
                    ?: putNull(TripRouteDatabaseHelper.COLUMN_SPEED)
                put(TripRouteDatabaseHelper.COLUMN_RPM, point.engineRpm)
                point.coolantTempCelsius?.let { put(TripRouteDatabaseHelper.COLUMN_COOLANT_TEMP, it) }
                    ?: putNull(TripRouteDatabaseHelper.COLUMN_COOLANT_TEMP)
                put(TripRouteDatabaseHelper.COLUMN_SEGMENT, point.segment)
            }
        )
    }

    suspend fun pointsForTrip(startedAtMs: Long): List<TripRoutePoint> = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            TripRouteDatabaseHelper.TABLE_POINTS,
            null,
            "${TripRouteDatabaseHelper.COLUMN_TRIP_STARTED_AT} = ?",
            arrayOf(startedAtMs.toString()),
            null,
            null,
            TripRouteDatabaseHelper.COLUMN_RECORDED_AT,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toPoint()) } }
    }

    suspend fun nextSegment(startedAtMs: Long): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.rawQuery(
            "SELECT MAX(${TripRouteDatabaseHelper.COLUMN_SEGMENT}) FROM ${TripRouteDatabaseHelper.TABLE_POINTS} WHERE ${TripRouteDatabaseHelper.COLUMN_TRIP_STARTED_AT} = ?",
            arrayOf(startedAtMs.toString()),
        ).use { cursor -> if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getInt(0) + 1 else 0 }
    }

    suspend fun deleteTripRoute(startedAtMs: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(
            TripRouteDatabaseHelper.TABLE_POINTS,
            "${TripRouteDatabaseHelper.COLUMN_TRIP_STARTED_AT} = ?",
            arrayOf(startedAtMs.toString()),
        )
        Unit
    }

    private fun Cursor.toPoint() = TripRoutePoint(
        id = getLong(getColumnIndexOrThrow(TripRouteDatabaseHelper.COLUMN_ID)),
        tripStartedAtMs = getLong(getColumnIndexOrThrow(TripRouteDatabaseHelper.COLUMN_TRIP_STARTED_AT)),
        recordedAtMs = getLong(getColumnIndexOrThrow(TripRouteDatabaseHelper.COLUMN_RECORDED_AT)),
        latitude = getDouble(getColumnIndexOrThrow(TripRouteDatabaseHelper.COLUMN_LATITUDE)),
        longitude = getDouble(getColumnIndexOrThrow(TripRouteDatabaseHelper.COLUMN_LONGITUDE)),
        accuracyMeters = getFloat(getColumnIndexOrThrow(TripRouteDatabaseHelper.COLUMN_ACCURACY)),
        speedMetersPerSecond = nullableFloat(getColumnIndexOrThrow(TripRouteDatabaseHelper.COLUMN_SPEED)),
        engineRpm = getInt(getColumnIndexOrThrow(TripRouteDatabaseHelper.COLUMN_RPM)),
        coolantTempCelsius = nullableInt(getColumnIndexOrThrow(TripRouteDatabaseHelper.COLUMN_COOLANT_TEMP)),
        segment = getInt(getColumnIndexOrThrow(TripRouteDatabaseHelper.COLUMN_SEGMENT)),
    )

    private fun Cursor.nullableFloat(index: Int) = if (isNull(index)) null else getFloat(index)
    private fun Cursor.nullableInt(index: Int) = if (isNull(index)) null else getInt(index)
}
