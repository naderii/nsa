package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.ui.FinancialSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialScreen(
    reminders: List<Reminder>,
    summary: FinancialSummary,
    onRecordPayment: (Reminder, Long) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدیریت مالی") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("بازگشت") }
                }
            )
        }
    ) { padding ->
        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("هنوز هیچ یادآوری مالی‌ای ثبت نکردی.")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SummaryCard(summary) }

            val grouped = reminders.groupBy { it.financialType ?: FinancialType.DEBT }
            FinancialType.entries.forEach { type ->
                val items = grouped[type].orEmpty()
                if (items.isNotEmpty()) {
                    item(key = "header_${type.name}") {
                        Text(
                            text = type.label(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(items, key = { it.id }) { reminder ->
                        FinancialItemCard(reminder, onRecordPayment)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: FinancialSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("خلاصه وضعیت مالی", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryStat(
                    label = "جمع بدهی من",
                    value = summary.totalDebtRemaining,
                    color = Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )
                SummaryStat(
                    label = "جمع طلب من",
                    value = summary.totalCreditRemaining,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            val netColor = if (summary.net >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
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
private fun FinancialItemCard(reminder: Reminder, onRecordPayment: (Reminder, Long) -> Unit) {
    val showDialog = remember { mutableStateOf(false) }
    val formatter = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    val remaining = (reminder.amount ?: 0) - reminder.amountPaid

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(reminder.title, style = MaterialTheme.typography.titleMedium)
            reminder.counterparty?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "سررسید: ${formatter.format(Date(reminder.triggerAtMillis))}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (reminder.isDone) "تسویه شده ✓" else "باقی‌مانده: $remaining تومان از ${reminder.amount} تومان",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            if (!reminder.isDone) {
                TextButton(
                    onClick = { showDialog.value = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("ثبت پرداخت")
                }
            }
        }
    }

    if (showDialog.value) {
        val amountText = remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog.value = false },
            title = { Text("ثبت پرداخت") },
            text = {
                OutlinedTextField(
                    value = amountText.value,
                    onValueChange = { amountText.value = it.filter { c -> c.isDigit() } },
                    label = { Text("مبلغ (تومان)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    amountText.value.toLongOrNull()?.let { onRecordPayment(reminder, it) }
                    showDialog.value = false
                }) { Text("ثبت") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog.value = false }) { Text("انصراف") }
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
