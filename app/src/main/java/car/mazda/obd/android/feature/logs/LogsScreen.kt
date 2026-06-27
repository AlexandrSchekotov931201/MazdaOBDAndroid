package car.mazda.obd.android.feature.logs

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import car.mazda.obd.android.BuildConfig
import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.core.logs.DiagnosticReportExporter
import car.mazda.obd.android.ui.AppToolbar

@Composable
fun LogsScreen(onOpenMenu: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries by AppLogger.entries.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedLayer by remember { mutableStateOf<AppLogger.Layer?>(null) }
    var problemsOnly by remember { mutableStateOf(false) }
    var grouped by remember { mutableStateOf(true) }
    var selectedEntry by remember { mutableStateOf<AppLogger.Entry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showShareOptions by remember { mutableStateOf(false) }

    val filtered = remember(entries, query, selectedLayer, problemsOnly) {
        entries.filter { entry ->
            (!problemsOnly || entry.level != AppLogger.Level.Info) &&
                (selectedLayer == null || entry.layer == selectedLayer) &&
                (query.isBlank() || entry.searchText().contains(query, ignoreCase = true))
        }
    }
    val groups = remember(filtered, grouped) {
        if (grouped) filtered.groupBy { it.exchangeId ?: "event-${it.id}" }.values.toList().asReversed()
        else filtered.asReversed().map(::listOf)
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppToolbar(onOpenMenu = onOpenMenu, title = "Diagnostics")

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search command, error, raw data…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = selectedLayer == null, onClick = { selectedLayer = null }, label = { Text("All layers") })
            AppLogger.Layer.entries.forEach { layer ->
                FilterChip(selected = selectedLayer == layer, onClick = { selectedLayer = layer }, label = { Text(layer.name) })
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = problemsOnly, onClick = { problemsOnly = !problemsOnly }, label = { Text("Problems only") })
            FilterChip(selected = grouped, onClick = { grouped = !grouped }, label = { Text("Group TX / RX / parser") })
            AssistChip(onClick = { showShareOptions = true }, label = { Text("Share logs") }, enabled = entries.isNotEmpty())
            AssistChip(onClick = { confirmClear = true }, label = { Text("Clear") }, enabled = entries.isNotEmpty())
        }

        Text(
            text = "${filtered.size} of ${entries.size} events • newest first",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (groups.isEmpty()) {
            Text("No matching diagnostic events", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groups, key = { group -> group.first().exchangeId ?: group.first().id }) { group ->
                    DiagnosticGroup(group = group, onOpen = { selectedEntry = it })
                }
            }
        }
    }

    selectedEntry?.let { entry -> EntryDetails(entry = entry, onDismiss = { selectedEntry = null }) }
    if (showShareOptions) {
        AlertDialog(
            onDismissRequest = { showShareOptions = false },
            title = { Text("Share diagnostic logs") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showShareOptions = false
                            shareReport(context, entries)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Share entire journal")
                    }
                    TextButton(
                        onClick = {
                            showShareOptions = false
                            shareReport(context, filtered)
                        },
                        enabled = filtered.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Share current filtered view")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showShareOptions = false }) { Text("Cancel") }
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear diagnostic history?") },
            text = { Text("Stored events will be removed from this device. Export them first if they may be useful.") },
            confirmButton = { TextButton(onClick = { AppLogger.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DiagnosticGroup(group: List<AppLogger.Entry>, onOpen: (AppLogger.Entry) -> Unit) {
    val worstLevel = group.maxBy { it.level.ordinal }.level
    val color = when (worstLevel) {
        AppLogger.Level.Info -> MaterialTheme.colorScheme.surfaceVariant
        AppLogger.Level.HandledError -> Color(0xFFFFF3CD)
        AppLogger.Level.Error -> Color(0xFFFFE2E2)
    }
    Surface(modifier = Modifier.fillMaxWidth(), color = color, shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            val first = group.first()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(first.time, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(first.layer.name.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                first.exchangeId?.let { Text("#$it", style = MaterialTheme.typography.labelMedium) }
                Text(first.operation, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            group.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) }.padding(vertical = 3.dp)) {
                    Text(
                        text = buildString {
                            if (entry.direction != AppLogger.Direction.None) append("${entry.direction.name.uppercase()} • ")
                            append(entry.message)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (entry.level == AppLogger.Level.Info) FontWeight.Normal else FontWeight.SemiBold,
                    )
                    entry.raw?.let {
                        Text(it.visibleRaw(), maxLines = 3, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    entry.errorType?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun EntryDetails(entry: AppLogger.Entry, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val details = entry.detailedText()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.layer.name} • ${entry.operation}") },
        text = {
            LazyColumn {
                item { Text(details, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(details)) }) { Text("Copy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun AppLogger.Entry.searchText() = listOf(
    time, level.name, layer.name, direction.name, operation, message, exchangeId, raw, errorType, errorMessage, stackTrace
).joinToString(" ") { it.orEmpty() }

private fun AppLogger.Entry.detailedText() = buildString {
    appendLine(line)
    appendLine("Timestamp: $timestampMs")
    appendLine("Session: $sessionId")
    exchangeId?.let { appendLine("Exchange: $it") }
    raw?.let { appendLine("Raw:\n${it.visibleRaw()}") }
    errorType?.let { appendLine("Error: $it: ${errorMessage.orEmpty()}") }
    stackTrace?.let { appendLine("Stack trace:\n$it") }
}

private fun String.visibleRaw() = replace("\r", "\\r").replace("\n", "\\n\n")

private fun shareReport(context: android.content.Context, entries: List<AppLogger.Entry>) {
    val appInfo = "App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.FLAVOR}\n" +
        "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    DiagnosticReportExporter.share(context, entries, appInfo)
}
