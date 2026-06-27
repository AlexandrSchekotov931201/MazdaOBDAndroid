package car.mazda.obd.android.feature.logs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.core.logs.DiagnosticReportExporter
import car.mazda.obd.android.ui.AppToolbar
import kotlinx.coroutines.flow.distinctUntilChanged

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

    val filtered = remember(entries, settings) { entries.filterFor(settings) }
    val groups = remember(filtered, settings.grouped, settings.newestFirst) { filtered.groupFor(settings) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (isScrolling) settings = settings.copy(followNewest = false)
            }
    }

    LaunchedEffect(entries.size, settings.followNewest) {
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
                    IconButton(onClick = { showShareOptions = true }, enabled = entries.isNotEmpty()) {
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
                Text(
                    "No matching diagnostic events",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = groups,
                        key = { group -> "${settings.newestFirst}-${group.first().exchangeId ?: group.first().id}" },
                    ) { group ->
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
                            DiagnosticReportExporter.share(context, entries)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Share entire journal") }
                    TextButton(
                        onClick = {
                            showShareOptions = false
                            DiagnosticReportExporter.share(context, filtered)
                        },
                        enabled = filtered.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Share current filtered view") }
                }
            },
            confirmButton = { TextButton(onClick = { showShareOptions = false }) { Text("Cancel") } },
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
