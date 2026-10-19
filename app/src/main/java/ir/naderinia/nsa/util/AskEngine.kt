package ir.naderinia.nsa.util

import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import java.util.Calendar

/** One answer of the «بپرس» box: a sentence, optional detail lines and the reminders it talks about. */
data class AskAnswer(
    val headline: String,
    val lines: List<String> = emptyList(),
    val reminderIds: List<Long> = emptyList(),
    /** false = the question was not understood (the UI then shows example questions). */
    val understood: Boolean = true
)

/**
 * Answers questions like «این ماه چقدر قسط دارم؟», «چک‌های این هفته», «فردا تعطیله؟», «بدهی‌های عقب‌افتاده»
 * from the data already on the phone — no network, no AI model, just rules. Pure Kotlin (testable).
 */
object AskEngine {

    private const val DAY = 24L * 60 * 60 * 1000

    private class Range(val start: Long, val end: Long, val label: String)

    private fun w(p: String) = Regex("""(?<![\p{L}\d])(?:$p)(?![\p{L}\d])""")

    private val SUM_Q = w("چقدر|چند تومان|چند تومن|مجموع|جمع|مبلغ|چقدره|چنده|چه قدر")
    private val COUNT_Q = w("چند تا|چندتا|تعداد|چندتاست|چند مورد|چند")
    private val NEXT_Q = w("بعدی|اولین|نزدیکترین|نزدیک ترین")
    private val OVERDUE_Q = w("عقب افتاده|عقبافتاده|عقب|معوق|گذشته|دیرکرد|پرداخت نشده")
    private val HOLIDAY_Q = w("تعطیل|تعطیلی|تعطیلات|تعطیله")

    private val FIN_TYPES: List<Pair<Regex, FinancialType>> = listOf(
        w("چک|چکها|چک ها|چکام|چکهام") to FinancialType.CHECK,
        w("قسط|قسطها|قسط ها|اقساط|وام|قسطام|قسطهام") to FinancialType.INSTALLMENT,
        w("قبض|قبضها|قبض ها|قبوض|قبضام|قبضهام") to FinancialType.BILL,
        w("طلب|طلبها|طلب ها|طلبکارم|طلبام") to FinancialType.CREDIT,
        w("بدهی|بدهیها|بدهی ها|بدهکارم|بدهیام|قرض") to FinancialType.DEBT
    )
    private val ANY_PAY_Q = w("پرداخت|بپردازم|بدم|خرج|هزینه|مخارج|پول")

    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun fa(n: Int) = JalaliCalendar.toPersianDigits(n.toString())

    private fun monthStart(y: Int, m: Int): Long = JalaliCalendar.jalaliToMillis(y, m, 1, 0, 0)

    private fun nextMonth(y: Int, m: Int): Pair<Int, Int> = if (m == 12) (y + 1) to 1 else y to (m + 1)

    private fun rangeOf(t: String, now: Long): Range? {
        val today = startOfDay(now)
        val j = JalaliCalendar.millisToJalali(now)
        val thisMonth = Range(monthStart(j.year, j.month), nextMonth(j.year, j.month).let { monthStart(it.first, it.second) }, "این ماه")

        Regex("""(\d+)\s*روز\s*(?:آینده|دیگه|بعد)""").find(t)?.let {
            val n = it.groupValues[1].toInt().coerceIn(1, 366)
            return Range(today, today + n * DAY, "${fa(n)} روز آینده")
        }
        if (w("پس ?فردا").containsMatchIn(t)) return Range(today + 2 * DAY, today + 3 * DAY, "پس‌فردا")
        if (w("فردا").containsMatchIn(t)) return Range(today + DAY, today + 2 * DAY, "فردا")
        if (w("امروز|امشب").containsMatchIn(t)) return Range(today, today + DAY, "امروز")
        if (w("تا آخر ماه|تا اخر ماه|تا پایان ماه").containsMatchIn(t)) return Range(now, thisMonth.end, "تا آخر ماه")
        if (w("(?:ماه|هفته)\\s*(?:بعد|آینده|دیگه)").containsMatchIn(t)) {
            if (t.contains("هفته")) {
                val ws = weekStart(today)
                return Range(ws + 7 * DAY, ws + 14 * DAY, "هفته‌ی بعد")
            }
            val (ny, nm) = nextMonth(j.year, j.month)
            val (ny2, nm2) = nextMonth(ny, nm)
            return Range(monthStart(ny, nm), monthStart(ny2, nm2), "ماه بعد")
        }
        if (w("این هفته|هفته").containsMatchIn(t)) {
            val ws = weekStart(today)
            return Range(ws, ws + 7 * DAY, "این هفته")
        }
        if (w("این ماه|ماه جاری|ماه").containsMatchIn(t)) return thisMonth
        val monthName = JalaliCalendar.monthNames.firstOrNull { w(it).containsMatchIn(t) }
        if (monthName != null) {
            val mo = JalaliCalendar.monthNames.indexOf(monthName) + 1
            val y = if (mo < j.month) j.year + 1 else j.year
            val (ny, nm) = nextMonth(y, mo)
            return Range(monthStart(y, mo), monthStart(ny, nm), monthName)
        }
        return null
    }

    /** Iranian week starts on Saturday. */
    private fun weekStart(today: Long): Long {
        var d = today
        var guard = 0
        while (Calendar.getInstance().apply { timeInMillis = d }.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY && guard < 7) {
            d -= DAY; guard++
        }
        return startOfDay(d + 3_600_000L)
    }

    private fun remaining(r: Reminder): Long = ((r.amount ?: 0L) - r.amountPaid).coerceAtLeast(0L)

    private fun isOpenMoney(r: Reminder) = r.financialType != null && !r.isDone && remaining(r) > 0

    private fun line(r: Reminder): String {
        val whenText = dayMonthLabel(r.triggerAtMillis)
        val money = if (r.financialType != null && remaining(r) > 0) " — ${remaining(r).compactToman()}" else ""
        val who = r.counterparty?.takeIf { it.isNotBlank() }?.let { " (${JalaliCalendar.toPersianDigits(it)})" } ?: ""
        val closed = if (r.financialType != null && !IranHolidays.isBankDay(r.triggerAtMillis)) "  ⚠ بانک بسته‌ست" else ""
        return "$whenText: ${JalaliCalendar.toPersianDigits(r.title)}$who$money$closed"
    }

    private fun typeWord(t: FinancialType, plural: Boolean = true) = when (t) {
        FinancialType.CHECK -> if (plural) "چک" else "چک"
        FinancialType.INSTALLMENT -> "قسط"
        FinancialType.BILL -> "قبض"
        FinancialType.CREDIT -> "طلب"
        FinancialType.DEBT -> "بدهی"
    }

    fun answer(question: String, reminders: List<Reminder>, now: Long = System.currentTimeMillis()): AskAnswer {
        val t = PersianText.prepare(question).replace("?", " ").replace(Regex("\\s+"), " ").trim()
        if (t.isBlank()) return AskAnswer("بپرس: مثلاً «این ماه چقدر قسط دارم؟»", understood = false)
        val range = rangeOf(t, now)

        // ── holidays ──
        if (HOLIDAY_Q.containsMatchIn(t)) {
            if (range != null && range.label in setOf("امروز", "فردا", "پس‌فردا")) {
                val s = IranHolidays.status(range.start + 12 * 3_600_000L)
                val h = when (s.kind) {
                    IranHolidays.Kind.HOLIDAY -> "بله، ${range.label} تعطیل رسمیه: ${s.name}" + if (s.approximate) " (تاریخ تقریبی)" else ""
                    IranHolidays.Kind.FRIDAY -> "بله، ${range.label} جمعه‌ست"
                    IranHolidays.Kind.THURSDAY -> "تعطیل رسمی نیست، ولی پنجشنبه‌ست و بیشتر شعبه‌های بانک بسته‌ان"
                    IranHolidays.Kind.WORKING -> "نه، ${range.label} روز کاریه"
                }
                return AskAnswer(h)
            }
            val ups = IranHolidays.upcoming(now, 4)
            return AskAnswer(
                "تعطیلات رسمی پیش رو:",
                ups.map { (ms, h) ->
                    "${JalaliCalendar.toPersianDigits(dayMonthLabel(ms))} — ${h.name}" + if (h.approximate) " (تقریبی)" else ""
                }
            )
        }

        // ── overdue ──
        if (OVERDUE_Q.containsMatchIn(t)) {
            val type = FIN_TYPES.firstOrNull { it.first.containsMatchIn(t) }?.second
            val items = reminders.filter {
                !it.isDone && it.triggerAtMillis < now &&
                    (if (type != null) it.financialType == type && remaining(it) > 0 else it.financialType == null || remaining(it) > 0)
            }.sortedBy { it.triggerAtMillis }
            if (items.isEmpty()) return AskAnswer("چیزی عقب‌افتاده نداری ✅")
            val money = items.filter { isOpenMoney(it) }.sumOf { remaining(it) }
            val head = "${fa(items.size)} مورد عقب‌افتاده داری" + if (money > 0) " (جمع مالی: ${money.compactToman()})" else ""
            return AskAnswer(head, items.take(8).map(::line), items.map { it.id })
        }

        // ── money ──
        val finType = FIN_TYPES.firstOrNull { it.first.containsMatchIn(t) }?.second
        val money = finType != null || ANY_PAY_Q.containsMatchIn(t)
        if (money) {
            val r = range ?: if (NEXT_Q.containsMatchIn(t)) null else rangeOf("این ماه", now)
            val pool = reminders.filter {
                isOpenMoney(it) &&
                    (if (finType != null) it.financialType == finType else it.financialType != FinancialType.CREDIT) &&
                    (r == null || it.triggerAtMillis in r.start until r.end)
            }.sortedBy { it.triggerAtMillis }
            val name = finType?.let { typeWord(it) } ?: "پرداخت"
            if (NEXT_Q.containsMatchIn(t)) {
                val next = pool.firstOrNull { it.triggerAtMillis >= now } ?: pool.firstOrNull()
                    ?: return AskAnswer("${name}ی باز نداری ✅")
                return AskAnswer("${name} بعدی:", listOf(line(next)), listOf(next.id))
            }
            if (pool.isEmpty()) return AskAnswer("${r?.label ?: ""} ${name}ی باز نداری ✅".trim())
            val total = pool.sumOf { remaining(it) }
            val verb = if (finType == FinancialType.CREDIT) "طلب داری" else "باید بپردازی"
            val head = if (COUNT_Q.containsMatchIn(t) && !SUM_Q.containsMatchIn(t))
                "${r?.label ?: ""} ${fa(pool.size)} $name داری".trim()
            else
                "${r?.label ?: ""} ${fa(pool.size)} $name، جمعاً ${total.compactToman()} $verb".trim()
            val closed = pool.count { !IranHolidays.isBankDay(it.triggerAtMillis) }
            val extra = if (closed > 0 && finType != FinancialType.CREDIT) listOf("⚠ ${fa(closed)} مورد روی روز تعطیل/بسته‌ی بانک افتاده؛ یه روز کاری قبلش اقدام کن") else emptyList()
            return AskAnswer(head, pool.take(8).map(::line) + extra, pool.map { it.id })
        }

        // ── medication ──
        if (w("دارو|قرص|آمپول|ویتامین|داروهام|داروها").containsMatchIn(t)) {
            val r = range ?: rangeOf("امروز", now)!!
            val items = reminders.filter { it.category == "دارو" && !it.isDone && it.triggerAtMillis in r.start until r.end }.sortedBy { it.triggerAtMillis }
            if (items.isEmpty()) return AskAnswer("${r.label} دارویی نداری ✅")
            return AskAnswer("${r.label} ${fa(items.size)} دارو داری:", items.map { "${JalaliCalendar.toPersianDigits(JalaliCalendar.formatDateTime(it.triggerAtMillis).substringAfter(" - "))} ${JalaliCalendar.toPersianDigits(it.title)}" }, items.map { it.id })
        }

        // ── birthdays ──
        if (w("تولد|تولدها|تولدا|سالگرد").containsMatchIn(t)) {
            val items = reminders.filter { it.category == "تولد و سالگرد" && !it.isDone && it.triggerAtMillis >= startOfDay(now) }.sortedBy { it.triggerAtMillis }
            if (items.isEmpty()) return AskAnswer("تولد یا سالگردی ثبت نکردی")
            val pick = if (NEXT_Q.containsMatchIn(t) || range == null) items.take(3) else items.filter { it.triggerAtMillis in range.start until range.end }
            if (pick.isEmpty()) return AskAnswer("${range?.label ?: ""} تولدی نداری".trim())
            return AskAnswer("تولد/سالگرد بعدی:", pick.map(::line), pick.map { it.id })
        }

        // ── generic agenda ──
        val agenda = w("چی دارم|چه کار|چه کاری|کارهام|کارام|کاری دارم|برنامه|یادآوری|یادآوریها|لیست|نشون بده|چی|چه")
        if (agenda.containsMatchIn(t) || range != null || NEXT_Q.containsMatchIn(t)) {
            if (NEXT_Q.containsMatchIn(t) && range == null) {
                val next = reminders.filter { !it.isDone && it.triggerAtMillis >= now }.minByOrNull { it.triggerAtMillis }
                    ?: return AskAnswer("یادآوری بازی نداری ✅")
                return AskAnswer("یادآوری بعدی:", listOf(line(next)), listOf(next.id))
            }
            val r = range ?: rangeOf("امروز", now)!!
            val items = reminders.filter { !it.isDone && it.triggerAtMillis in r.start until r.end }.sortedBy { it.triggerAtMillis }
            if (items.isEmpty()) return AskAnswer("${r.label} چیزی نداری ✅")
            return AskAnswer("${r.label} ${fa(items.size)} مورد داری:", items.take(10).map(::line), items.map { it.id })
        }

        // ── fallback: search the words ──
        val words = t.split(' ').filter { it.length >= 3 }
        if (words.isNotEmpty()) {
            val hits = reminders.filter { r ->
                val hay = PersianText.normalize(r.title + " " + r.note + " " + r.category + " " + (r.counterparty ?: ""))
                words.any { hay.contains(it) }
            }.sortedBy { it.triggerAtMillis }
            if (hits.isNotEmpty()) return AskAnswer("${fa(hits.size)} مورد پیدا کردم:", hits.take(8).map(::line), hits.map { it.id })
        }
        return AskAnswer("متوجه نشدم 🤔", understood = false)
    }

    /** Questions that make a good first impression in the empty state of the box. */
    val EXAMPLES = listOf(
        "این ماه چقدر قسط دارم؟",
        "چک‌های این هفته",
        "فردا تعطیله؟",
        "بدهی‌های عقب‌افتاده",
        "امروز چی دارم؟",
        "تعطیلی بعدی کی هست؟"
    )
}
