package car.mazda.obd.android.feature.trip.route

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal class TripRouteDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_POINTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TRIP_STARTED_AT INTEGER NOT NULL,
                $COLUMN_RECORDED_AT INTEGER NOT NULL,
                $COLUMN_LATITUDE REAL NOT NULL,
                $COLUMN_LONGITUDE REAL NOT NULL,
                $COLUMN_ACCURACY REAL NOT NULL,
                $COLUMN_SPEED REAL,
                $COLUMN_RPM INTEGER NOT NULL,
                $COLUMN_COOLANT_TEMP INTEGER,
                $COLUMN_SEGMENT INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX route_trip_time ON $TABLE_POINTS ($COLUMN_TRIP_STARTED_AT, $COLUMN_RECORDED_AT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    companion object {
        private const val DATABASE_NAME = "trip_routes.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_POINTS = "route_points"
        const val COLUMN_ID = "id"
        const val COLUMN_TRIP_STARTED_AT = "trip_started_at_ms"
        const val COLUMN_RECORDED_AT = "recorded_at_ms"
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_ACCURACY = "accuracy_meters"
        const val COLUMN_SPEED = "speed_meters_per_second"
        const val COLUMN_RPM = "engine_rpm"
        const val COLUMN_COOLANT_TEMP = "coolant_temp_celsius"
        const val COLUMN_SEGMENT = "segment"
    }
}
