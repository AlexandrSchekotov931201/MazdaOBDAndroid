package car.mazda.obd.android.ui.utils.sound

import android.content.Context
import android.media.MediaPlayer
import car.mazda.obd.android.R

class SpeechPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    fun greetingSound() {
        stop() // гарантируем один звук за раз

        player = MediaPlayer.create(context, R.raw.greeting_sound).apply {
            setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            start()
        }
    }

    fun stop() {
        player?.run {
            try {
                stop()
            } catch (_: IllegalStateException) {
            }
            release()
        }
        player = null
    }

}
