package ir.naderinia.nsa.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.PregnantWoman
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.sectionKey
import ir.naderinia.nsa.ui.components.pressScale
import ir.naderinia.nsa.ui.components.rememberPressSource
import ir.naderinia.nsa.ui.theme.LocalClassicStyle
import ir.naderinia.nsa.util.JalaliCalendar
import kotlinx.coroutines.delay


private data class CategoryTile(
    val label: String,
    val categoryKey: String,
    val icon: ImageVector,
    val accent: Color
)

private val CATEGORY_TILES = listOf(
    CategoryTile("عمومی", "عمومی", Icons.Default.ChecklistRtl, Color(0xFF4F46E5)),
    CategoryTile("سرویس وسیله نقلیه", "وسیله نقلیه", Icons.Default.DirectionsCar, Color(0xFF0D9488)),
    CategoryTile("جلسات", "جلسات", Icons.Default.Groups, Color(0xFFF59E0B)),
    CategoryTile("ساختمان", "ساختمان", Icons.Default.Apartment, Color(0xFF64748B)),
    CategoryTile("مالی", "مالی", Icons.Default.AccountBalanceWallet, Color(0xFFDC2626)),
    CategoryTile("تولد و سالگرد", "تولد و سالگرد", Icons.Default.Cake, Color(0xFFDB2777)),
    CategoryTile("دارو", "دارو", Icons.Default.Medication, Color(0xFF16A34A)),
    CategoryTile("بارداری", "بارداری", Icons.Default.PregnantWoman, Color(0xFFE11D48)),
    CategoryTile("نوزاد و کودک", "نوزاد و کودک", Icons.Default.ChildCare, Color(0xFF0284C7)),
    CategoryTile("مذهبی", "مذهبی", Icons.Filled.Book, Color(0xFF7C3AED)),
    CategoryTile("مدرسه", "مدرسه", Icons.Default.School, Color(0xFFEA580C))
)

private fun fa(n: Int): String = JalaliCalendar.toPersianDigits(n.toString())

@Composable
fun RemindersOverview(
    reminders: List<Reminder>,
    onOpenToday: () -> Unit,
    onOpenOverdue: () -> Unit,
    onOpenActive: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    if (LocalClassicStyle.current) {
        ClassicRemindersOverview(reminders, onOpenToday, onOpenOverdue, onOpenActive, onOpenCategory)
        return
    }

    val active = reminders.filter { !it.isDone }
    val now = System.currentTimeMillis()

    // Same rule as the «امروز» list: still ahead of now AND on today's calendar date.
    val todayCal = java.util.Calendar.getInstance()
    val todayCount = active.count {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = it.triggerAtMillis }
        it.triggerAtMillis >= now &&
            c.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
            c.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    val overdueCount = active.count {
        it.triggerAtMillis < now
    }

    // Items that already played their entrance animation don't replay it when they scroll
    // back into view — the animation is a hello, not a loop.
    val played = remember { mutableSetOf<Int>() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // کارت اصلی: همه کارهای فعال
        item(span = { GridItemSpan(2) }) {
            Entrance(index = 0, played = played) {
                HeroCard(
                    activeCount = active.size,
                    todayCount = todayCount,
                    overdueCount = overdueCount,
                    onClick = onOpenActive
                )
            }
        }

        // امروز
        item {
            Entrance(index = 1, played = played) {
                QuickTile(
                    label = "امروز",
                    count = todayCount,
                    icon = Icons.Default.Today,
                    accent = Color(0xFFF59E0B),
                    highlightColor = MaterialTheme.colorScheme.tertiaryContainer,
                    highlightContent = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = onOpenToday
                )
            }
        }

        // عقب‌افتاده
        item {
            Entrance(index = 2, played = played) {
                QuickTile(
                    label = "عقب‌افتاده",
                    count = overdueCount,
                    icon = Icons.Default.EventBusy,
                    accent = MaterialTheme.colorScheme.error,
                    highlightColor = MaterialTheme.colorScheme.errorContainer,
                    highlightContent = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = onOpenOverdue
                )
            }
        }

        // دسته‌بندی‌ها
        itemsIndexed(CATEGORY_TILES) { index, tile ->
            val count = active.count {
                it.sectionKey() == tile.categoryKey
            }

            Entrance(index = index + 3, played = played) {
                QuickTile(
                    label = tile.label,
                    count = count,
                    icon = tile.icon,
                    accent = tile.accent,
                    highlightColor = null,
                    highlightContent = null,
                    onClick = {
                        onOpenCategory(tile.categoryKey)
                    }
                )
            }
        }
    }
}

/** Fade + small upward slide, staggered by [index]. Keeps its size, so the grid never jumps. */
@Composable
private fun Entrance(
    index: Int,
    played: MutableSet<Int>,
    content: @Composable () -> Unit
) {
    val firstTime = remember { index !in played }
    val progress = remember { Animatable(if (firstTime) 0f else 1f) }

    LaunchedEffect(Unit) {
        if (firstTime) {
            delay((index * 40L).coerceAtMost(400L))
            progress.animateTo(1f, tween(durationMillis = 380))
            played.add(index)
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 48f
        }
    ) {
        content()
    }
}

@Composable
private fun HeroCard(
    activeCount: Int,
    todayCount: Int,
    overdueCount: Int,
    onClick: () -> Unit
) {
    val source = rememberPressSource()
    val scheme = MaterialTheme.colorScheme

    val subtitle = when {
        overdueCount > 0 -> "${fa(overdueCount)} مورد عقب افتاده؛ بزن ببینیم"
        todayCount > 0 -> "${fa(todayCount)} کار برای امروز داری"
        activeCount > 0 -> "برای امروز کاری نداری، راحت باش"
        else -> "همه‌چیز مرتبه؛ یه یادآوری جدید بساز"
    }

    Card(
        onClick = onClick,
        interactionSource = source,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source, pressedScale = 0.98f)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(scheme.primary, scheme.secondary))
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${fa(activeCount)} کار فعال داری",
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onPrimary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onPrimary.copy(alpha = 0.9f)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(scheme.onPrimary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = scheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    label: String,
    count: Int,
    icon: ImageVector,
    accent: Color,
    /** When non-null and [count] > 0 the tile lights up (e.g. red for overdue). */
    highlightColor: Color?,
    highlightContent: Color?,
    onClick: () -> Unit
) {
    val source = rememberPressSource()
    val lit = highlightColor != null && count > 0

    val container = if (lit) highlightColor!! else MaterialTheme.colorScheme.surface
    val textColor = if (lit) highlightContent!! else MaterialTheme.colorScheme.onSurface
    val subColor = if (lit) highlightContent!! else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        interactionSource = source,
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = if (lit) 0.dp else 1.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(source)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = if (lit) 0.22f else 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = textColor,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = if (count == 0) "خالی" else "${fa(count)} مورد",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal,
                color = subColor
            )
        }
    }
}

private val CLASSIC_TILES = CATEGORY_TILES.map { Triple(it.label, it.categoryKey, it.icon) }

@Composable
private fun ClassicRemindersOverview(
    reminders: List<Reminder>,
    onOpenToday: () -> Unit,
    onOpenOverdue: () -> Unit,
    onOpenActive: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    val active = reminders.filter { !it.isDone }
    val now = System.currentTimeMillis()

    // Same rule as the «امروز» list: still ahead of now AND on today's calendar date.
    val todayCal = java.util.Calendar.getInstance()
    val todayCount = active.count {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = it.triggerAtMillis }
        it.triggerAtMillis >= now &&
            c.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
            c.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    val overdueCount = active.count {
        it.triggerAtMillis < now
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // همه کارهای فعال
        item(span = { GridItemSpan(2) }) {
            Card(
                onClick = onOpenActive,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        "${active.size} کار فعال داری",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "برای مشاهده همه کارهای فعال لمس کنید",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // امروز
        item {
            ClassicQuickTile(
                label = "امروز",
                count = todayCount,
                icon = Icons.Default.Today,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = onOpenToday
            )
        }

        // عقب‌افتاده
        item {
            ClassicQuickTile(
                label = "عقب‌افتاده",
                count = overdueCount,
                icon = Icons.Default.EventBusy,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                onClick = onOpenOverdue
            )
        }

        // دسته‌بندی‌ها
        items(CLASSIC_TILES) { tile ->
            val count = active.count {
                it.sectionKey() == tile.second
            }

            ClassicQuickTile(
                label = tile.first,
                count = count,
                icon = tile.third,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                onClick = {
                    onOpenCategory(tile.second)
                }
            )
        }
    }
}

@Composable
private fun ClassicQuickTile(
    label: String,
    count: Int,
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall
            )

            Text(
                text = "$count مورد",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}