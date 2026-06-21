package car.mazda.obd.android.ui.utils.sound

import android.media.AudioManager
import android.media.ToneGenerator
import car.mazda.obd.android.logs.AppLogger
import kotlinx.coroutines.delay

class SoundPlayer(
    streamType: Int = AudioManager.STREAM_MUSIC,
    volume: Int = 100,
    private val toneType: Int = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
) {

    private val toneGenerator = ToneGenerator(streamType, volume)

    suspend fun playPattern(pattern: BeepPattern) {
        AppLogger.log("Beep pattern started")
        repeat(pattern.count) {
            val started = toneGenerator.startTone(toneType, pattern.toneDurationMs)
            if (!started) {
                AppLogger.log("Beep tone failed to start")
            }
            delay(pattern.toneDurationMs.toLong())
            toneGenerator.stopTone()
            delay(pattern.pauseMs)
        }
        AppLogger.log("Beep pattern finished")
    }

    fun release() {
        toneGenerator.release()
    }
}
