package ir.naderinia.nsa.util

import ir.naderinia.nsa.data.CheckStatus
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.data.effectiveAnchorDay
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/**
 * The "brain" of the finance section. Pure Kotlin (no Android classes) so it can be unit-tested:
 * give it every financial reminder + every payment log + "now" and it answers
 *   - how much is due / paid / left THIS Jalali month, per type (installment, cheque, bill, debt)
 *   - what is overdue, what is due in the next 7 days
 *   - what the next months will cost (real items + estimated repeating bills)
 *   - one card per loan («قسط ۵ از ۱۲»)
 *   - a short list of smart, human-readable insights
 *
 * Nothing here touches the database schema: it is computed from the data the app already has.
 */

// ───────────────────────────── result types ─────────────────────────────

enum class InsightLevel { CRITICAL, WARNING, INFO, GOOD }

data class FinanceInsight(
    val level: InsightLevel,
    val title: String,
    val detail: String,
    /** Reminders this insight is about (so the UI can jump to / act on them). */
    val reminderIds: List<Long> = emptyList(),
    /** A credit (money owed TO the person) they can nudge with one tap. */
    val nudgeReminderId: Long? = null
)

/** One outgoing type's share of the month: [paid] already left the pocket, [remaining] still has to. */
data class TypeLoad(
    val type: FinancialType,
    val paid: Long,
    val remaining: Long,
    val remainingCount: Int
) {
    val total: Long get() = paid + remaining
}

data class MonthPlan(
    val year: Int,
    val month: Int,
    val label: String,
    /** Always in the order installment, cheque, bill, debt (even when zero). */
    val loads: List<TypeLoad>,
    val paid: Long,
    /** Still to pay with a due date inside this month (includes the days of it that already passed). */
    val remaining: Long,
    /** Unpaid items from EARLIER months. */
    val carryOverdue: Long,
    val carryOverdueCount: Int,
    val overdueAmount: Long,
    val overdueCount: Int,
    val incomingExpected: Long,
    val incomingReceived: Long,
    val next7Amount: Long,
    val next7Count: Int,
    val next7Incoming: Long
) {
    /** What the person still has to put on the table right now: this month's rest + old overdue. */
    val mustPayNow: Long get() = remaining + carryOverdue
    val monthTotal: Long get() = paid + remaining
    val progress: Float
        get() = if (monthTotal > 0) (paid.toFloat() / monthTotal.toFloat()).coerceIn(0f, 1f) else 0f
}

data class ForecastItem(
    val at: Long,
    val reminderId: Long,
    val title: String,
    val type: FinancialType,
    val amount: Long,
    /** true = a repeating bill/payment projected into the future (not a real row yet). */
    val estimated: Boolean
)

data class MonthForecast(
    val year: Int,
    val month: Int,
    val label: String,
    val certain: Long,
    val estimated: Long,
    val items: List<ForecastItem>
) {
    val total: Long get() = certain + estimated
}

data class LoanGroup(
    val key: String,
    val name: String,
    val counterparty: String?,
    val bank: String?,
    val totalCount: Int,
    val paidCount: Int,
    val remainingCount: Int,
    val eachAmount: Long,
    val paidAmount: Long,
    val remainingAmount: Long,
    val nextDueAt: Long?,
    val lastDueAt: Long?,
    val overdueCount: Int,
    val reminderIds: List<Long>
)

data class DueLine(val reminderId: Long, val title: String, val type: FinancialType, val amount: Long, val at: Long)

data class FinanceAnalysis(
    val month: MonthPlan,
    val loans: List<LoanGroup>,
    val forecast: List<MonthForecast>,
    val insights: List<FinanceInsight>,
    val overdue: List<DueLine>,
    val today: List<DueLine>,
    val tomorrow: List<DueLine>
) {
    companion object {
        val EMPTY: FinanceAnalysis = FinanceInsights.analyze(emptyList(), emptyList(), System.currentTimeMillis())
    }
}

// ───────────────────────────── formatting helpers ─────────────────────────────

/** 12_500_000 → «۱۲٫۵ میلیون تومان», 450_000 → «۴۵۰,۰۰۰ تومان» (short enough for a notification). */
fun Long.compactToman(): String {
    val a = abs(this)
    val sign = if (this < 0) "−" else ""
    val body = when {
        a >= 1_000_000_000L -> decimal(a / 1_000_000_000.0) + " میلیارد"
        a >= 1_000_000L -> decimal(a / 1_000_000.0) + " میلیون"
        else -> a.grouped()
    }
    return JalaliCalendar.toPersianDigits("$sign$body تومان")
}

private fun decimal(v: Double): String {
    val s = String.format(Locale.US, "%.1f", v).removeSuffix(".0")
    return s.replace('.', '٫')
}

private fun fa(n: Int): String = JalaliCalendar.toPersianDigits(n.toString())

/** «عنوان» with Persian digits, ready to embed in a sentence. */
private fun q(title: String): String = "«" + JalaliCalendar.toPersianDigits(title) + "»"

/** «۱۲ آبان» */
fun dayMonthLabel(millis: Long): String {
    val d = JalaliCalendar.millisToJalali(millis)
    return JalaliCalendar.toPersianDigits("${d.day} ${JalaliCalendar.monthNames[d.month - 1]}")
}

// ───────────────────────────── the engine ─────────────────────────────

object FinanceInsights {

    private const val DAY = 24L * 60 * 60 * 1000
    private const val FORECAST_MONTHS = 6
    private val OUTGOING = setOf(
        FinancialType.INSTALLMENT, FinancialType.CHECK, FinancialType.BILL, FinancialType.DEBT
    )
    private val LOAN_TITLE = Regex("^(.*?)\\s*[—–-]\\s*قسط\\s*([0-9۰-۹٠-٩]+)\\s*از\\s*([0-9۰-۹٠-٩]+)\\s*$")

    private fun isOutgoing(r: Reminder) = r.financialType != null && r.financialType in OUTGOING
    private fun remainingOf(r: Reminder): Long = ((r.amount ?: 0L) - r.amountPaid).coerceAtLeast(0L)
    private fun recurs(r: Reminder) = r.repeatInterval != RepeatInterval.NONE

    fun startOfDay(millis: Long): Long =
        Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun addDays(millis: Long, days: Int): Long =
        Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.DAY_OF_YEAR, days) }.timeInMillis

    private fun daysFromToday(now: Long, at: Long): Int {
        val a = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
        val b = Calendar.getInstance().apply { timeInMillis = startOfDay(at) }
        val diff = (b.timeInMillis - a.timeInMillis) / DAY.toDouble()
        return Math.round(diff).toInt()
    }

    private fun monthStart(year: Int, month: Int): Long = JalaliCalendar.jalaliToMillis(year, month, 1, 0, 0)

    private fun monthIndexOf(millis: Long): Int {
        val j = JalaliCalendar.millisToJalali(millis)
        return j.year * 12 + (j.month - 1)
    }

    private fun startOfMonthIndex(index: Int): Long = monthStart(index / 12, index % 12 + 1)

    // ───────────── repeating items: step + typical amount ─────────────

    private fun step(r: Reminder, from: Long): Long? = when (r.repeatInterval) {
        RepeatInterval.MONTHLY -> JalaliCalendar.addMonths(from, 1, r.effectiveAnchorDay())
        RepeatInterval.YEARLY -> JalaliCalendar.addMonths(from, 12, r.effectiveAnchorDay())
        RepeatInterval.WEEKLY -> addDays(from, 7)
        RepeatInterval.DAILY -> addDays(from, 1)
        RepeatInterval.NONE -> null
    }

    /**
     * What a repeating payment usually costs, learned from the person's own payments: the average
     * of the last (up to) three months that had payments. Null when there is not enough history
     * (fewer than two months) — then the amount typed on the reminder is the best guess.
     */
    fun typicalAmount(reminderId: Long, logs: List<PaymentLog>): Long? {
        val perMonth = logs
            .filter { it.reminderId == reminderId && it.amount > 0 }
            .groupBy { monthIndexOf(it.timestampMillis) }
            .toSortedMap(compareByDescending<Int> { it })
            .values
            .take(3)
            .map { month -> month.sumOf { it.amount } }
        if (perMonth.size < 2) return null
        return perMonth.sum() / perMonth.size
    }

    /**
     * Every outgoing payment that falls in [from, to): real, unpaid rows with their remaining amount,
     * plus the future cycles of repeating ones (marked estimated, amount learned from history).
     */
    fun projectItems(reminders: List<Reminder>, logs: List<PaymentLog>, from: Long, to: Long): List<ForecastItem> {
        val out = ArrayList<ForecastItem>()
        for (r in reminders) {
            if (!isOutgoing(r) || r.isDone) continue
            val type = r.financialType ?: continue
            val rest = remainingOf(r)

            if (r.triggerAtMillis in from until to && rest > 0) {
                out += ForecastItem(r.triggerAtMillis, r.id, r.title, type, rest, estimated = false)
            }

            if (recurs(r)) {
                val estimate = typicalAmount(r.id, logs) ?: (r.amount ?: 0L)
                if (estimate <= 0) continue
                var at = r.triggerAtMillis
                var guard = 0
                while (guard < 400) {
                    at = step(r, at) ?: break
                    guard++
                    if (at >= to) break
                    if (at >= from) out += ForecastItem(at, r.id, r.title, type, estimate, estimated = true)
                }
            }
        }
        return out.sortedBy { it.at }
    }

    // ───────────── loans ─────────────

    fun loanGroups(reminders: List<Reminder>, now: Long): List<LoanGroup> {
        val installments = reminders.filter { it.financialType == FinancialType.INSTALLMENT }
        val grouped = LinkedHashMap<String, MutableList<Reminder>>()
        for (r in installments) {
            val m = LOAN_TITLE.matchEntire(r.title)
            val key = if (m != null) {
                "${m.groupValues[1].trim()}|${r.counterparty.orEmpty()}|${r.bankName.orEmpty()}|${toAsciiDigits(m.groupValues[3])}"
            } else "single|${r.id}"
            grouped.getOrPut(key) { ArrayList() } += r
        }
        return grouped.map { (key, items) ->
            val first = items.first()
            val m = LOAN_TITLE.matchEntire(first.title)
            val undone = items.filter { !it.isDone }
            val amounts = items.mapNotNull { it.amount }.filter { it > 0 }
            // the most common instalment amount (edits of single instalments can differ)
            val each = amounts.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 0L
            LoanGroup(
                key = key,
                name = m?.groupValues?.get(1)?.trim()?.ifBlank { first.title } ?: first.title,
                counterparty = first.counterparty?.takeIf { it.isNotBlank() },
                bank = first.bankName?.takeIf { it.isNotBlank() },
                totalCount = items.size,
                paidCount = items.count { it.isDone },
                remainingCount = undone.size,
                eachAmount = each,
                paidAmount = items.sumOf { it.amountPaid },
                remainingAmount = undone.sumOf { remainingOf(it) },
                nextDueAt = undone.minOfOrNull { it.triggerAtMillis },
                lastDueAt = items.maxOfOrNull { it.triggerAtMillis },
                overdueCount = undone.count { it.triggerAtMillis < now },
                reminderIds = items.map { it.id }
            )
        }.sortedWith(
            compareBy<LoanGroup> { it.remainingCount == 0 }
                .thenBy { it.nextDueAt ?: Long.MAX_VALUE }
        )
    }

    private fun toAsciiDigits(s: String): String = s.map {
        when (it) {
            in '۰'..'۹' -> '0' + (it - '۰')
            in '٠'..'٩' -> '0' + (it - '٠')
            else -> it
        }
    }.joinToString("")

    // ───────────── the month ─────────────

    fun monthPlan(
        reminders: List<Reminder>,
        logs: List<PaymentLog>,
        now: Long,
        year: Int,
        month: Int
    ): MonthPlan {
        val start = monthStart(year, month)
        val nextIdx = year * 12 + (month - 1) + 1
        val end = startOfMonthIndex(nextIdx)
        val byId = reminders.filter { it.financialType != null }.associateBy { it.id }

        val loads = listOf(
            FinancialType.INSTALLMENT, FinancialType.CHECK, FinancialType.BILL, FinancialType.DEBT
        ).map { type ->
            val paid = logs
                .filter { it.timestampMillis in start until end && byId[it.reminderId]?.financialType == type }
                .sumOf { it.amount }
            val open = reminders.filter {
                it.financialType == type && !it.isDone && it.triggerAtMillis in start until end
            }
            TypeLoad(type, paid, open.sumOf { remainingOf(it) }, open.count { remainingOf(it) > 0 })
        }

        val outgoingOpen = reminders.filter { isOutgoing(it) && !it.isDone }
        val overdue = outgoingOpen.filter { it.triggerAtMillis < now && remainingOf(it) > 0 }
        val carry = overdue.filter { it.triggerAtMillis < start }

        val incoming = reminders.filter { it.financialType == FinancialType.CREDIT }
        val in7 = incoming.filter { !it.isDone && it.triggerAtMillis in now..(now + 7 * DAY) }
        val next7 = outgoingOpen.filter { it.triggerAtMillis in now..(now + 7 * DAY) && remainingOf(it) > 0 }

        return MonthPlan(
            year = year,
            month = month,
            label = JalaliCalendar.toPersianDigits("${JalaliCalendar.monthNames[month - 1]} $year"),
            loads = loads,
            paid = loads.sumOf { it.paid },
            remaining = loads.sumOf { it.remaining },
            carryOverdue = carry.sumOf { remainingOf(it) },
            carryOverdueCount = carry.size,
            overdueAmount = overdue.sumOf { remainingOf(it) },
            overdueCount = overdue.size,
            incomingExpected = incoming
                .filter { !it.isDone && it.triggerAtMillis in start until end }
                .sumOf { remainingOf(it) },
            incomingReceived = logs
                .filter { it.timestampMillis in start until end && byId[it.reminderId]?.financialType == FinancialType.CREDIT }
                .sumOf { it.amount },
            next7Amount = next7.sumOf { remainingOf(it) },
            next7Count = next7.size,
            next7Incoming = in7.sumOf { remainingOf(it) }
        )
    }

    // ───────────── everything ─────────────

    fun analyze(reminders: List<Reminder>, logs: List<PaymentLog>, now: Long): FinanceAnalysis {
        val fin = reminders.filter { it.financialType != null }
        val nowJ = JalaliCalendar.millisToJalali(now)
        val plan = monthPlan(fin, logs, now, nowJ.year, nowJ.month)

        val curIdx = nowJ.year * 12 + (nowJ.month - 1)
        val horizonStart = startOfMonthIndex(curIdx)
        val horizonEnd = startOfMonthIndex(curIdx + FORECAST_MONTHS)
        val projected = projectItems(fin, logs, horizonStart, horizonEnd)
        val forecast = (0 until FORECAST_MONTHS).map { i ->
            val idx = curIdx + i
            val s = startOfMonthIndex(idx)
            val e = startOfMonthIndex(idx + 1)
            val items = projected.filter { it.at in s until e }
            MonthForecast(
                year = idx / 12,
                month = idx % 12 + 1,
                label = JalaliCalendar.toPersianDigits("${JalaliCalendar.monthNames[idx % 12]} ${idx / 12}"),
                certain = items.filter { !it.estimated }.sumOf { it.amount },
                estimated = items.filter { it.estimated }.sumOf { it.amount },
                items = items
            )
        }

        val loans = loanGroups(fin, now)

        val openOut = fin.filter { isOutgoing(it) && !it.isDone && remainingOf(it) > 0 }
        fun line(r: Reminder) = DueLine(r.id, r.title, r.financialType!!, remainingOf(r), r.triggerAtMillis)
        val overdueLines = openOut.filter { it.triggerAtMillis < now }.sortedBy { it.triggerAtMillis }.map(::line)
        val todayStart = startOfDay(now)
        val todayLines = openOut
            .filter { it.triggerAtMillis >= now && it.triggerAtMillis < todayStart + DAY }
            .sortedBy { it.triggerAtMillis }.map(::line)
        val tomorrowLines = openOut
            .filter { it.triggerAtMillis >= todayStart + DAY && it.triggerAtMillis < todayStart + 2 * DAY }
            .sortedBy { it.triggerAtMillis }.map(::line)

        val insights = buildInsights(fin, logs, now, plan, forecast, loans, overdueLines, todayLines, tomorrowLines)

        return FinanceAnalysis(plan, loans, forecast, insights, overdueLines, todayLines, tomorrowLines)
    }

    // ───────────── smart insights ─────────────

    private fun buildInsights(
        fin: List<Reminder>,
        logs: List<PaymentLog>,
        now: Long,
        plan: MonthPlan,
        forecast: List<MonthForecast>,
        loans: List<LoanGroup>,
        overdue: List<DueLine>,
        today: List<DueLine>,
        tomorrow: List<DueLine>
    ): List<FinanceInsight> {
        val list = ArrayList<FinanceInsight>()

        // 1) bounced cheques nobody dealt with
        val bounced = fin.filter { it.financialType == FinancialType.CHECK && it.checkStatus == CheckStatus.BOUNCED && !it.isDone }
        if (bounced.isNotEmpty()) {
            val sum = bounced.sumOf { remainingOf(it) }
            list += FinanceInsight(
                InsightLevel.CRITICAL,
                "${fa(bounced.size)} چک برگشتی رسیدگی نشده",
                "جمعاً ${sum.compactToman()}. تا وقتی تکلیفش روشن نشه، مانده‌ی مالی واقعی نیست.",
                bounced.map { it.id }
            )
        }

        // 2) overdue payments (cheques that bounced are already covered above)
        val bouncedIds = bounced.map { it.id }.toSet()
        val overdueRest = overdue.filter { it.reminderId !in bouncedIds }
        if (overdueRest.isNotEmpty()) {
            val oldest = overdueRest.first()
            val days = -daysFromToday(now, oldest.at)
            val sum = overdueRest.sumOf { it.amount }
            list += FinanceInsight(
                InsightLevel.CRITICAL,
                "${fa(overdueRest.size)} پرداخت عقب‌افتاده، جمعاً ${sum.compactToman()}",
                "قدیمی‌ترین: ${q(oldest.title)} (${fa(days.coerceAtLeast(1))} روز پیش). هرچه دیرتر، دردسر و جریمه‌ی بیشتر.",
                overdueRest.map { it.reminderId }
            )
        }

        // 3) today / tomorrow — cheques get their own, sharper wording
        val todayCheques = today.filter { it.type == FinancialType.CHECK }
        val tomorrowCheques = tomorrow.filter { it.type == FinancialType.CHECK }
        if (todayCheques.isNotEmpty()) {
            list += FinanceInsight(
                InsightLevel.WARNING,
                "امروز ${fa(todayCheques.size)} چک سررسید داری (${todayCheques.sumOf { it.amount }.compactToman()})",
                "مطمئن شو موجودی حساب کافیه.",
                todayCheques.map { it.reminderId }
            )
        }
        if (tomorrowCheques.isNotEmpty()) {
            list += FinanceInsight(
                InsightLevel.WARNING,
                "فردا ${fa(tomorrowCheques.size)} چک سررسید می‌شه (${tomorrowCheques.sumOf { it.amount }.compactToman()})",
                "امروز موجودی حساب رو چک کن تا فردا غافلگیر نشی.",
                tomorrowCheques.map { it.reminderId }
            )
        }
        val todayOther = today.filter { it.type != FinancialType.CHECK }
        if (todayOther.isNotEmpty()) {
            list += FinanceInsight(
                InsightLevel.WARNING,
                "امروز ${todayOther.sumOf { it.amount }.compactToman()} پرداخت داری",
                todayOther.take(3).joinToString("، ") { q(it.title) },
                todayOther.map { it.reminderId }
            )
        }

        // 4) the heaviest 7-day stretch of the next 30 days
        val dayStart = startOfDay(now)
        val window30 = projectItems(fin, logs, dayStart, dayStart + 30 * DAY)
        val total30 = window30.sumOf { it.amount }
        if (window30.size >= 3 && total30 > 0) {
            var bestStart = dayStart
            var bestSum = 0L
            var bestCount = 0
            for (d in 0..23) {
                val s = dayStart + d * DAY
                val inside = window30.filter { it.at >= s && it.at < s + 7 * DAY }
                val sum = inside.sumOf { it.amount }
                if (sum > bestSum) { bestSum = sum; bestStart = s; bestCount = inside.size }
            }
            if (bestCount >= 3 && bestSum * 100 >= total30 * 55) {
                list += FinanceInsight(
                    InsightLevel.WARNING,
                    "هفته‌ی سنگین: ${dayMonthLabel(bestStart)} تا ${dayMonthLabel(bestStart + 6 * DAY)}",
                    "${fa(bestCount)} پرداخت با جمع ${bestSum.compactToman()}، یعنی ${fa((bestSum * 100 / total30).toInt())}٪ از ۳۰ روز آینده توی همین هفته جمع شده."
                )
            }
        }

        // 5) this month vs last month, next month vs this month
        val nowJ = JalaliCalendar.millisToJalali(now)
        val lastIdx = nowJ.year * 12 + (nowJ.month - 1) - 1
        val lastStart = startOfMonthIndex(lastIdx)
        val thisStart = startOfMonthIndex(lastIdx + 1)
        val byId = fin.associateBy { it.id }
        val lastMonthPaid = logs
            .filter { it.timestampMillis in lastStart until thisStart }
            .filter { val t = byId[it.reminderId]?.financialType; t != null && t in OUTGOING }
            .sumOf { it.amount }
        val thisTotal = plan.monthTotal
        if (lastMonthPaid > 0 && thisTotal > lastMonthPaid * 125 / 100 && thisTotal - lastMonthPaid >= 500_000) {
            val ratio = thisTotal.toDouble() / lastMonthPaid.toDouble()
            val phrase = if (ratio >= 2.0) "حدود ${fa(Math.round(ratio).toInt())} برابر ماه قبله"
            else "${fa(((ratio - 1) * 100).toInt())}٪ از ماه قبل بیشتره"
            list += FinanceInsight(
                InsightLevel.INFO,
                "تعهدات این ماه $phrase",
                "این ماه ${thisTotal.compactToman()}، ماه قبل ${lastMonthPaid.compactToman()} پرداخت شده."
            )
        }
        val nextMonth = forecast.getOrNull(1)
        val thisForecast = forecast.firstOrNull()
        if (nextMonth != null && thisForecast != null &&
            nextMonth.total >= 1_000_000 &&
            nextMonth.total > maxOf(thisForecast.total, thisTotal) * 130 / 100
        ) {
            list += FinanceInsight(
                InsightLevel.WARNING,
                "ماه آینده (${nextMonth.label.substringBefore(' ')}) سنگین‌تره",
                "حدود ${nextMonth.total.compactToman()} تعهد داری؛ از همین حالا کنار بذار."
            )
        }

        // 6) people who owe the person money and are late
        fin.filter { it.financialType == FinancialType.CREDIT && !it.isDone && remainingOf(it) > 0 && it.triggerAtMillis < now }
            .sortedBy { it.triggerAtMillis }
            .take(2)
            .forEach { c ->
                val days = (-daysFromToday(now, c.triggerAtMillis)).coerceAtLeast(1)
                val who = c.counterparty?.takeIf { it.isNotBlank() } ?: "طرف حساب"
                list += FinanceInsight(
                    InsightLevel.INFO,
                    "$who ${remainingOf(c).compactToman()} بهت بدهکاره",
                    "${fa(days)} روز از سررسید گذشته؛ یه یادآوری دوستانه بفرست.",
                    listOf(c.id),
                    nudgeReminderId = if (!c.counterpartyPhone.isNullOrBlank()) c.id else null
                )
            }

        // 7) loans close to the finish line
        loans.filter { it.totalCount > 1 && it.remainingCount in 1..2 }.forEach { g ->
            list += FinanceInsight(
                InsightLevel.GOOD,
                "${q(g.name)} رو به پایانه",
                if (g.remainingCount == 1) "فقط یک قسط مونده (${g.nextDueAt?.let { dayMonthLabel(it) } ?: ""})."
                else "فقط ${fa(g.remainingCount)} قسط مونده؛ آخرین قسط ${g.lastDueAt?.let { dayMonthLabel(it) } ?: ""}.",
                g.reminderIds
            )
        }

        // 8) repeating bills whose recorded amount differs from what the person usually pays
        fin.filter { it.financialType == FinancialType.BILL && recurs(it) && !it.isDone }.forEach { b ->
            val typical = typicalAmount(b.id, logs) ?: return@forEach
            val now0 = b.amount ?: 0L
            if (now0 > 0 && typical > 0 && abs(now0 - typical) * 100 / typical >= 30) {
                list += FinanceInsight(
                    InsightLevel.INFO,
                    "مبلغ ${q(b.title)} با عادت پرداختت نمی‌خونه",
                    "ثبت‌شده ${now0.compactToman()}، اما معمولاً حدود ${typical.compactToman()} می‌دی. اگه عوض شده، مبلغ رو اصلاح کن.",
                    listOf(b.id)
                )
            }
        }

        // 9) things due soon with no amount: the totals above are blind to them
        val noAmount = fin.filter {
            isOutgoing(it) && !it.isDone && (it.amount ?: 0L) <= 0 &&
                it.triggerAtMillis in now..(now + 14 * DAY)
        }
        if (noAmount.isNotEmpty()) {
            list += FinanceInsight(
                InsightLevel.INFO,
                "برای ${fa(noAmount.size)} مورد نزدیک مبلغ ثبت نشده",
                "تا مبلغ نزنی، جمع‌های ماه و پیش‌بینی ناقص می‌مونن: " +
                    noAmount.take(3).joinToString("، ") { q(it.title) },
                noAmount.map { it.id }
            )
        }

        // 10) good news: everything of a kind is settled this month
        val inst = plan.loads.firstOrNull { it.type == FinancialType.INSTALLMENT }
        if (inst != null && inst.paid > 0 && inst.remaining == 0L && plan.carryOverdue == 0L) {
            list += FinanceInsight(
                InsightLevel.GOOD,
                "همه‌ی اقساط این ماه پرداخت شده 🎉",
                "${inst.paid.compactToman()} پرداخت کردی."
            )
        } else if (plan.monthTotal > 0 && plan.remaining == 0L && plan.carryOverdue == 0L) {
            list += FinanceInsight(
                InsightLevel.GOOD,
                "تعهدات این ماه تموم شده 🎉",
                "${plan.paid.compactToman()} پرداخت کردی و چیزی عقب نیست."
            )
        }

        val order = mapOf(InsightLevel.CRITICAL to 0, InsightLevel.WARNING to 1, InsightLevel.INFO to 2, InsightLevel.GOOD to 3)
        return list.sortedBy { order.getValue(it.level) }.take(10)
    }

    // ───────────── the morning digest ─────────────

    /** Title + text for the daily notification, or null when there is nothing worth interrupting for. */
    fun digest(analysis: FinanceAnalysis, now: Long): Pair<String, String>? {
        val lines = ArrayList<String>()
        if (analysis.overdue.isNotEmpty()) {
            lines += "عقب‌افتاده: ${fa(analysis.overdue.size)} مورد، ${analysis.overdue.sumOf { it.amount }.compactToman()}"
        }
        fun part(label: String, items: List<DueLine>) {
            if (items.isEmpty()) return
            val names = items.take(2).joinToString("، ") { q(it.title) }
            val more = if (items.size > 2) " و ${fa(items.size - 2)} مورد دیگر" else ""
            lines += "$label ${items.sumOf { it.amount }.compactToman()}: $names$more"
        }
        part("امروز", analysis.today)
        part("فردا", analysis.tomorrow)

        val crunch = analysis.insights.firstOrNull { it.title.startsWith("هفته‌ی سنگین") }
        if (crunch != null) lines += crunch.title

        // Something falls due in the next 3 days on a day the banks are closed (holiday / Friday / Thursday):
        // say so now, while there is still a bank day left to deal with it.
        val onClosedDay = analysis.forecast.flatMap { it.items }
            .filter { it.at >= startOfDay(now) && it.at <= now + 3 * DAY && !IranHolidays.isBankDay(it.at) }
            .distinctBy { it.reminderId }
            .sortedBy { it.at }
        if (onClosedDay.isNotEmpty()) {
            val names = onClosedDay.take(2).joinToString("، ") { q(it.title) + " (" + dayMonthLabel(it.at) + ")" }
            val more = if (onClosedDay.size > 2) " و ${fa(onClosedDay.size - 2)} مورد دیگر" else ""
            lines += "⚠ بانک بسته‌ست و سررسید داره: $names$more — قبلش اقدام کن"
        }

        val firstOfMonth = JalaliCalendar.millisToJalali(now).day == 1
        if (firstOfMonth && analysis.month.mustPayNow > 0) {
            val m = analysis.month
            return "برنامه‌ی مالی ${m.label}" to
                (listOf("این ماه ${m.mustPayNow.compactToman()} پرداخت داری") + lines).joinToString("\n")
        }
        if (lines.isEmpty()) return null
        val title = when {
            analysis.overdue.isNotEmpty() -> "خلاصه‌ی مالی: مورد عقب‌افتاده داری"
            analysis.today.any { it.type == FinancialType.CHECK } -> "خلاصه‌ی مالی: امروز چک داری"
            else -> "خلاصه‌ی مالی امروز"
        }
        return title to lines.joinToString("\n")
    }

    // ───────────── smart payment amounts ─────────────

    /** Quick amounts offered in the «ثبت پرداخت» dialog: label → amount. */
    fun paymentSuggestions(reminder: Reminder, logs: List<PaymentLog>, loan: LoanGroup?): List<Pair<String, Long>> {
        val rest = remainingOf(reminder)
        if (rest <= 0) return emptyList()
        val out = LinkedHashMap<Long, String>()
        out[rest] = "همه‌ی مانده"
        loan?.takeIf { it.eachAmount in 1 until rest }?.let { out.putIfAbsent(it.eachAmount, "یک قسط") }
        val last = logs.filter { it.reminderId == reminder.id }.maxByOrNull { it.timestampMillis }
        if (last != null && last.amount in 1 until rest) out.putIfAbsent(last.amount, "مثل دفعه‌ی قبل")
        if (rest >= 2_000_000 && out.size < 3) {
            val half = (rest / 2 / 1000) * 1000
            if (half > 0) out.putIfAbsent(half, "نصف مانده")
        }
        return out.map { (amount, label) -> label to amount }
    }
}
