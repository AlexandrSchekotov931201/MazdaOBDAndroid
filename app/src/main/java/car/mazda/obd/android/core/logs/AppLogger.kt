package car.mazda.obd.android.core.logs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

object AppLogger {
    private const val MAX_LOG_ENTRIES = 20_000
    private const val LOG_FILE_NAME = "diagnostic_events.jsonl"

    enum class Level { Info, HandledError, Error }

    enum class Layer { App, Network, Elm, Obd, Parser }

    enum class Direction { None, Tx, Rx }

    data class Entry(
        val id: Long,
        val timestampMs: Long,
        val time: String,
        val level: Level,
        val layer: Layer,
        val direction: Direction,
        val operation: String,
        val message: String,
        val exchangeId: String? = null,
        val raw: String? = null,
        val errorType: String? = null,
        val errorMessage: String? = null,
        val stackTrace: String? = null,
        val sessionId: String = currentSessionId,
    ) {
        val line: String
            get() = buildString {
                append("[$time] ${level.label()} ${layer.name.uppercase(Locale.US)}")
                if (direction != Direction.None) append(" ${direction.name.uppercase(Locale.US)}")
                exchangeId?.let { append(" #$it") }
                if (operation.isNotBlank()) append(" [$operation]")
                append(" $message")
                raw?.let { append(" | raw=${it.visibleRaw()}") }
                errorType?.let { append(" | error=$it: ${errorMessage.orEmpty()}") }
            }
    }

    private val currentSessionId = UUID.randomUUID().toString().take(8)
    private val nextId = AtomicLong(System.currentTimeMillis())
    private val diskExecutor = Executors.newSingleThreadExecutor()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs
    @Volatile private var logFile: File? = null

    fun initialize(context: Context) {
        if (logFile != null) return
        synchronized(this) {
            if (logFile != null) return
            val file = File(context.applicationContext.filesDir, LOG_FILE_NAME)
            logFile = file
            val restored = runCatching {
                if (!file.exists()) emptyList<Entry>() else file.useLines { lines ->
                    lines.mapNotNull { line -> decode(line) }.toList().takeLast(MAX_LOG_ENTRIES)
                }
            }.getOrDefault(emptyList())
            _entries.value = restored
            _logs.value = restored.map(Entry::line)
        }
        event(layer = Layer.App, operation = "lifecycle", message = "Diagnostic session started")
    }

    fun newExchangeId(): String = nextId.incrementAndGet().toString(36).uppercase(Locale.US)

    fun log(msg: String) = event(message = msg)
    fun handledError(msg: String) = event(level = Level.HandledError, message = msg)
    fun error(msg: String) = event(level = Level.Error, message = msg)

    fun event(
        level: Level = Level.Info,
        layer: Layer = Layer.App,
        direction: Direction = Direction.None,
        operation: String = "",
        message: String,
        exchangeId: String? = null,
        raw: String? = null,
        throwable: Throwable? = null,
    ) {
        val timestampMs = System.currentTimeMillis()
        val entry = Entry(
            id = nextId.incrementAndGet(),
            timestampMs = timestampMs,
            time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestampMs)),
            level = level,
            layer = layer,
            direction = direction,
            operation = operation,
            message = message,
            exchangeId = exchangeId,
            raw = raw,
            errorType = throwable?.javaClass?.name,
            errorMessage = throwable?.message,
            stackTrace = throwable?.stackTraceToString(),
        )
        _entries.update { (it + entry).takeLast(MAX_LOG_ENTRIES) }
        _logs.update { (it + entry.line).takeLast(MAX_LOG_ENTRIES) }
        persist(entry)
    }

    fun clear() {
        _entries.value = emptyList()
        _logs.value = emptyList()
        diskExecutor.execute { runCatching { logFile?.writeText("") } }
    }

    fun buildReport(entries: List<Entry> = _entries.value, appInfo: String = ""): String = buildString {
        appendLine("Mazda OBD diagnostic report")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())}")
        appendLine("Session: $currentSessionId")
        if (appInfo.isNotBlank()) appendLine(appInfo)
        appendLine("Events: ${entries.size}")
        appendLine()
        entries.forEach { entry ->
            appendLine(entry.line)
            entry.stackTrace?.let { appendLine(it) }
        }
    }

    private fun persist(entry: Entry) {
        val file = logFile ?: return
        diskExecutor.execute {
            runCatching {
                file.appendText(encode(entry) + "\n")
                if (_entries.value.size == MAX_LOG_ENTRIES && file.length() > 8_000_000L) {
                    file.writeText(_entries.value.joinToString("\n", postfix = "\n", transform = ::encode))
                }
            }
        }
    }

    private fun encode(entry: Entry): String = JSONObject().apply {
        put("id", entry.id); put("timestampMs", entry.timestampMs); put("time", entry.time)
        put("level", entry.level.name); put("layer", entry.layer.name); put("direction", entry.direction.name)
        put("operation", entry.operation); put("message", entry.message); put("sessionId", entry.sessionId)
        entry.exchangeId?.let { put("exchangeId", it) }; entry.raw?.let { put("raw", it) }
        entry.errorType?.let { put("errorType", it) }; entry.errorMessage?.let { put("errorMessage", it) }
        entry.stackTrace?.let { put("stackTrace", it) }
    }.toString()

    private fun decode(line: String): Entry? = runCatching {
        val json = JSONObject(line)
        Entry(
            id = json.getLong("id"), timestampMs = json.getLong("timestampMs"), time = json.getString("time"),
            level = Level.valueOf(json.getString("level")), layer = Layer.valueOf(json.optString("layer", Layer.App.name)),
            direction = Direction.valueOf(json.optString("direction", Direction.None.name)),
            operation = json.optString("operation"), message = json.getString("message"),
            exchangeId = json.optString("exchangeId").takeIf(String::isNotBlank),
            raw = json.optString("raw").takeIf(String::isNotBlank), errorType = json.optString("errorType").takeIf(String::isNotBlank),
            errorMessage = json.optString("errorMessage").takeIf(String::isNotBlank),
            stackTrace = json.optString("stackTrace").takeIf(String::isNotBlank), sessionId = json.optString("sessionId", "legacy"),
        )
    }.getOrNull()

    private fun Level.label() = when (this) {
        Level.Info -> "INFO"; Level.HandledError -> "WARNING"; Level.Error -> "ERROR"
    }

    private fun String.visibleRaw(): String = replace("\r", "\\r").replace("\n", "\\n")
}
