package car.mazda.obd.android.feature.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import car.mazda.obd.android.core.logs.AppLogger

@Composable
internal fun DiagnosticGroup(group: List<AppLogger.Entry>, onOpen: (AppLogger.Entry) -> Unit) {
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
internal fun EntryDetails(entry: AppLogger.Entry, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val details = entry.detailedText()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.layer.name} • ${entry.operation}") },
        text = { LazyColumn { item { Text(details, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) } } },
        confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(details)) }) { Text("Copy") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
