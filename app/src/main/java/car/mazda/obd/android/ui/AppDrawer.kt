package car.mazda.obd.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class AppDrawerDestination {
    Dashboard,
    Trips,
    Logs,
    Settings
}

@Composable
fun AppDrawer(
    selectedDestination: AppDrawerDestination,
    isReady: Boolean,
    onSelectDestination: (AppDrawerDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight(),
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        color = Color(0xFFF7F7FA),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            DrawerHeader(isReady = isReady)

            Spacer(modifier = Modifier.height(28.dp))

            DrawerItem(
                text = "Dashboard",
                selected = selectedDestination == AppDrawerDestination.Dashboard,
                onClick = { onSelectDestination(AppDrawerDestination.Dashboard) }
            )
            DrawerItem(
                text = "Trips",
                selected = selectedDestination == AppDrawerDestination.Trips,
                onClick = { onSelectDestination(AppDrawerDestination.Trips) }
            )
            DrawerItem(
                text = "Logs",
                selected = selectedDestination == AppDrawerDestination.Logs,
                onClick = { onSelectDestination(AppDrawerDestination.Logs) }
            )
            DrawerItem(
                text = "Settings",
                selected = selectedDestination == AppDrawerDestination.Settings,
                onClick = { onSelectDestination(AppDrawerDestination.Settings) }
            )
        }
    }
}

@Composable
private fun DrawerHeader(isReady: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Mazda OBD",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF20242A)
        )
        Text(
            text = "Vehicle dashboard",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF6B7078)
        )
        DrawerStatusChip(isReady = isReady)
    }
}

@Composable
private fun DrawerStatusChip(isReady: Boolean) {
    val statusColor = if (isReady) Color(0xFF2E7D32) else Color(0xFFC62828)
    val statusText = if (isReady) "Ready" else "Offline"

    Surface(
        shape = RoundedCornerShape(50),
        color = Color(0xFFE9EBF2),
        contentColor = Color(0xFF3E424A)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DrawerItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val itemShape = RoundedCornerShape(18.dp)
    val backgroundColor = if (selected) Color(0xFFE5E9F8) else Color.Transparent
    val textColor = if (selected) Color(0xFF20242A) else Color(0xFF626772)
    val fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(itemShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(50))
                .background(if (selected) Color(0xFFC62828) else Color.Transparent)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = fontWeight,
            color = textColor
        )
    }
}
