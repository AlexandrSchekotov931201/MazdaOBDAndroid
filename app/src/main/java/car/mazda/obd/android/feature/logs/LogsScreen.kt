package car.mazda.obd.android.feature.logs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import car.mazda.obd.android.core.logs.AppLogger
import car.mazda.obd.android.ui.AppToolbar

@Composable
fun LogsScreen(
    onOpenMenu: () -> Unit,
    modifier: Modifier
) {
    val logs by AppLogger.logs.collectAsState()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        AppToolbar(
            onOpenMenu = onOpenMenu,
            title = "Logs"
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(logs) { line ->
                Text(
                    text = line,
                    modifier = Modifier.fillMaxWidth(),
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}
