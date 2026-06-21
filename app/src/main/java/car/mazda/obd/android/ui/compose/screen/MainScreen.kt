package car.mazda.obd.android.ui.compose.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.ui.MainViewModel
import car.mazda.obd.android.ui.compose.MazdaStyleTachometer

@Composable
internal fun MainScreen(
    viewModel: MainViewModel,
    onOpenLogs: () -> Unit,
    modifier: Modifier
) {
    val connectionTextState by viewModel.connectionTextState.collectAsStateWithLifecycle()
    val rpmState by viewModel.rpmState.collectAsStateWithLifecycle()

    MainContent(
        connectionText = connectionTextState,
        rpm = rpmState,
        onOpenLogs = onOpenLogs,
        modifier = modifier,
    )
}

@Composable
private fun MainContent(
    connectionText: String,
    rpm: Int,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Status: $connectionText",
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(onClick = onOpenLogs) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Article,
                    contentDescription = "Open logs"
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val tachometerSize = minOf(maxWidth, maxHeight)

            Box(
                modifier = Modifier
                    .size(tachometerSize)
                    .sizeIn(maxWidth = 520.dp, maxHeight = 520.dp),
                contentAlignment = Alignment.Center
            ) {
                MazdaStyleTachometer(
                    modifier = Modifier.fillMaxSize(),
                    rpm = rpm,
                )
            }
        }
    }
}
