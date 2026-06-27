package car.mazda.obd.android

import android.app.Application
import car.mazda.obd.android.core.logs.AppLogger

class MazdaObdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.initialize(this)
    }
}
