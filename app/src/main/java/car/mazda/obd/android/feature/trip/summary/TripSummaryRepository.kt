package car.mazda.obd.android.feature.trip.summary

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class TripSummaryRepository(context: Context) {
    private val dbHelper = TripSummaryDatabaseHelper(context.applicationContext)

    private val _recentTrips = MutableStateFlow<List<TripSummary>>(emptyList())
    val recentTrips: StateFlow<List<TripSummary>> = _recentTrips

    suspend fun refreshRecentTrips(limit: Int = RECENT_TRIPS_LIMIT) {
        _recentTrips.value = loadRecentTrips(limit)
    }

    suspend fun saveTrip(summary: TripSummary) {
        withContext(Dispatchers.IO) {
            dbHelper.writableDatabase.insert(
                TripSummaryDatabaseHelper.TABLE_TRIPS,
                null,
                ContentValues().apply {
                    put(TripSummaryDatabaseHelper.COLUMN_STARTED_AT_MS, summary.startedAtMs)
                    put(TripSummaryDatabaseHelper.COLUMN_FINISHED_AT_MS, summary.finishedAtMs)
                    put(TripSummaryDatabaseHelper.COLUMN_MAX_RPM, summary.maxRpm)
                    if (summary.maxEngineTempCelsius == null) {
                        putNull(TripSummaryDatabaseHelper.COLUMN_MAX_COOLANT_TEMP_CELSIUS)
                    } else {
                        put(
                            TripSummaryDatabaseHelper.COLUMN_MAX_COOLANT_TEMP_CELSIUS,
                            summary.maxEngineTempCelsius,
                        )
                    }
                }
            )
        }
        refreshRecentTrips()
    }

    private suspend fun loadRecentTrips(limit: Int): List<TripSummary> =
        withContext(Dispatchers.IO) {
            dbHelper.readableDatabase.query(
                TripSummaryDatabaseHelper.TABLE_TRIPS,
                null,
                null,
                null,
                null,
                null,
                "${TripSummaryDatabaseHelper.COLUMN_FINISHED_AT_MS} DESC",
                limit.toString(),
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toTripSummary())
                    }
                }
            }
        }

    private fun Cursor.toTripSummary(): TripSummary =
        TripSummary(
            id = getLong(columnIndex(TripSummaryDatabaseHelper.COLUMN_ID)),
            startedAtMs = getLong(columnIndex(TripSummaryDatabaseHelper.COLUMN_STARTED_AT_MS)),
            finishedAtMs = getLong(columnIndex(TripSummaryDatabaseHelper.COLUMN_FINISHED_AT_MS)),
            maxRpm = getInt(columnIndex(TripSummaryDatabaseHelper.COLUMN_MAX_RPM)),
            maxEngineTempCelsius = nullableInt(
                columnIndex(TripSummaryDatabaseHelper.COLUMN_MAX_COOLANT_TEMP_CELSIUS),
            ),
        )

    private fun Cursor.columnIndex(columnName: String): Int =
        getColumnIndexOrThrow(columnName)

    private fun Cursor.nullableInt(index: Int): Int? =
        if (isNull(index)) null else getInt(index)

    private companion object {
        const val RECENT_TRIPS_LIMIT = 50
    }
}
