package car.mazda.obd.android.feature.logs

import car.mazda.obd.android.core.logs.AppLogger

internal data class DiagnosticViewSettings(
    val query: String = "",
    val selectedLayers: Set<AppLogger.Layer> = emptySet(),
    val problemsOnly: Boolean = false,
    val grouped: Boolean = true,
    val newestFirst: Boolean = true,
    val followNewest: Boolean = true,
)

internal fun List<AppLogger.Entry>.filterFor(settings: DiagnosticViewSettings): List<AppLogger.Entry> =
    filter { entry ->
        (!settings.problemsOnly || entry.level != AppLogger.Level.Info) &&
            (settings.selectedLayers.isEmpty() || entry.layer in settings.selectedLayers) &&
            (settings.query.isBlank() || entry.searchableText().contains(settings.query, ignoreCase = true))
    }

internal fun List<AppLogger.Entry>.groupFor(settings: DiagnosticViewSettings): List<List<AppLogger.Entry>> {
    val chronological = if (settings.grouped) {
        groupBy { it.exchangeId ?: "event-${it.id}" }.values.toList()
    } else {
        map(::listOf)
    }
    return if (settings.newestFirst) chronological.asReversed() else chronological
}

private fun AppLogger.Entry.searchableText(): String = listOf(
    time,
    level.name,
    layer.name,
    direction.name,
    operation,
    message,
    exchangeId,
    raw,
    errorType,
    errorMessage,
    stackTrace,
).joinToString(" ") { it.orEmpty() }
