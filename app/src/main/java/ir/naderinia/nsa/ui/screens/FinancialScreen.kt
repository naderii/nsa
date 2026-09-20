package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.ui.FinancialSummary
import ir.naderinia.nsa.util.JalaliCalendar

/**
 * No Scaffold/TopAppBar here: this screen is embedded as a tab inside
 * HomeScreen, which owns the single shared Scaffold (with the bottom nav
 * bar). Nesting another Scaffold here would double up app bars/padding.
 */
@Composable
fun FinancialScreen(
    reminders: List<Reminder>,
    summary: FinancialSummary,
    onRecordPayment: (Reminder, Long) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<FinancialType?>(null) } // null = all

    val filtered = if (selectedFilter == null) reminders else reminders.filter { it.financialType == selectedFilter }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SummaryCard(summary) }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("همه") }
                    )
                }
                items(FinancialType.entries.toList()) { type ->
                    FilterChip(
                        selected = selectedFilter == type,
                        onClick = { selectedFilter = type },
                        label = { Text(type.label()) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "چیزی توی این دسته نیست",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { reminder ->
                FinancialItemCard(reminder, onRecordPayment)
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: FinancialSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "خلاصه وضعیت مالی",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryStat(
                    label = "جمع بدهی من",
                    value = summary.totalDebtRemaining,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    label = "جمع طلب من",
                    value = summary.totalCreditRemaining,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            val netColor = if (summary.net >= 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            Text(
                text = "مانده‌ی خالص: ${if (summary.net >= 0) "+" else ""}${summary.net} تومان",
                style = MaterialTheme.typography.titleMedium,
                color = netColor
            )
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text("$value تومان", style = MaterialTheme.typography.titleLarge, color = color)
    }
}

@Composable
private fun FinancialItemCard(
    reminder: Reminder,
    onRecordPayment: (Reminder, Long) -> Unit
) {
    val showDialog = remember { mutableStateOf(false) }
    val amountText = remember { mutableStateOf("") }
    val total = reminder.amount ?: 0
    val remaining = total - reminder.amountPaid
    val progress = if (total > 0) (reminder.amountPaid.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val type = reminder.financialType ?: FinancialType.DEBT

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    type.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = reminder.title, style = MaterialTheme.typography.titleMedium)
                    reminder.counterparty?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (reminder.isDone) {
                    AssistChip(onClick = {}, enabled = false, label = { Text("تسویه شده") })
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "سررسید: ${JalaliCalendar.formatDate(reminder.triggerAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!reminder.isDone && total > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "باقی‌مانده: $remaining از $total تومان",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!reminder.isDone) {
                TextButton(
                    onClick = {
                        amountText.value = ""
                        showDialog.value = true
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("ثبت پرداخت")
                }
            }
        }
    }

    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = {
                showDialog.value = false
                amountText.value = ""
            },
            title = { Text("ثبت پرداخت") },
            text = {
                OutlinedTextField(
                    value = amountText.value,
                    onValueChange = { value -> amountText.value = value.filter { c -> c.isDigit() } },
                    label = { Text("مبلغ (تومان)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    amountText.value.toLongOrNull()?.let { paymentAmount ->
                        onRecordPayment(reminder, paymentAmount)
                    }
                    showDialog.value = false
                    amountText.value = ""
                }) { Text("ثبت") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog.value = false
                    amountText.value = ""
                }) { Text("انصراف") }
            }
        )
    }
}

private fun FinancialType.label(): String = when (this) {
    FinancialType.DEBT -> "بدهی‌های من"
    FinancialType.CREDIT -> "طلب‌های من"
    FinancialType.CHECK -> "چک‌ها"
    FinancialType.INSTALLMENT -> "اقساط و وام‌ها"
    FinancialType.BILL -> "قبض‌ها"
}

private fun FinancialType.icon(): ImageVector = when (this) {
    FinancialType.DEBT -> Icons.AutoMirrored.Filled.TrendingDown
    FinancialType.CREDIT -> Icons.AutoMirrored.Filled.TrendingUp
    FinancialType.CHECK -> Icons.AutoMirrored.Filled.ReceiptLong
    FinancialType.INSTALLMENT -> Icons.Default.CalendarMonth
    FinancialType.BILL -> Icons.AutoMirrored.Filled.ReceiptLong
}
