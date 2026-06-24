package car.mazda.obd.android.feature.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.ui.AppToolbar

@Composable
fun LogsScreen(
    onOpenMenu: () -> Unit,
    modifier: Modifier
) {
    val entries by AppLogger.entries.collectAsState()
    var errorsOnly by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val visibleEntries = if (errorsOnly) {
        entries.filter { it.level != AppLogger.Level.Info }
    } else {
        entries
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        AppToolbar(
            onOpenMenu = onOpenMenu,
            title = "Logs"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            FilterChip(
                selected = !errorsOnly,
                onClick = { errorsOnly = false },
                label = { Text("All") }
            )
            FilterChip(
                selected = errorsOnly,
                onClick = { errorsOnly = true },
                label = { Text("Handled & errors") },
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(visibleEntries) { entry ->
                LogLine(entry = entry)
            }
        }
    }
}

@Composable
private fun LogLine(
    entry: AppLogger.Entry,
    modifier: Modifier = Modifier
) {
    val containerColor = when (entry.level) {
        AppLogger.Level.Info -> Color.Transparent
        AppLogger.Level.HandledError -> Color(0xFFFFF8E1)
        AppLogger.Level.Error -> Color(0xFFFFEBEE)
    }
    val contentColor = when (entry.level) {
        AppLogger.Level.Info -> MaterialTheme.colorScheme.onSurface
        AppLogger.Level.HandledError -> Color(0xFF6D4C00)
        AppLogger.Level.Error -> Color(0xFFB71C1C)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = entry.line,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            overflow = TextOverflow.Clip
        )
    }
}
