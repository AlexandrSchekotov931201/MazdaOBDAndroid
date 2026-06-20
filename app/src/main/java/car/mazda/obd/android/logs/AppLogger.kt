package car.mazda.obd.android.logs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Locale

object AppLogger {
    private const val MAX_LOG_LINES = 500

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs

    fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(System.currentTimeMillis())
        val line = "[$time] $msg"

        _logs.update { current ->
            (current + line).takeLast(MAX_LOG_LINES)
        }
    }
}
