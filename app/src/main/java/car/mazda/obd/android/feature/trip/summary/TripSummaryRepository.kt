package car.mazda.obd.android.feature.trip.summary

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import car.mazda.obd.android.BuildConfig
import car.mazda.obd.android.core.logs.AppLogger
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
            val tripId = dbHelper.writableDatabase.insert(
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
            if (BuildConfig.DEBUG && tripId > 0L) {
                saveDebugEventsForTrip(tripId = tripId, summary = summary)
            }
        }
        refreshRecentTrips()
    }

    private fun saveDebugEventsForTrip(tripId: Long, summary: TripSummary) {
        val debugEntries = AppLogger.entries.value
            .asSequence()
            .filter { entry -> entry.level != AppLogger.Level.Info }
            .filter { entry -> entry.timestampMs in summary.startedAtMs..summary.finishedAtMs }
            .toList()

        if (debugEntries.isEmpty()) return

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            debugEntries.forEach { entry ->
                db.insert(
                    TripSummaryDatabaseHelper.TABLE_TRIP_DEBUG_EVENTS,
                    null,
                    ContentValues().apply {
                        put(TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_TRIP_ID, tripId)
                        put(TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_OCCURRED_AT_MS, entry.timestampMs)
                        put(TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_LEVEL, entry.level.name)
                        put(TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_MESSAGE, entry.message)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
                        add(cursor.toTripSummary().withDebugEvents())
                    }
                }
            }
        }

    private fun TripSummary.withDebugEvents(): TripSummary {
        if (!BuildConfig.DEBUG || id <= 0L) return this
        return copy(debugEvents = loadDebugEvents(id))
    }

    private fun loadDebugEvents(tripId: Long): List<TripDebugEvent> {
        return dbHelper.readableDatabase.query(
            TripSummaryDatabaseHelper.TABLE_TRIP_DEBUG_EVENTS,
            null,
            "${TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_TRIP_ID} = ?",
            arrayOf(tripId.toString()),
            null,
            null,
            "${TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_OCCURRED_AT_MS} ASC",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toTripDebugEvent())
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

    private fun Cursor.toTripDebugEvent(): TripDebugEvent =
        TripDebugEvent(
            id = getLong(columnIndex(TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_ID)),
            tripId = getLong(columnIndex(TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_TRIP_ID)),
            occurredAtMs = getLong(columnIndex(TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_OCCURRED_AT_MS)),
            level = getString(columnIndex(TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_LEVEL)),
            message = getString(columnIndex(TripSummaryDatabaseHelper.COLUMN_DEBUG_EVENT_MESSAGE)),
        )

    private fun Cursor.columnIndex(columnName: String): Int =
        getColumnIndexOrThrow(columnName)

    private fun Cursor.nullableInt(index: Int): Int? =
        if (isNull(index)) null else getInt(index)

    private companion object {
        const val RECENT_TRIPS_LIMIT = 50
    }
}
