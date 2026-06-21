package car.mazda.obd.android.ui.utils.sound

import android.content.Context
import android.media.MediaPlayer
import car.mazda.obd.android.R
import car.mazda.obd.android.logs.AppLogger

class SpeechPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    fun greetingSound() {
        if (player?.isPlaying == true) {
            AppLogger.log("Greeting sound already playing")
            return
        }

        stop()

        val mediaPlayer = MediaPlayer.create(context, R.raw.greeting_sound)
        if (mediaPlayer == null) {
            AppLogger.log("Greeting sound failed: MediaPlayer.create returned null")
            return
        }

        player = mediaPlayer.apply {
            setOnCompletionListener {
                it.release()
                if (player === it) player = null
            }
            setOnErrorListener { mp, what, extra ->
                AppLogger.log("Greeting sound error: what=$what, extra=$extra")
                mp.release()
                if (player === mp) player = null
                true
            }
            AppLogger.log("Greeting sound started")
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
