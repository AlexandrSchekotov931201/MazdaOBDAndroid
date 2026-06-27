package car.mazda.obd.android.feature.logs

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.distinctUntilChanged

private data class DiagnosticViewSettings(
    val query: String = "",
    val selectedLayers: Set<AppLogger.Layer> = emptySet(),
    val problemsOnly: Boolean = false,
    val grouped: Boolean = true,
    val newestFirst: Boolean = true,
    val followNewest: Boolean = true,
)

@Composable
fun LogsScreen(onOpenMenu: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entries by AppLogger.entries.collectAsState()
    var settings by remember { mutableStateOf(DiagnosticViewSettings()) }
    var draftSettings by remember { mutableStateOf(settings) }
    var showSettings by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<AppLogger.Entry?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showShareOptions by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val filtered = remember(entries, settings) {
        entries.filter { entry ->
            (!settings.problemsOnly || entry.level != AppLogger.Level.Info) &&
                (settings.selectedLayers.isEmpty() || entry.layer in settings.selectedLayers) &&
                (settings.query.isBlank() || entry.searchText().contains(settings.query, ignoreCase = true))
        }
    }
    val groups = remember(filtered, settings.grouped, settings.newestFirst) {
        val chronological = if (settings.grouped) {
            filtered.groupBy { it.exchangeId ?: "event-${it.id}" }.values.toList()
        } else {
            filtered.map(::listOf)
        }
        if (settings.newestFirst) chronological.asReversed() else chronological
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (isScrolling) settings = settings.copy(followNewest = false)
            }
    }

    LaunchedEffect(filtered.size, settings.newestFirst, settings.followNewest) {
        if (settings.followNewest && groups.isNotEmpty()) {
            listState.scrollToItem(if (settings.newestFirst) 0 else groups.lastIndex)
        }
    }

    if (showSettings) {
        DiagnosticSettingsScreen(
            settings = draftSettings,
            hasLogs = entries.isNotEmpty(),
            onSettingsChange = { draftSettings = it },
            onApply = {
                settings = draftSettings
                showSettings = false
            },
            onBack = { showSettings = false },
            onClear = { confirmClear = true },
            modifier = modifier,
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            AppToolbar(
                onOpenMenu = onOpenMenu,
                title = "Diagnostics",
                actions = {
                    IconButton(
                        onClick = { showShareOptions = true },
                        enabled = entries.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share diagnostic logs")
                    }
                    IconButton(
                        onClick = {
                            draftSettings = settings
                            showSettings = true
                        },
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Diagnostic log settings")
                    }
                },
            )

            OutlinedTextField(
                value = settings.query,
                onValueChange = { settings = settings.copy(query = it) },
                label = { Text("Search command, error, raw data…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.newestFirst,
                    onClick = { settings = settings.copy(newestFirst = true) },
                    label = { Text("Newest first") },
                )
                FilterChip(
                    selected = !settings.newestFirst,
                    onClick = { settings = settings.copy(newestFirst = false) },
                    label = { Text("Oldest first") },
                )
                FilterChip(
                    selected = settings.followNewest,
                    onClick = { settings = settings.copy(followNewest = !settings.followNewest) },
                    label = { Text("Follow new events") },
                )
            }

            Text(
                text = "${filtered.size} of ${entries.size} events • ${if (settings.newestFirst) "newest first" else "oldest first"}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (groups.isEmpty()) {
                Text("No matching diagnostic events", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(groups, key = { group -> group.first().exchangeId ?: group.first().id }) { group ->
                        DiagnosticGroup(group = group, onOpen = { selectedEntry = it })
                    }
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
private fun DiagnosticSettingsScreen(
    settings: DiagnosticViewSettings,
    hasLogs: Boolean,
    onSettingsChange: (DiagnosticViewSettings) -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to diagnostic logs")
            }
            Text("Log view settings", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection("Layer") {
                FilterChip(
                    selected = settings.selectedLayers.isEmpty(),
                    onClick = { onSettingsChange(settings.copy(selectedLayers = emptySet())) },
                    label = { Text("All layers") },
                )
                AppLogger.Layer.entries.forEach { layer ->
                    FilterChip(
                        selected = layer in settings.selectedLayers,
                        onClick = {
                            val selectedLayers = settings.selectedLayers.toMutableSet().apply {
                                if (!add(layer)) remove(layer)
                            }
                            onSettingsChange(settings.copy(selectedLayers = selectedLayers))
                        },
                        label = { Text(layer.name) },
                    )
                }
            }

            SettingsSection("Content") {
                FilterChip(
                    selected = settings.problemsOnly,
                    onClick = { onSettingsChange(settings.copy(problemsOnly = !settings.problemsOnly)) },
                    label = { Text("Problems only") },
                )
                FilterChip(
                    selected = settings.grouped,
                    onClick = { onSettingsChange(settings.copy(grouped = !settings.grouped)) },
                    label = { Text("Group TX / RX / parser") },
                )
            }

            TextButton(onClick = onClear, enabled = hasLogs) {
                Text("Clear stored journal", color = MaterialTheme.colorScheme.error)
            }
        }

        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text("Apply")
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
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
