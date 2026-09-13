package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.ui.DashboardStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    stats: DashboardStats,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("داشبورد روزانه") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SpendComparisonCard(stats.thisMonthSpend, stats.lastMonthSpend) }

            item { SectionHeader("امروز چه کارهایی دارم؟ (${stats.todayItems.size})") }
            if (stats.todayItems.isEmpty()) {
                item { EmptyLine("امروز چیزی برنامه‌ریزی نشده") }
            } else {
                items(stats.todayItems, key = { "today_${it.id}" }) { SimpleReminderRow(it) }
            }

            item { SectionHeader("چه کارهایی عقب‌افتاده؟ (${stats.overdueItems.size})") }
            if (stats.overdueItems.isEmpty()) {
                item { EmptyLine("چیزی عقب نیفتاده، خوبه!") }
            } else {
                items(stats.overdueItems, key = { "overdue_${it.id}" }) { SimpleReminderRow(it, isWarning = true) }
            }

            item { SectionHeader("چه پرداخت‌هایی نزدیکه؟ (تا ۷ روز آینده)") }
            if (stats.upcomingPayments.isEmpty()) {
                item { EmptyLine("پرداخت نزدیکی نداری") }
            } else {
                items(stats.upcomingPayments, key = { "payment_${it.id}" }) { SimpleReminderRow(it, showAmount = true) }
            }
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
    val diffColor = if (diff > 0) Color(0xFFC62828) else Color(0xFF2E7D32)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("خرج این ماه چقدر بوده؟", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("$thisMonth تومان", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(diffText, style = MaterialTheme.typography.bodyMedium, color = diffColor)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "ماه قبل: $lastMonth تومان",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SimpleReminderRow(reminder: Reminder, isWarning: Boolean = false, showAmount: Boolean = false) {
    val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isWarning) Color(0xFFC62828) else Color.Unspecified
                )
                Text(
                    formatter.format(Date(reminder.triggerAtMillis)),
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
