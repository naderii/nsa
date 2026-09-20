package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.ui.DashboardStats
import ir.naderinia.nsa.ui.components.EmptyHint
import ir.naderinia.nsa.ui.components.SectionHeader
import ir.naderinia.nsa.util.CarPrefs
import ir.naderinia.nsa.util.JalaliCalendar

/**
 * No Scaffold here: embedded as a tab inside HomeScreen, which owns the
 * single shared Scaffold (top bar + bottom nav bar).
 */
@Composable
fun DashboardScreen(stats: DashboardStats) {
    val context = LocalContext.current
    var currentKm by remember { mutableStateOf(CarPrefs.getCurrentKm(context)) }
    var showKmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SpendComparisonCard(stats.thisMonthSpend, stats.lastMonthSpend) }

        item { CarOdometerCard(currentKm = currentKm, onEditClick = { showKmDialog = true }) }

        item { SectionHeader("امروز چه کارهایی دارم؟ (${stats.todayItems.size})") }
        if (stats.todayItems.isEmpty()) {
            item { EmptyHint("امروز چیزی برنامه‌ریزی نشده") }
        } else {
            items(stats.todayItems, key = { "today_${it.id}" }) { SimpleReminderRow(it) }
        }

        item { SectionHeader("چه کارهایی عقب‌افتاده؟ (${stats.overdueItems.size})") }
        if (stats.overdueItems.isEmpty()) {
            item { EmptyHint("چیزی عقب نیفتاده، خوبه!") }
        } else {
            items(stats.overdueItems, key = { "overdue_${it.id}" }) { SimpleReminderRow(it, isWarning = true) }
        }

        item { SectionHeader("چه پرداخت‌هایی نزدیکه؟ (تا ۷ روز آینده)") }
        if (stats.upcomingPayments.isEmpty()) {
            item { EmptyHint("پرداخت نزدیکی نداری") }
        } else {
            items(stats.upcomingPayments, key = { "payment_${it.id}" }) { SimpleReminderRow(it, showAmount = true) }
        }
    }

    if (showKmDialog) {
        var kmText by remember { mutableStateOf(currentKm.toString()) }
        AlertDialog(
            onDismissRequest = { showKmDialog = false },
            title = { Text("به‌روزرسانی کیلومتر ماشین") },
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

@Composable
private fun CarOdometerCard(currentKm: Long, onEditClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("کیلومتر فعلی ماشین", style = MaterialTheme.typography.titleSmall)
                Text("$currentKm کیلومتر", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onEditClick) { Text("به‌روزرسانی") }
        }
    }
}

@Composable
private fun SpendComparisonCard(thisMonth: Long, lastMonth: Long) {
    val diff = thisMonth - lastMonth
    val diffText = when {
        lastMonth == 0L && thisMonth == 0L -> "هنوز پرداختی ثبت نشده"
        diff > 0 -> "${diff} تومان بیشتر از ماه قبل"
        diff < 0 -> "${-diff} تومان کمتر از ماه قبل"
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
                "$thisMonth تومان",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(diffText, style = MaterialTheme.typography.bodyMedium, color = diffColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "ماه قبل: $lastMonth تومان",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
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
                Text("$remaining تومان", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
