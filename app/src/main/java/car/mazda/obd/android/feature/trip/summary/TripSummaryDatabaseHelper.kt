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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TRIPS")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "trip_summary.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_TRIPS = "trip_summaries"
        const val COLUMN_ID = "id"
        const val COLUMN_STARTED_AT_MS = "started_at_ms"
        const val COLUMN_FINISHED_AT_MS = "finished_at_ms"
        const val COLUMN_MAX_RPM = "max_rpm"
        const val COLUMN_MAX_COOLANT_TEMP_CELSIUS = "max_coolant_temp_celsius"
    }
}
