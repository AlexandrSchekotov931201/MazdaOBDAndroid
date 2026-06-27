package car.mazda.obd.android.feature.logs

import car.mazda.obd.android.core.logs.AppLogger

internal fun AppLogger.Entry.detailedText(): String = buildString {
    appendLine(line)
    appendLine("Timestamp: $timestampMs")
    appendLine("Session: $sessionId")
    exchangeId?.let { appendLine("Exchange: $it") }
    raw?.let { appendLine("Raw:\n${it.visibleRaw()}") }
    errorType?.let { appendLine("Error: $it: ${errorMessage.orEmpty()}") }
    stackTrace?.let { appendLine("Stack trace:\n$it") }
}

internal fun String.visibleRaw(): String = replace("\r", "\\r").replace("\n", "\\n\n")
