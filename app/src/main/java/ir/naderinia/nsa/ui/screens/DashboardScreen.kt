package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.ui.DashboardStats
import ir.naderinia.nsa.ui.FinancialSummary
import ir.naderinia.nsa.ui.MonthSpend
import ir.naderinia.nsa.ui.components.EmptyHint
import ir.naderinia.nsa.ui.components.SectionHeader
import ir.naderinia.nsa.util.CarPrefs
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.util.grouped
import java.util.Calendar
import kotlin.math.roundToInt

private val WEEKDAY_NAMES = arrayOf(
    "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"
)

private const val PREVIEW_LIMIT = 5

/**
 * No Scaffold here: embedded as a tab inside HomeScreen, which owns the
 * single shared Scaffold (top bar + bottom nav bar).
 */
@Composable
fun DashboardScreen(
    stats: DashboardStats,
    financialSummary: FinancialSummary,
    onOpenToday: () -> Unit,
    onOpenOverdue: () -> Unit,
    onOpenActive: () -> Unit,
    onOpenFinancial: () -> Unit
) {
    val context = LocalContext.current
    var currentKm by remember { mutableStateOf(CarPrefs.getCurrentKm(context)) }
    var showKmDialog by remember { mutableStateOf(false) }
    val avgKmPerDay = remember(currentKm) { CarPrefs.averageKmPerDay(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TodayHeaderCard(stats) }

        stats.nextItem?.let { next ->
            item { NextTaskCard(next) }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    label = "عقب‌افتاده",
                    count = stats.overdueItems.size,
                    container = if (stats.overdueItems.isEmpty()) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.errorContainer,
                    content = if (stats.overdueItems.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onErrorContainer,
                    onClick = onOpenOverdue,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "امروز",
                    count = stats.todayItems.size,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenToday,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "۷ روز آینده",
                    count = stats.weekCount,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onOpenActive,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (stats.medicationExpectedToday > 0 || stats.medicationTakenWeek > 0) {
            item { MedicationCard(stats) }
        }

        stats.nextBirthday?.let { birthday ->
            item { BirthdayCard(birthday) }
        }

        if (financialSummary.totalDebtRemaining != 0L ||
            financialSummary.totalCreditRemaining != 0L ||
            stats.upcomingPayments.isNotEmpty()
        ) {
            item { FinanceCard(stats, financialSummary, onOpenFinancial) }
        }

        item { SpendChartCard(stats) }

        item { SectionHeader("امروز چه کارهایی دارم؟ (${stats.todayItems.size})") }
        if (stats.todayItems.isEmpty()) {
            item { EmptyHint("امروز چیزی برنامه‌ریزی نشده") }
        } else {
            items(stats.todayItems.take(PREVIEW_LIMIT), key = { "today_${it.id}" }) { SimpleReminderRow(it) }
            if (stats.todayItems.size > PREVIEW_LIMIT) {
                item {
                    TextButton(onClick = onOpenToday) { Text("نمایش همه (${stats.todayItems.size})") }
                }
            }
        }

        item { SectionHeader("چه کارهایی عقب‌افتاده؟ (${stats.overdueItems.size})") }
        if (stats.overdueItems.isEmpty()) {
            item { EmptyHint("چیزی عقب نیفتاده، خوبه!") }
        } else {
            items(stats.overdueItems.take(PREVIEW_LIMIT), key = { "overdue_${it.id}" }) {
                SimpleReminderRow(it, isWarning = true)
            }
            if (stats.overdueItems.size > PREVIEW_LIMIT) {
                item {
                    TextButton(onClick = onOpenOverdue) { Text("نمایش همه (${stats.overdueItems.size})") }
                }
            }
        }

        item { SectionHeader("چه پرداخت‌هایی نزدیکه؟ (تا ۷ روز آینده)") }
        if (stats.upcomingPayments.isEmpty()) {
            item { EmptyHint("پرداخت نزدیکی نداری") }
        } else {
            items(stats.upcomingPayments.take(PREVIEW_LIMIT), key = { "payment_${it.id}" }) {
                SimpleReminderRow(it, showAmount = true)
            }
            if (stats.upcomingPayments.size > PREVIEW_LIMIT) {
                item {
                    TextButton(onClick = onOpenFinancial) { Text("نمایش همه در بخش مالی (${stats.upcomingPayments.size})") }
                }
            }
        }

        item { CarOdometerCard(currentKm = currentKm, avgPerDay = avgKmPerDay, onEditClick = { showKmDialog = true }) }
    }

    if (showKmDialog) {
        var kmText by remember { mutableStateOf(currentKm.toString()) }
        AlertDialog(
            onDismissRequest = { showKmDialog = false },
            title = { Text("به‌روزرسانی کیلومتر وسیله نقلیه") },
            text = {
                OutlinedTextField(
                    value = kmText,
                    onValueChange = { kmText = it.filter { c -> c.isDigit() } },
                    label = { Text("کیلومتر فعلی") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    kmText.toLongOrNull()?.let {
                        CarPrefs.setCurrentKm(context, it)
                        currentKm = it
                    }
                    showKmDialog = false
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showKmDialog = false }) { Text("انصراف") }
            }
        )
    }
}

// ---------------------------------------------------------------- header

@Composable
private fun TodayHeaderCard(stats: DashboardStats) {
    val now = remember(stats) { System.currentTimeMillis() }
    val cal = remember(stats) { Calendar.getInstance() }
    val jalali = JalaliCalendar.millisToJalali(now)
    val weekday = WEEKDAY_NAMES[cal.get(Calendar.DAY_OF_WEEK) - 1]
    val dateText = JalaliCalendar.toPersianDigits(
        "${jalali.day} ${JalaliCalendar.monthNames[jalali.month - 1]} ${jalali.year}"
    )
    val greeting = when (cal.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "صبح بخیر"
        in 12..16 -> "روز بخیر"
        in 17..20 -> "عصر بخیر"
        else -> "شب بخیر"
    }
    val overdue = stats.overdueItems.size
    val today = stats.todayItems.size
    val summary = when {
        overdue > 0 && today > 0 -> "$today کار امروز و $overdue کار عقب‌افتاده داری"
        overdue > 0 -> "$overdue کار عقب‌افتاده داری"
        today > 0 -> "$today کار برای امروز داری"
        stats.todayTotal > 0 -> "همه‌ی کارهای امروز انجام شده"
        else -> "امروز کار بازی نداری"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                greeting,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "$weekday، $dateText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                summary,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (stats.todayTotal > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { stats.todayDone.toFloat() / stats.todayTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    if (stats.todayDone == stats.todayTotal) "آفرین! همه‌ی کارهای امروز انجام شد ✓"
                    else "${stats.todayDone} از ${stats.todayTotal} کار امروز انجام شده",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun NextTaskCard(reminder: Reminder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "کار بعدی",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
            Text(
                reminder.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${timeUntil(reminder.triggerAtMillis)} • ${JalaliCalendar.formatDateTime(reminder.triggerAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun timeUntil(millis: Long): String {
    val minutes = (millis - System.currentTimeMillis()) / 60_000
    return when {
        minutes < 1 -> "همین الان"
        minutes < 60 -> "$minutes دقیقه دیگه"
        minutes < 24 * 60 -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "$h ساعت دیگه" else "$h ساعت و $m دقیقه دیگه"
        }
        else -> "${minutes / (24 * 60)} روز دیگه"
    }
}

// ---------------------------------------------------------------- stats row

@Composable
private fun StatTile(
    label: String,
    count: Int,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = content
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------- medication / birthday

@Composable
private fun MedicationCard(stats: DashboardStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("داروها", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            if (stats.medicationExpectedToday > 0) {
                Text(
                    "امروز ${stats.medicationTakenToday} از ${stats.medicationExpectedToday} نوبت",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = {
                        (stats.medicationTakenToday.toFloat() / stats.medicationExpectedToday.toFloat())
                            .coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
            } else {
                Text("امروز ${stats.medicationTakenToday} نوبت ثبت شده", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "۷ روز اخیر: ${stats.medicationTakenWeek} نوبت",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BirthdayCard(reminder: Reminder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "🎂 ${reminder.title}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                birthdayLine(reminder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

// ---------------------------------------------------------------- finance

@Composable
private fun FinanceCard(
    stats: DashboardStats,
    summary: FinancialSummary,
    onClick: () -> Unit
) {
    val netColor = if (summary.net >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("وضعیت مالی", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "مانده‌ی خالص: ${if (summary.net >= 0) "+" else ""}${summary.net.grouped()} تومان",
                style = MaterialTheme.typography.titleLarge,
                color = netColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                buildString {
                    append("${stats.upcomingPayments.size} پرداخت تا ۷ روز آینده")
                    if (stats.weekChecks > 0) append(" (${stats.weekChecks} چک)")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- spend chart

@Composable
private fun SpendChartCard(stats: DashboardStats) {
    val thisMonth = stats.thisMonthSpend
    val lastMonth = stats.lastMonthSpend
    val diff = thisMonth - lastMonth
    val diffText = when {
        lastMonth == 0L && thisMonth == 0L -> "هنوز پرداختی ثبت نشده"
        diff > 0 -> "${diff.grouped()} تومان بیشتر از ماه قبل"
        diff < 0 -> "${(-diff).grouped()} تومان کمتر از ماه قبل"
        else -> "دقیقاً مثل ماه قبل"
    }
    val diffColor = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "خرج این ماه چقدر بوده؟",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "${thisMonth.grouped()} تومان",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(diffText, style = MaterialTheme.typography.bodyMedium, color = diffColor)

            if (stats.monthlySpend.any { it.amount > 0 }) {
                Spacer(modifier = Modifier.height(16.dp))
                SpendBars(stats.monthlySpend)
            }
        }
    }
}

@Composable
private fun SpendBars(months: List<MonthSpend>) {
    val max = months.maxOf { it.amount }.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.onTertiaryContainer

    Row(
        modifier = Modifier.fillMaxWidth().height(110.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        months.forEachIndexed { index, month ->
            val isCurrent = index == months.lastIndex
            val fraction = month.amount.toFloat() / max.toFloat()
            val barHeight = if (month.amount > 0) (70f * fraction).coerceAtLeast(4f) else 2f

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(barHeight.dp)
                        .background(
                            barColor.copy(alpha = if (isCurrent) 1f else 0.35f),
                            RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                        )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    month.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = barColor,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------- misc

@Composable
private fun CarOdometerCard(currentKm: Long, avgPerDay: Double?, onEditClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("کیلومتر فعلی وسیله نقلیه", style = MaterialTheme.typography.titleSmall)
                Text("$currentKm کیلومتر", style = MaterialTheme.typography.bodyMedium)
                if (avgPerDay != null) {
                    Text(
                        "میانگین رانندگی: حدود ${avgPerDay.roundToInt()} کیلومتر در روز",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onEditClick) { Text("به‌روزرسانی") }
        }
    }
}

@Composable
private fun SimpleReminderRow(reminder: Reminder, isWarning: Boolean = false, showAmount: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    JalaliCalendar.formatDateTime(reminder.triggerAtMillis),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (showAmount && reminder.amount != null) {
                val remaining = reminder.amount - reminder.amountPaid
                Text("${remaining.grouped()} تومان", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
