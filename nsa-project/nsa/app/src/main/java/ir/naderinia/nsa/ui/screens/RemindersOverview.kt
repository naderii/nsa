package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.Reminder

private data class CategoryTile(val label: String, val categoryKey: String, val icon: ImageVector)

private val CATEGORY_TILES = listOf(
    CategoryTile("عمومی", "عمومی", Icons.Default.ChecklistRtl),
    CategoryTile("سرویس خودرو", "ماشین", Icons.Default.DirectionsCar),
    CategoryTile("جلسات", "جلسات", Icons.Default.Groups),
    CategoryTile("ساختمان", "ساختمان", Icons.Default.Apartment),
    CategoryTile("مالی", "مالی", Icons.Default.AccountBalanceWallet),
    CategoryTile("تولد و سالگرد", "تولد و سالگرد", Icons.Default.Cake),
    CategoryTile("دارو", "دارو", Icons.Default.Medication),
    CategoryTile("بارداری", "بارداری", Icons.Default.PregnantWoman),
    CategoryTile("نوزاد و کودک", "نوزاد و کودک", Icons.Default.ChildCare),
    CategoryTile("مذهبی", "مذهبی", Icons.Default.MenuBook)
)

@Composable
fun RemindersOverview(
    reminders: List<Reminder>,
    onOpenToday: () -> Unit,
    onOpenOverdue: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    val active = reminders.filter { !it.isDone }
    val now = System.currentTimeMillis()
    val todayCount = active.count { it.triggerAtMillis in now..(now + 24L * 60 * 60 * 1000) }
    val overdueCount = active.count { it.triggerAtMillis < now }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        "${active.size} کار فعال داری",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        item {
            QuickTile(
                label = "امروز",
                count = todayCount,
                icon = Icons.Default.Today,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = onOpenToday
            )
        }
        item {
            QuickTile(
                label = "عقب‌افتاده",
                count = overdueCount,
                icon = Icons.Default.EventBusy,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                onClick = onOpenOverdue
            )
        }

        items(CATEGORY_TILES) { tile ->
            val count = active.count { it.category == tile.categoryKey }
            QuickTile(
                label = tile.label,
                count = count,
                icon = tile.icon,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onOpenCategory(tile.categoryKey) }
            )
        }
    }
}

@Composable
private fun QuickTile(
    label: String,
    count: Int,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text("$count مورد", style = MaterialTheme.typography.bodySmall)
        }
    }
}
