package ir.naderinia.nsa.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.ui.components.EmptyHint
import ir.naderinia.nsa.ui.components.SectionHeader
import ir.naderinia.nsa.util.FinanceReport
import ir.naderinia.nsa.util.FinanceReportBuilder
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.util.ReportExporter
import ir.naderinia.nsa.util.grouped

private const val LIST_LIMIT = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceReportScreen(
    financialReminders: List<Reminder>,
    paymentLogs: List<PaymentLog>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var monthsAgo by rememberSaveable { mutableStateOf(0) }

    val report = remember(monthsAgo, financialReminders, paymentLogs) {
        val now = JalaliCalendar.millisToJalali(System.currentTimeMillis())
        val total = now.year * 12 + (now.month - 1) - monthsAgo
        FinanceReportBuilder.build(total / 12, total % 12 + 1, financialReminders, paymentLogs)
    }

    fun export(csv: Boolean) {
        try {
            if (csv) {
                ReportExporter.share(context, ReportExporter.exportCsv(context, report), "text/csv")
            } else {
                ReportExporter.share(context, ReportExporter.exportPdf(context, report), "application/pdf")
            }
        } catch (e: Exception) {
            Toast.makeText(context, "ساخت یا ارسال فایل انجام نشد", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("گزارش مالی") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { monthsAgo++ }) { Text("ماه قبل") }
                    Text(report.monthLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { monthsAgo-- }, enabled = monthsAgo > 0) { Text("ماه بعد") }
                }
            }

            item { SummaryCard(report) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { export(csv = true) }, modifier = Modifier.weight(1f)) {
                        Text("خروجی اکسل (CSV)")
                    }
                    OutlinedButton(onClick = { export(csv = false) }, modifier = Modifier.weight(1f)) {
                        Text("خروجی PDF")
                    }
                }
            }

            if (report.paidByType.isNotEmpty()) {
                item { SectionHeader("پرداخت‌ها به تفکیک نوع") }
                items(report.paidByType.size) { i ->
                    val (label, amount) = report.paidByType[i]
                    ShareRow(label, amount, report.totalPaid)
                }
            }

            if (report.paidByBank.isNotEmpty()) {
                item { SectionHeader("پرداخت‌ها به تفکیک بانک") }
                items(report.paidByBank.size) { i ->
                    val (bank, amount) = report.paidByBank[i]
                    ShareRow(bank, amount, report.totalPaid)
                }
            }

            item { SectionHeader("پرداخت‌های ثبت‌شده (${report.payments.size})") }
            if (report.payments.isEmpty()) {
                item { EmptyHint("در این ماه پرداختی ثبت نشده") }
            } else {
                items(report.payments.take(LIST_LIMIT).size) { i ->
                    val p = report.payments[i]
                    LineRow(
                        title = p.title,
                        subtitle = "${JalaliCalendar.formatDate(p.at)} • ${p.typeLabel}" +
                            if (p.bank.isNotBlank()) " • بانک ${p.bank}" else "",
                        trailing = "${p.amount.grouped()} تومان"
                    )
                }
                if (report.payments.size > LIST_LIMIT) {
                    item { EmptyHint("برای دیدن همه‌ی ردیف‌ها خروجی بگیر") }
                }
            }

            item { SectionHeader("سررسیدهای این ماه (${report.dues.size})") }
            if (report.dues.isEmpty()) {
                item { EmptyHint("سررسیدی در این ماه نیست") }
            } else {
                items(report.dues.take(LIST_LIMIT).size) { i ->
                    val d = report.dues[i]
                    LineRow(
                        title = d.title,
                        subtitle = "${JalaliCalendar.formatDate(d.dueAt)} • ${d.typeLabel} • ${d.status}",
                        trailing = if (d.remaining > 0) "${d.remaining.grouped()} تومان" else ""
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(report: FinanceReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "جمع پرداخت‌ها",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "${report.totalPaid.grouped()} تومان",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "باقی‌مانده‌ی سررسیدهای این ماه: ${report.remainingDue.grouped()} تومان",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ShareRow(label: String, amount: Long, total: Long) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${amount.grouped()} تومان", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) (amount.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
    }
}

@Composable
private fun LineRow(title: String, subtitle: String, trailing: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing.isNotEmpty()) {
                Text(trailing, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
