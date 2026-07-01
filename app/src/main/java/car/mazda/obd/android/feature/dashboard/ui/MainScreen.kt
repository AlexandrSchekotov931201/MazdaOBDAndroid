package car.mazda.obd.android.feature.dashboard.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import car.mazda.obd.android.feature.dashboard.MainViewModel
import car.mazda.obd.android.feature.monitor.MonitorConnectionStatus
import car.mazda.obd.android.feature.warmup.EngineWarmupGuidance
import car.mazda.obd.android.feature.warmup.EngineWarmupStage
import car.mazda.obd.android.ui.AppToolbar

@Composable
internal fun MainScreen(
    viewModel: MainViewModel,
    onOpenMenu: () -> Unit,
    modifier: Modifier
) {
    val connectionStatus by viewModel.connectionStatusState.collectAsStateWithLifecycle()
    val rpmState by viewModel.rpmState.collectAsStateWithLifecycle()
    val coolantTempState by viewModel.coolantTempState.collectAsStateWithLifecycle()
    val warmupTextState by viewModel.warmupTextState.collectAsStateWithLifecycle()

    MainContent(
        connectionStatus = connectionStatus,
        rpm = rpmState,
        coolantTemp = coolantTempState,
        warmupText = warmupTextState,
        onOpenMenu = onOpenMenu,
        modifier = modifier,
    )
}

@Composable
private fun MainContent(
    connectionStatus: MonitorConnectionStatus,
    rpm: Int,
    coolantTemp: Int?,
    warmupText: String,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engineStatus = if (connectionStatus == MonitorConnectionStatus.Ready) {
        coolantTemp.toEngineStatus()
    } else {
        EngineStatus.NoData
    }
    val engineText = if (connectionStatus == MonitorConnectionStatus.Ready) {
        warmupText
    } else {
        "Coolant temp: --"
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppToolbar(onOpenMenu = onOpenMenu) {
            ConnectionIndicator(status = connectionStatus)
        }

        EngineStatusBanner(
            status = engineStatus,
            warmupText = engineText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (connectionStatus == MonitorConnectionStatus.Ready) 1f else 0.42f),
                    rpm = rpm,
                )
            }
        }
    }
}

@Composable
private fun ConnectionIndicator(status: MonitorConnectionStatus) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = status.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Surface(
                modifier = Modifier.size(10.dp),
                shape = CircleShape,
                color = status.indicatorColor(),
                content = {}
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun EngineStatusBanner(
    status: EngineStatus,
    warmupText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(top = 8.dp),
        color = status.containerColor,
        contentColor = status.contentColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = status.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = warmupText,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun MonitorConnectionStatus.indicatorColor(): Color = when (this) {
    MonitorConnectionStatus.Ready -> Color(0xFF2E7D32)
    MonitorConnectionStatus.Connecting -> Color(0xFF1565C0)
    MonitorConnectionStatus.Reconnecting -> Color(0xFFEF6C00)
    MonitorConnectionStatus.Offline -> Color(0xFFC62828)
}

private enum class EngineStatus(
    val title: String,
    val containerColor: Color,
    val contentColor: Color
) {
    NoData(
        title = "Engine data pending",
        containerColor = Color(0xFFECEFF1),
        contentColor = Color(0xFF263238)
    ),
    WarmingUp(
        title = "Warm-up mode",
        containerColor = Color(0xFFFFF3E0),
        contentColor = Color(0xFF5D3A00)
    ),
    NormalCity(
        title = "Normal city driving",
        containerColor = Color(0xFFE8F5E9),
        contentColor = Color(0xFF1B5E20)
    ),
    FullyWarm(
        title = "Fully warm",
        containerColor = Color(0xFFE8F5E9),
        contentColor = Color(0xFF1B5E20)
    ),
    Critical(
        title = "Critical temperature",
        containerColor = Color(0xFFFFEBEE),
        contentColor = Color(0xFFB71C1C)
    )
}

private fun Int?.toEngineStatus(): EngineStatus =
    when (this?.let(EngineWarmupGuidance::stageFor)) {
        null -> EngineStatus.NoData
        EngineWarmupStage.VeryGentle,
        EngineWarmupStage.Gentle -> EngineStatus.WarmingUp
        EngineWarmupStage.NormalCity -> EngineStatus.NormalCity
        EngineWarmupStage.FullyWarm -> EngineStatus.FullyWarm
        EngineWarmupStage.Critical -> EngineStatus.Critical
    }
