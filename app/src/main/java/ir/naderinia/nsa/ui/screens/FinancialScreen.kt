package ir.naderinia.nsa.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.CheckStatus
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.persianLabel
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.ui.FinancialSummary
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.util.grouped

/**
 * No Scaffold/TopAppBar here: this screen is embedded as a tab inside
 * HomeScreen, which owns the single shared Scaffold (with the bottom nav
 * bar). Nesting another Scaffold here would double up app bars/padding.
 */
@Composable
fun FinancialScreen(
    reminders: List<Reminder>,
    summary: FinancialSummary,
    selectedFilter: FinancialType?, // null = all
    onFilterChange: (FinancialType?) -> Unit,
    paymentLogs: List<PaymentLog>,
    onCheckStatusChange: (Reminder, CheckStatus?) -> Unit,
    onRecordPayment: (Reminder, Long) -> Unit,
    onReportClick: () -> Unit
) {

    val filtered = if (selectedFilter == null) reminders else reminders.filter { it.financialType == selectedFilter }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SummaryCard(summary) }

        item {
            OutlinedButton(
                onClick = onReportClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("گزارش ماهانه و خروجی PDF / اکسل")
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { onFilterChange(null) },
                        label = { Text("همه") }
                    )
                }
                items(FinancialType.entries.toList()) { type ->
                    FilterChip(
                        selected = selectedFilter == type,
                        onClick = { onFilterChange(type) },
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
                FinancialItemCard(
                    reminder = reminder,
                    logs = paymentLogs,
                    onRecordPayment = onRecordPayment,
                    onCheckStatusChange = onCheckStatusChange
                )
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
                text = "مانده‌ی خالص: ${if (summary.net >= 0) "+" else ""}${summary.net.grouped()} تومان",
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
        Text("${value.grouped()} تومان", style = MaterialTheme.typography.titleLarge, color = color)
    }
}

@Composable
private fun FinancialItemCard(
    reminder: Reminder,
    logs: List<PaymentLog>,
    onRecordPayment: (Reminder, Long) -> Unit,
    onCheckStatusChange: (Reminder, CheckStatus?) -> Unit
) {
    val context = LocalContext.current
    val showDialog = remember { mutableStateOf(false) }
    val amountText = remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var messageMenuExpanded by remember { mutableStateOf(false) }
    val total = reminder.amount ?: 0
    val remaining = total - reminder.amountPaid
    val progress = if (total > 0) (reminder.amountPaid.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val type = reminder.financialType ?: FinancialType.DEBT
    val myLogs = remember(logs, reminder.id) { logs.filter { it.reminderId == reminder.id } }
    val phone = reminder.counterpartyPhone

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
                val statusLabel = if (type == FinancialType.CHECK) reminder.checkStatus?.persianLabel() else null
                if (statusLabel != null) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(statusLabel) })
                } else if (reminder.isDone) {
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
                    text = "باقی‌مانده: ${remaining.toLong().grouped()} از ${total.toLong().grouped()} تومان",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Also shown for a settled cheque: a mistaken "passed" tap (or a bounce) must be fixable.
            if (type == FinancialType.CHECK) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("وضعیت چک", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CheckStatus.entries.toList()) { status ->
                        val selected = reminder.checkStatus == status
                        FilterChip(
                            selected = selected,
                            onClick = { onCheckStatusChange(reminder, if (selected) null else status) },
                            label = { Text(status.persianLabel()) }
                        )
                    }
                }
            }

            if (myLogs.isNotEmpty()) {
                TextButton(onClick = { showHistory = !showHistory }) {
                    Text(
                        if (showHistory) "بستن سابقه‌ی پرداخت‌ها"
                        else "سابقه‌ی پرداخت‌ها (${myLogs.size})"
                    )
                }
                if (showHistory) {
                    myLogs.forEach { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                JalaliCalendar.formatDate(log.timestampMillis),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${log.amount.grouped()} تومان",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (!reminder.isDone) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (phone != null) {
                        Box {
                            TextButton(onClick = { messageMenuExpanded = true }) {
                                Text("پیام به طرف حساب")
                            }
                            DropdownMenu(
                                expanded = messageMenuExpanded,
                                onDismissRequest = { messageMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("پیامک") },
                                    onClick = {
                                        messageMenuExpanded = false
                                        sendMessage(context, phone, buildMessage(reminder, remaining.toLong()), false)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("واتساپ") },
                                    onClick = {
                                        messageMenuExpanded = false
                                        sendMessage(context, phone, buildMessage(reminder, remaining.toLong()), true)
                                    }
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            amountText.value = ""
                            showDialog.value = true
                        }
                    ) {
                        Text("ثبت پرداخت")
                    }
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

/** A short, polite default text — the person can still edit it in the SMS/WhatsApp app. */
private fun buildMessage(reminder: Reminder, remaining: Long): String {
    val name = reminder.counterparty?.takeIf { it.isNotBlank() }
    val greeting = if (name == null) "سلام،" else "سلام $name عزیز،"
    val amountText = "${remaining.grouped()} تومان"
    val date = JalaliCalendar.formatDate(reminder.triggerAtMillis)
    return when (reminder.financialType) {
        FinancialType.CREDIT ->
            "$greeting یادآوری دوستانه: مبلغ $amountText بابت «${reminder.title}» تاریخ $date سررسید می‌شه."
        FinancialType.DEBT ->
            "$greeting بابت «${reminder.title}» مبلغ $amountText رو تاریخ $date پرداخت می‌کنم."
        else ->
            "$greeting یادآوری «${reminder.title}» به مبلغ $amountText، تاریخ $date."
    }
}

/** Opens the SMS app, or WhatsApp, with the text ready — no permission needed since the person presses send. */
private fun sendMessage(context: Context, phone: String, text: String, whatsapp: Boolean) {
    val digits = phone.filter { it.isDigit() || it == '+' }
    val intent = if (whatsapp) {
        val noPlus = digits.removePrefix("+")
        val international = when {
            noPlus.startsWith("00") -> noPlus.removePrefix("00")
            noPlus.startsWith("0") -> "98" + noPlus.removePrefix("0")
            else -> noPlus
        }
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$international?text=${Uri.encode(text)}"))
    } else {
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$digits")).putExtra("sms_body", text)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "برنامه‌ی مناسبی برای ارسال پیام پیدا نشد", Toast.LENGTH_SHORT).show()
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
