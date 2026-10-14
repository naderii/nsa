package ir.naderinia.nsa.util

import ir.naderinia.nsa.data.CheckStatus
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.persianLabel

fun FinancialType.reportLabel(): String = when (this) {
    FinancialType.DEBT -> "بدهی"
    FinancialType.CREDIT -> "طلب"
    FinancialType.CHECK -> "چک"
    FinancialType.INSTALLMENT -> "قسط/وام"
    FinancialType.BILL -> "قبض"
}

data class ReportPayment(
    val at: Long,
    val title: String,
    val typeLabel: String,
    val counterparty: String,
    val bank: String,
    val amount: Long
)

data class ReportDue(
    val dueAt: Long,
    val title: String,
    val typeLabel: String,
    val amount: Long,
    val paid: Long,
    val remaining: Long,
    val status: String
)

data class FinanceReport(
    val year: Int,
    val month: Int,
    val monthLabel: String,
    val payments: List<ReportPayment>,
    val dues: List<ReportDue>,
    val totalPaid: Long,
    val paidByType: List<Pair<String, Long>>,
    val paidByBank: List<Pair<String, Long>>,
    val remainingDue: Long
)

object FinanceReportBuilder {

    /** Report for one Jalali month: what was paid in it, and what falls due in it. */
    fun build(
        year: Int,
        month: Int,
        financialReminders: List<Reminder>,
        logs: List<PaymentLog>
    ): FinanceReport {
        val start = JalaliCalendar.jalaliToMillis(year, month, 1, 0, 0)
        val nextTotal = year * 12 + (month - 1) + 1
        val end = JalaliCalendar.jalaliToMillis(nextTotal / 12, nextTotal % 12 + 1, 1, 0, 0)
        val byId = financialReminders.associateBy { it.id }
        val now = System.currentTimeMillis()

        val payments = logs
            .filter { it.timestampMillis in start until end && byId.containsKey(it.reminderId) }
            .sortedBy { it.timestampMillis }
            .map { log ->
                val reminder = byId[log.reminderId]
                ReportPayment(
                    at = log.timestampMillis,
                    title = reminder?.title ?: "—",
                    typeLabel = reminder?.financialType?.reportLabel() ?: "نامشخص",
                    counterparty = reminder?.counterparty.orEmpty(),
                    bank = reminder?.bankName.orEmpty(),
                    amount = log.amount
                )
            }

        val dues = financialReminders
            .filter { it.triggerAtMillis in start until end }
            .sortedBy { it.triggerAtMillis }
            .map { r ->
                val total = r.amount ?: 0L
                ReportDue(
                    dueAt = r.triggerAtMillis,
                    title = r.title,
                    typeLabel = r.financialType?.reportLabel() ?: "",
                    amount = total,
                    paid = r.amountPaid,
                    remaining = if (r.isDone) 0L else (total - r.amountPaid).coerceAtLeast(0L),
                    status = when {
                        r.checkStatus != null -> r.checkStatus.persianLabel()
                        r.isDone -> "تسویه شده"
                        r.triggerAtMillis < now -> "عقب‌افتاده"
                        else -> "در انتظار"
                    }
                )
            }

        return FinanceReport(
            year = year,
            month = month,
            monthLabel = JalaliCalendar.toPersianDigits("${JalaliCalendar.monthNames[month - 1]} $year"),
            payments = payments,
            dues = dues,
            // «طلب» payments are money received, not money paid out.
            totalPaid = payments.filter { it.typeLabel != FinancialType.CREDIT.reportLabel() }.sumOf { it.amount },
            paidByType = payments
                .filter { it.typeLabel != FinancialType.CREDIT.reportLabel() }
                .groupBy { it.typeLabel }
                .map { (label, items) -> label to items.sumOf { it.amount } }
                .sortedByDescending { it.second },
            paidByBank = payments
                .filter { it.bank.isNotBlank() && it.typeLabel != FinancialType.CREDIT.reportLabel() }
                .groupBy { it.bank }
                .map { (bank, items) -> bank to items.sumOf { it.amount } }
                .sortedByDescending { it.second },
            remainingDue = dues.filter { it.typeLabel != FinancialType.CREDIT.reportLabel() }.sumOf { it.remaining }
        )
    }
}
