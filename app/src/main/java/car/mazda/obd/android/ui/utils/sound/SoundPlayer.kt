package car.mazda.obd.android.ui.utils.sound

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.delay

class SoundPlayer(
    streamType: Int = AudioManager.STREAM_MUSIC,
    volume: Int = 100,
    private val toneType: Int = ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
) {

    private val toneGenerator = ToneGenerator(streamType, volume)

    suspend fun playPattern(pattern: BeepPattern) {
        repeat(pattern.count) {
            toneGenerator.startTone(toneType, pattern.toneDurationMs)
            delay(pattern.pauseMs)
        }
    }
}