package car.mazda.obd.android.feature.trip.summary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TripSummaryDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onCreate(db: SQLiteDatabase) {
        createTripsTable(db)
        createTripDebugEventsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            createTripDebugEventsTable(db)
        }
    }

    private fun createTripsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TRIPS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_STARTED_AT_MS INTEGER NOT NULL,
                $COLUMN_FINISHED_AT_MS INTEGER NOT NULL,
                $COLUMN_MAX_RPM INTEGER NOT NULL,
                $COLUMN_MAX_COOLANT_TEMP_CELSIUS INTEGER
            )
            """.trimIndent()
        )
    }

    private fun createTripDebugEventsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_TRIP_DEBUG_EVENTS (
                $COLUMN_DEBUG_EVENT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_DEBUG_EVENT_TRIP_ID INTEGER NOT NULL,
                $COLUMN_DEBUG_EVENT_OCCURRED_AT_MS INTEGER NOT NULL,
                $COLUMN_DEBUG_EVENT_LEVEL TEXT NOT NULL,
                $COLUMN_DEBUG_EVENT_MESSAGE TEXT NOT NULL,
                FOREIGN KEY($COLUMN_DEBUG_EVENT_TRIP_ID)
                    REFERENCES $TABLE_TRIPS($COLUMN_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    companion object {
        private const val DATABASE_NAME = "trip_summary.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_TRIPS = "trip_summaries"
        const val TABLE_TRIP_DEBUG_EVENTS = "trip_debug_events"
        const val COLUMN_ID = "id"
        const val COLUMN_STARTED_AT_MS = "started_at_ms"
        const val COLUMN_FINISHED_AT_MS = "finished_at_ms"
        const val COLUMN_MAX_RPM = "max_rpm"
        const val COLUMN_MAX_COOLANT_TEMP_CELSIUS = "max_coolant_temp_celsius"
        const val COLUMN_DEBUG_EVENT_ID = "id"
        const val COLUMN_DEBUG_EVENT_TRIP_ID = "trip_id"
        const val COLUMN_DEBUG_EVENT_OCCURRED_AT_MS = "occurred_at_ms"
        const val COLUMN_DEBUG_EVENT_LEVEL = "level"
        const val COLUMN_DEBUG_EVENT_MESSAGE = "message"
    }
}
