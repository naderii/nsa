package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.notification.FinanceDigest
import ir.naderinia.nsa.util.FinanceAnalysis
import ir.naderinia.nsa.util.FinanceInsight
import ir.naderinia.nsa.util.InsightLevel
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.util.LoanGroup
import ir.naderinia.nsa.util.MonthForecast
import ir.naderinia.nsa.util.MonthPlan
import ir.naderinia.nsa.util.compactToman
import ir.naderinia.nsa.util.dayMonthLabel
import ir.naderinia.nsa.util.grouped
import java.util.Locale

private fun typeShort(type: FinancialType): String = when (type) {
    FinancialType.INSTALLMENT -> "اقساط"
    FinancialType.CHECK -> "چک‌ها"
    FinancialType.BILL -> "قبض‌ها"
    FinancialType.DEBT -> "بدهی‌ها"
    FinancialType.CREDIT -> "طلب‌ها"
}

private fun fa(n: Int): String = JalaliCalendar.toPersianDigits(n.toString())

// ───────────────────────────── this month ─────────────────────────────

/**
 * «این ماه چقدر قسط، چک، قبض و بدهی دارم؟» — one card that answers it: what is still to pay,
 * what is already paid, split by type, plus overdue carry-over and the next 7 days.
 */
@Composable
fun MonthFinanceCard(
    plan: MonthPlan,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) modifier.fillMaxWidth().clickable(onClick = onClick) else modifier.fillMaxWidth()
    Card(
        modifier = cardModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                "برنامه‌ی مالی ${plan.label}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (plan.monthTotal == 0L && plan.carryOverdue == 0L) {
                Text(
                    "برای این ماه پرداخت یا سررسیدی ثبت نشده.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Text(
                    if (plan.mustPayNow > 0) "هنوز باید پرداخت کنی" else "همه‌چیز پرداخت شده",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "${plan.mustPayNow.grouped()} تومان",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (plan.carryOverdue > 0) {
                    Text(
                        "شامل ${plan.carryOverdue.grouped()} تومان عقب‌افتاده از ماه‌های قبل (${fa(plan.carryOverdueCount)} مورد)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (plan.monthTotal > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { plan.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "پرداخت‌شده ${plan.paid.grouped()} از ${plan.monthTotal.grouped()} تومان",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                val visible = plan.loads.filter { it.total > 0 }
                if (visible.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    visible.forEach { load ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                typeShort(load.type),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.width(64.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (load.remaining > 0)
                                        "مانده ${load.remaining.grouped()} از ${load.total.grouped()}"
                                    else "کامل پرداخت شد (${load.paid.grouped()})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                LinearProgressIndicator(
                                    progress = { if (load.total > 0) load.paid.toFloat() / load.total.toFloat() else 0f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                            }
                            if (load.remainingCount > 0) {
                                Text(
                                    "  ${fa(load.remainingCount)} مورد",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }

            if (plan.next7Count > 0 || plan.next7Incoming > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val parts = ArrayList<String>()
                if (plan.next7Count > 0) parts += "${fa(plan.next7Count)} پرداخت (${plan.next7Amount.compactToman()})"
                if (plan.next7Incoming > 0) parts += "دریافتی ${plan.next7Incoming.compactToman()}"
                Text(
                    "۷ روز آینده: " + parts.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (plan.incomingExpected > 0 || plan.incomingReceived > 0) {
                Text(
                    "طلب این ماه: دریافت‌شده ${plan.incomingReceived.grouped()}، در انتظار ${plan.incomingExpected.grouped()} تومان",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            }
        }
    }
}

// ───────────────────────────── insights ─────────────────────────────

@Composable
fun InsightCard(insight: FinanceInsight, onNudge: ((Long) -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    val (container, content, mark) = when (insight.level) {
        InsightLevel.CRITICAL -> Triple(scheme.errorContainer, scheme.onErrorContainer, "⛔")
        InsightLevel.WARNING -> Triple(scheme.tertiaryContainer, scheme.onTertiaryContainer, "⚠️")
        InsightLevel.INFO -> Triple(scheme.surfaceVariant, scheme.onSurfaceVariant, "💡")
        InsightLevel.GOOD -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, "✅")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Text(mark, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    insight.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = content
                )
                if (insight.detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(insight.detail, style = MaterialTheme.typography.bodySmall, color = content)
                }
                if (insight.nudgeReminderId != null && onNudge != null) {
                    TextButton(onClick = { onNudge(insight.nudgeReminderId) }) { Text("ارسال پیامک یادآوری") }
                }
            }
        }
    }
}

/** A short stack of insight cards ("دستیار می‌گه…"). Renders nothing when there is nothing to say. */
@Composable
fun InsightsList(
    insights: List<FinanceInsight>,
    modifier: Modifier = Modifier,
    onNudge: ((Long) -> Unit)? = null
) {
    if (insights.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        insights.forEach { InsightCard(it, onNudge) }
    }
}

// ───────────────────────────── loans ─────────────────────────────

@Composable
fun LoanGroupCard(group: LoanGroup) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        JalaliCalendar.toPersianDigits(group.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val who = listOfNotNull(group.bank, group.counterparty).joinToString(" • ")
                    if (who.isNotBlank()) {
                        Text(who, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (group.overdueCount > 0) {
                    Text(
                        "${fa(group.overdueCount)} قسط عقب‌افتاده",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (group.totalCount > 0) group.paidCount.toFloat() / group.totalCount.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "${fa(group.paidCount)} قسط از ${fa(group.totalCount)} پرداخت شده • ${fa(group.remainingCount)} قسط مانده",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "جمع مانده: ${group.remainingAmount.grouped()} تومان",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            val lines = ArrayList<String>()
            group.nextDueAt?.let {
                lines += "قسط بعدی: ${dayMonthLabel(it)}" +
                    if (group.eachAmount > 0) " (${group.eachAmount.grouped()} تومان)" else ""
            }
            if (group.remainingCount > 1) group.lastDueAt?.let { lines += "آخرین قسط: ${JalaliCalendar.formatDate(it)}" }
            lines.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ───────────────────────────── morning digest setting ─────────────────────────────

@Composable
fun DigestSettingsCard() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(FinanceDigest.isEnabled(context)) }
    var hour by remember { mutableStateOf(FinanceDigest.hour(context)) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("خلاصه‌ی مالی صبحگاهی", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "هر روز عقب‌افتاده‌ها و سررسیدهای امروز و فردا رو بهت می‌گه؛ روزی که چیزی نباشه ساکت می‌مونه.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        FinanceDigest.configure(context, it, hour)
                    }
                )
            }
            if (enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(7, 8, 9, 12, 18)) { h ->
                        FilterChip(
                            selected = hour == h,
                            onClick = {
                                hour = h
                                FinanceDigest.configure(context, true, h)
                            },
                            label = { Text(JalaliCalendar.toPersianDigits("$h:00")) }
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── forecast screen ─────────────────────────────

private fun millionsLabel(v: Long): String {
    if (v <= 0) return "—"
    if (v < 100_000) return "<۰٫۱م"
    val s = String.format(Locale.US, "%.1f", v / 1_000_000.0).removeSuffix(".0").replace('.', '٫')
    return JalaliCalendar.toPersianDigits(s) + "م"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceForecastScreen(
    analysis: FinanceAnalysis,
    onBack: () -> Unit
) {
    var expanded by remember { mutableStateOf(setOf(0)) }
    val forecast = analysis.forecast
    val max = forecast.maxOfOrNull { it.total }?.coerceAtLeast(1L) ?: 1L
    val sixMonths = forecast.sumOf { it.total }
    val estimatedAll = forecast.sumOf { it.estimated }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("برنامه‌ی ماه‌های آینده") },
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
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(
                            "تعهدات ۶ ماه آینده",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "${sixMonths.grouped()} تومان",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (estimatedAll > 0) {
                            Text(
                                "که ${estimatedAll.grouped()} تومانش تخمینی است (قبض‌ها و پرداخت‌های تکرارشونده)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        val carry = analysis.month.carryOverdue
                        if (carry > 0) {
                            Text(
                                "جدا از این‌ها ${carry.grouped()} تومان عقب‌افتاده از ماه‌های قبل داری.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        ForecastBars(forecast, max)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "م = میلیون تومان • ستون کم‌رنگ = تخمینی",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            forecast.forEachIndexed { index, month ->
                item(key = "month_$index") {
                    ForecastMonthCard(
                        month = month,
                        isOpen = index in expanded,
                        onToggle = { expanded = if (index in expanded) expanded - index else expanded + index }
                    )
                }
            }

            item {
                Text(
                    "مبلغ‌های «تخمینی» از میانگین پرداخت‌های ۳ ماه اخیر همون قبض محاسبه می‌شن؛ اگه سابقه‌ی کافی نباشه، مبلغ ثبت‌شده روی یادآوری ملاکه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ForecastBars(months: List<MonthForecast>, max: Long) {
    val certainColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        months.forEachIndexed { index, month ->
            val certainH = if (month.certain > 0) (90f * month.certain / max).coerceAtLeast(3f) else 0f
            val estimatedH = if (month.estimated > 0) (90f * month.estimated / max).coerceAtLeast(3f) else 0f
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    millionsLabel(month.total),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (estimatedH > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(estimatedH.dp)
                            .background(
                                certainColor.copy(alpha = 0.35f),
                                RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                    )
                }
                if (certainH > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(certainH.dp)
                            .background(
                                certainColor,
                                if (estimatedH > 0f) RoundedCornerShape(0.dp)
                                else RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                            )
                    )
                }
                if (certainH == 0f && estimatedH == 0f) {
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.dp).background(certainColor.copy(alpha = 0.2f)))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    month.label.substringBefore(' '),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ForecastMonthCard(month: MonthForecast, isOpen: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(month.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (month.items.isEmpty()) "تعهدی ثبت نشده"
                        else "${fa(month.items.size)} پرداخت" +
                            if (month.estimated > 0) " • ${month.estimated.compactToman()} تخمینی" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (month.total > 0) "${month.total.grouped()} تومان" else "—",
                    style = MaterialTheme.typography.titleSmall
                )
            }
            if (isOpen && month.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                month.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            dayMonthLabel(item.at),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                JalaliCalendar.toPersianDigits(item.title),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                typeShort(item.type) + if (item.estimated) " • تخمینی" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("${item.amount.grouped()}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
