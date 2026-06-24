package car.mazda.obd.android.core.logs

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Locale

object AppLogger {
    private const val MAX_LOG_LINES = 500

    enum class Level {
        Info,
        HandledError,
        Error
    }

    data class Entry(
        val timestampMs: Long,
        val time: String,
        val level: Level,
        val message: String
    ) {
        val line: String = "[$time] ${level.label()} $message"
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    fun log(msg: String) {
        log(Level.Info, msg)
    }

    fun handledError(msg: String) {
        log(Level.HandledError, msg)
    }

    fun error(msg: String) {
        log(Level.Error, msg)
    }

    private fun log(level: Level, msg: String) {
        val timestampMs = System.currentTimeMillis()
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(timestampMs)
        val entry = Entry(
            timestampMs = timestampMs,
            time = time,
            level = level,
            message = msg,
        )

        _entries.update { current ->
            (current + entry).takeLast(MAX_LOG_LINES)
        }
        _logs.update { current ->
            (current + entry.line).takeLast(MAX_LOG_LINES)
        }
    }

    private fun Level.label(): String =
        when (this) {
            Level.Info -> "INFO"
            Level.HandledError -> "HANDLED"
            Level.Error -> "ERROR"
        }
}
