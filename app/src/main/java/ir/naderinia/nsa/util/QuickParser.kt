package ir.naderinia.nsa.util

import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.RepeatInterval
import java.util.Calendar

/** What [QuickParser] understood from one sentence. Nothing is saved until the person confirms. */
data class ParsedReminder(
    val title: String,
    val note: String,
    val category: String,
    val categoryColor: Long,
    val triggerAtMillis: Long,
    val repeatInterval: RepeatInterval,
    /** Comma-separated Calendar.DAY_OF_WEEK values (1=Sunday … 7=Saturday) for weekly repeats. */
    val repeatDaysOfWeek: String?,
    val amount: Long?,
    val counterparty: String?,
    val financialType: FinancialType?,
    val bankName: String?,
    val mileageTargetKm: Long?,
    val birthYear: Int?,
    /** > 1 only for an installment plan («۱۲ قسط»). */
    val installmentCount: Int,
    val critical: Boolean,
    val hasExplicitDate: Boolean,
    val hasExplicitTime: Boolean,
    /** The hour had no «صبح/عصر»: it was guessed, so the UI offers a one-tap AM↔PM switch. */
    val timeInferred: Boolean,
    val warnings: List<String>
)

/**
 * Turns «فردا ساعت ۵ عصر جلسه با آقای احمدی» / «قسط ۱۲ میلیونی بانک ملی هر ماه ۵ام» /
 * «چک ۴۰ میلیون ۲۰ آذر به نام رضایی» into a reminder — fully offline, rule based.
 *
 * Pure Kotlin (no Android classes) so it can be unit-tested on the JVM.
 */
object QuickParser {

    // Same hex values as ui/theme/Color.kt CategoryColors (kept here so this file has no UI dependency).
    private const val C_GENERAL = 0xFF4F46E5L
    private const val C_VEHICLE = 0xFF0D9488L
    private const val C_FINANCE = 0xFFDC2626L
    private const val C_WORK = 0xFFF59E0BL
    private const val C_PERSONAL = 0xFFDB2777L

    private val DOW = linkedMapOf(
        "یکشنبه" to Calendar.SUNDAY, "دوشنبه" to Calendar.MONDAY, "سهشنبه" to Calendar.TUESDAY,
        "چهارشنبه" to Calendar.WEDNESDAY, "پنجشنبه" to Calendar.THURSDAY, "جمعه" to Calendar.FRIDAY,
        "شنبه" to Calendar.SATURDAY
    )
    private const val DOW_ALT = "یکشنبه|دوشنبه|سهشنبه|چهارشنبه|پنجشنبه|جمعه|شنبه"
    private val MONTH_ALT = JalaliCalendar.monthNames.joinToString("|")

    private fun w(p: String) = Regex("""(?<![\p{L}\d])(?:$p)(?![\p{L}\d])""")

    // ── patterns ──
    private val CRITICAL = w("حیاتی|خیلی مهم|حتما|فراموش نشه|فراموش نشود|اورژانسی")
    private val REL_TIME = w("""(?:(\d+)|نیم)\s*(ساعت|دقیقه)\s*(?:دیگه|دیگر|بعد|بعدش)""")
    private val REPEAT_N = w("""هر\s+(\d+)\s*(روز|هفته|ماه|سال)""")
    private val REPEAT_DOWS = w("""هر\s+((?:$DOW_ALT)(?:\s+و?\s*(?:$DOW_ALT))*)""")
    private val REPEAT_DOWS_PLURAL = w("""(?:$DOW_ALT)\s*ها""")
    private val REPEAT_PERIOD = w("""هر\s+(صبح|شب|عصر|ظهر)""")
    private val REPEAT_DAILY = w("""هر\s*روز(?:ه)?|روزانه|همه روزه|روزی یک بار|روزی 1 بار""")
    private val REPEAT_WEEKLY = w("""هر\s*هفته|هفتگی|هفته ای|هفته‌ای""")
    private val REPEAT_MONTHLY = w("""هر\s*ماه(?:ه)?|ماهانه|ماهیانه|ماه به ماه|ماهی یک بار|ماهی 1 بار""")
    private val REPEAT_YEARLY = w("""هر\s*سال(?:ه)?|سالانه|سالیانه|سالی یک بار|سالی 1 بار""")
    private val INST_COUNT = w("""(\d{1,3})\s*(?:قسط|قسطه|اقساط|ماهه)""")
    private val KM = Regex("""(?<![\p{L}\d])(\d+(?:\.\d+)?)\s*(هزار)?\s*(?:کیلومتر|کیلومتری|کیلو متر|km)(?![\p{L}\d])""")
    private val AMT_START = Regex("""(\d+(?:\.\d+)?)\s*(میلیارد|میلیون|هزار)ی?(?![\p{L}])""")
    private val AMT_NEXT = Regex("""^\s*(?:و\s*)?(\d+(?:\.\d+)?)\s*(میلیارد|میلیون|هزار)ی?(?![\p{L}])""")
    private val AMT_CUR = Regex("""^\s*(تومان|تومن|تومانی|تومنی|ریال|ریالی)(?![\p{L}])""")
    private val AMT_WITH_CUR = Regex("""(?<![\d/.:])(\d{1,12})\s*(تومان|تومن|ریال|تومانی|تومنی)(?![\p{L}])""")
    private val AMT_PLAIN = Regex("""(?<![\d/.:])([1-9]\d{4,12})(?![\d/:])""")
    private val TIME_COLON = Regex("""(?<![\d/])(\d{1,2}):(\d{2})(?![\d])""")
    private val TIME_RABA_BE = w("""ربع به\s*(\d{1,2})""")
    private val TIME_SAAT = w("""ساعت\s*(\d{1,2})(?:\s*و\s*(نیم|ربع|(\d{1,2})\s*دقیقه?))?""")
    private val TIME_PERIOD_NUM = w("""(\d{1,2})(?:\s*و\s*(نیم|ربع))?\s*(صبح|ظهر|عصر|شب|بعدازظهر)""")
    private val PERIOD = w("صبح|ظهر|بعدازظهر|عصر|شب|بامداد|سحر|نیمهشب")
    private val DATE_FULL = Regex("""(?<![\d])(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})(?![\d])""")
    private val DATE_DAY_MONTH = w("""(\d{1,2})\s*(?:ام)?\s*($MONTH_ALT)(?:\s*ماه)?(?:\s*(1[34]\d{2}))?""")
    private val DATE_LAST_OF_MONTH = w("""آخر\s*($MONTH_ALT)""")
    private val DATE_END_OF_MONTH = w("""آخر\s*(?:این\s*)?ماه""")
    private val DATE_START_OF_NEXT_MONTH = w("""1ام\s*ماه(?:\s*(?:بعد|آینده|دیگه))?""")
    private val DATE_END_OF_WEEK = w("""آخر\s*(?:این\s*)?هفته""")
    private val DATE_AFTER_TOMORROW = w("پسفردا|پس فردا")
    private val DATE_TOMORROW = w("فردا")
    private val DATE_TODAY = w("امروز")
    private val DATE_TONIGHT = w("امشب")
    private val DATE_REL_N = w("""(\d+)\s*(روز|هفته|ماه|سال)\s*(?:دیگه|دیگر|بعد|بعدش|آینده)""")
    private val DATE_NEXT_UNIT = w("""(هفته|ماه|سال)\s*(?:بعد|بعدی|آینده|دیگه|دیگر)""")
    private val DATE_DOW = w("""($DOW_ALT)(?:\s*(?:آینده|بعدی|دیگه|بعد))?""")
    private val DATE_DAY_ONLY = w("""(?:روز\s*)?(\d{1,2})ام""")

    private val BANK_NAMES = listOf(
        "ملی", "ملت", "صادرات", "سپه", "تجارت", "رفاه", "کشاورزی", "مسکن",
        "پاسارگاد", "پارسیان", "سامان", "اقتصاد نوین", "کارآفرین", "سینا", "سرمایه",
        "شهر", "دی", "آینده", "پست بانک", "توسعه صادرات", "توسعه تعاون", "صنعت و معدن",
        "گردشگری", "ایران زمین", "قوامین", "خاورمیانه", "قرض الحسنه مهر ایران", "قرض الحسنه رسالت"
    )
    /** Names that are also ordinary words: only a bank when «بانک» comes first. */
    private val AMBIGUOUS_BANKS = setOf("ملی", "شهر", "دی", "آینده", "مسکن", "سرمایه", "کشاورزی", "سپه")
    private val BANK_ALT = BANK_NAMES.sortedByDescending { it.length }.joinToString("|")
    private val BANK_WITH_WORD = w("""بانک\s*($BANK_ALT)""")
    private val BANK_BARE = w("""($BANK_ALT)""")

    private val CHECK_KW = w("""چک(?!\s*(?:کن|کرد|می ?کن|بکن|میکن))|چکی""")
    private val INSTALLMENT_KW = w("قسط|قسطی|اقساط|وام|تسهیلات")
    private val BILL_KW = w("قبض|قبوض")
    private val UTILITY_KW = w("برق|گاز|اینترنت|تلفن|موبایل|شارژ همراه")
    private val PAY_VERB = w("پرداخت|بپردازم|بدم|بزنم|بدهم|بریزم")
    private val CREDIT_KW = w("طلب|طلبکارم|طلبکار|بگیرم|پس بگیرم|بدهکاره|بهم بدهکار|باید بده|بهم بده|بده بهم|پولمو")
    private val DEBT_KW = w("""بدهی|بدهکارم|بدهکار|قرض|پس بدم|باید\s+(?:\S+\s+){0,3}?(?:بدم|بدهم|بپردازم|بپردازیم)""")

    private val MED_KW = w("قرص|دارو|آمپول|ویتامین|شربت|کپسول|انسولین|قطره|داروی")
    private val MEETING_KW = w("جلسه|قرار|ملاقات|مصاحبه|وقت دکتر|نوبت|دندانپزشک|ویزیت|دکتر|وکیل")
    private val BIRTHDAY_KW = w("تولد|سالگرد|زادروز")
    private val VEHICLE_KW = w("ماشین|موتور|خودرو|روغن|لاستیک|تایر|ترمز|معاینه فنی|شخص ثالث|بنزین|سرویس دوره ای|باتری ماشین|بیمه بدنه")
    private val PROPERTY_KW = w("شارژ ساختمان|ساختمان|کولر|پکیج|اجاره|رهن|آسانسور|موتورخانه")
    private val SCHOOL_KW = w("مدرسه|امتحان|تکلیف|معلم|اولیا|دانشگاه|کلاس")
    private val RELIGIOUS_KW = w("نماز|دعا|زیارت|قرآن|روضه|کمیل|ندبه|محرم|عاشورا")
    private val BABY_KW = w("نوزاد|کودک|پوشک|واکسن بچه|شیر بچه")
    private val PREGNANCY_KW = w("بارداری|سونوگرافی|حاملگی")

    private val FILLER = setOf(
        "یادآوری", "یادم", "بنداز", "بنداز", "باش", "باشه", "که", "لطفا", "میخوام", "می", "خوام",
        "بهم", "برام", "من", "رو", "را", "یه", "هست", "بود", "هم", "برای", "بابت", "در", "و", "تا", "ام",
        "از", "به", "با"
    )
    private val STOP_AFTER_PREP = setOf(
        "بابت", "برای", "تو", "توی", "در", "ساعت", "که", "و", "رو", "را", "هر", "ماه", "روز", "فردا",
        "امروز", "بانک", "نام", "عنوان", "مبلغ", "مبلغی", "بده", "بدم", "بگیرم", "بدهم", "است", "هست", "باید",
        "طلب", "طلبکارم", "بدهی", "بدهکارم", "دارم", "میخوام", "چک", "قسط", "قبض", "پس", "قرض", "تا", "بپردازم"
    )
    /** Words that carry no information about WHAT the money is for. */
    private val WEAK_TITLE = setOf(
        "باید", "بدم", "بدهم", "بپردازم", "بگیرم", "دارم", "طلب", "طلبکارم", "بدهکارم", "بدهی",
        "پرداخت", "پس", "قرض", "بده", "بهم", "پولمو", "من", "به", "از", "با", "تا", "را", "رو"
    )
    private val HONORIFICS = setOf("آقای", "خانم", "دکتر", "مهندس", "حاج", "حاجی", "استاد", "سرکار", "جناب")

    // ───────────────────────────────────────────

    private class Work(var s: String) {
        fun take(re: Regex): MatchResult? {
            val m = re.find(s) ?: return null
            s = s.substring(0, m.range.first) + " " + s.substring(m.range.last + 1)
            return m
        }
        fun takeAll(re: Regex): Boolean {
            var any = false
            while (take(re) != null) any = true
            return any
        }
        fun has(re: Regex) = re.containsMatchIn(s)
        fun replaceMatch(m: MatchResult, with: String) {
            s = s.substring(0, m.range.first) + " " + with + " " + s.substring(m.range.last + 1)
        }
    }

    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun addDays(ms: Long, n: Int): Long =
        Calendar.getInstance().apply { timeInMillis = ms; add(Calendar.DAY_OF_YEAR, n) }.timeInMillis

    private fun at(dayStart: Long, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = dayStart
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun dowOf(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.DAY_OF_WEEK)

    private fun mult(unit: String): Double = when (unit) {
        "میلیارد" -> 1e9
        "میلیون" -> 1e6
        else -> 1e3
    }

    /** Heuristic for "does this read like a question for the assistant rather than a new reminder?" */
    fun looksLikeQuestion(raw: String): Boolean {
        val t = PersianText.normalize(raw)
        if (t.isBlank()) return false
        if (t.contains('?')) return true
        val first = t.split(' ').first()
        if (first in setOf("چقدر", "چند", "چنده", "کی", "کدوم", "کدام", "چی", "چه", "آیا", "کجا", "چرا", "چطور", "چجوری", "مجموع", "جمع", "نشون", "نشان", "بگو", "لیست")) return true
        if (Regex("^(?:چک|قسط|قبض|بدهی|طلب|کار|یادآوری|دارو|تولد|جلسه)\\s*(?:ها|هام|های)(?![\\p{L}])").containsMatchIn(t)) return true
        if (first in setOf("کارام", "کارهام", "داروهام", "چکام", "چکهام", "قسطام", "قسطهام", "قبضام", "قبضهام", "بدهیام", "طلبام", "تولدا", "تعطیلات", "تعطیلی")) return true
        return t.endsWith(" چنده") || t.endsWith(" کیه") || t.endsWith(" چیه") || t.endsWith(" کدومه") || t.endsWith(" چقدره") || t.endsWith(" چقدر") || t.endsWith(" کی هست") || t.endsWith(" کی میشه")
    }

    fun parse(input: String, now: Long = System.currentTimeMillis()): ParsedReminder? {
        if (input.isBlank()) return null
        val w = Work(
            PersianText.prepare(input)
                .replace("بعد از ظهر", "بعدازظهر")
                .replace("نیمه شب", "نیمهشب")
        )
        val warnings = ArrayList<String>()
        val todayStart = startOfDay(now)
        val jNow = JalaliCalendar.millisToJalali(now)

        // ── 1. critical ──
        val critical = w.takeAll(CRITICAL)

        // ── 2. financial type (decided early: it changes how a few words are read) ──
        var financialType: FinancialType? = when {
            w.has(CHECK_KW) -> FinancialType.CHECK
            w.has(INSTALLMENT_KW) -> FinancialType.INSTALLMENT
            w.has(BILL_KW) || (w.has(UTILITY_KW) && (w.has(PAY_VERB) || w.has(AMT_START))) -> FinancialType.BILL
            w.has(CREDIT_KW) -> FinancialType.CREDIT
            w.has(DEBT_KW) -> FinancialType.DEBT
            else -> null
        }

        // ── 3. repeat ──
        var repeat = RepeatInterval.NONE
        var repeatDays: String? = null
        val unsupported = w.take(REPEAT_N)
        if (unsupported != null) {
            val n = unsupported.groupValues[1].toIntOrNull() ?: 1
            val unit = unsupported.groupValues[2]
            if (n == 1) {
                repeat = when (unit) { "روز" -> RepeatInterval.DAILY; "هفته" -> RepeatInterval.WEEKLY; "ماه" -> RepeatInterval.MONTHLY; else -> RepeatInterval.YEARLY }
            } else {
                warnings += "تکرار «هر ${JalaliCalendar.toPersianDigits(n.toString())} $unit» پشتیبانی نمی‌شه؛ بدون تکرار ساخته شد"
            }
        }
        if (repeat == RepeatInterval.NONE) {
            val dows = w.take(REPEAT_DOWS)
            if (dows != null) {
                repeat = RepeatInterval.WEEKLY
                val days = Regex(DOW_ALT).findAll(dows.groupValues[1]).mapNotNull { DOW[it.value] }.distinct().toList()
                repeatDays = days.sorted().joinToString(",")
            } else {
                val plural = w.take(REPEAT_DOWS_PLURAL)
                if (plural != null) {
                    repeat = RepeatInterval.WEEKLY
                    val d = Regex(DOW_ALT).find(plural.value)?.value?.let { DOW[it] }
                    if (d != null) repeatDays = d.toString()
                }
            }
        }
        if (repeat == RepeatInterval.NONE) {
            val per = w.take(REPEAT_PERIOD)
            if (per != null) {
                repeat = RepeatInterval.DAILY
                w.s += " " + per.groupValues[1]
            }
        }
        if (repeat == RepeatInterval.NONE) {
            repeat = when {
                w.takeAll(REPEAT_DAILY) -> RepeatInterval.DAILY
                w.takeAll(REPEAT_WEEKLY) -> RepeatInterval.WEEKLY
                w.takeAll(REPEAT_MONTHLY) -> RepeatInterval.MONTHLY
                w.takeAll(REPEAT_YEARLY) -> RepeatInterval.YEARLY
                else -> RepeatInterval.NONE
            }
        }

        // ── 4. installment count ──
        var installmentCount = 1
        if (financialType == FinancialType.INSTALLMENT) {
            val m = w.take(INST_COUNT)
            if (m != null) installmentCount = (m.groupValues[1].toIntOrNull() ?: 1).coerceIn(1, 60)
            if (installmentCount > 1 && repeat != RepeatInterval.NONE) repeat = RepeatInterval.NONE
        }

        // ── 5. mileage, then amount ──
        var mileageKm: Long? = null
        w.take(KM)?.let { m ->
            val base = m.groupValues[1].toDoubleOrNull() ?: 0.0
            mileageKm = (if (m.groupValues[2].isNotEmpty()) base * 1000 else base).toLong().takeIf { it > 0 }
        }
        var amount: Long? = null
        run {
            val start = AMT_START.find(w.s)
            if (start != null) {
                var total = (start.groupValues[1].toDouble() * mult(start.groupValues[2]))
                var lastMult = mult(start.groupValues[2])
                var end = start.range.last + 1
                while (true) {
                    val rest = w.s.substring(end)
                    val n = AMT_NEXT.find(rest) ?: break
                    val mm = mult(n.groupValues[2])
                    if (mm >= lastMult) break
                    total += n.groupValues[1].toDouble() * mm
                    lastMult = mm
                    end += n.range.last + 1
                }
                var riyal = false
                AMT_CUR.find(w.s.substring(end))?.let { c ->
                    riyal = c.groupValues[1].startsWith("ریال")
                    end += c.range.last + 1
                }
                amount = (if (riyal) total / 10 else total).toLong()
                w.s = w.s.substring(0, start.range.first) + " " + w.s.substring(end)
            } else {
                val cur = AMT_WITH_CUR.find(w.s)
                if (cur != null) {
                    val v = cur.groupValues[1].toLong()
                    amount = if (cur.groupValues[2].startsWith("ریال")) v / 10 else v
                    w.s = w.s.substring(0, cur.range.first) + " " + w.s.substring(cur.range.last + 1)
                } else {
                    val plain = AMT_PLAIN.find(w.s)
                    if (plain != null) {
                        amount = plain.groupValues[1].toLong()
                        w.s = w.s.substring(0, plain.range.first) + " " + w.s.substring(plain.range.last + 1)
                    }
                }
            }
        }
        if (amount != null && amount!! <= 0) amount = null

        // ── 6. time ──
        var hour: Int? = null
        var minute = 0
        var relMillis: Long? = null
        var timeInferred = false
        var dayShift = 0
        var explicitPeriod: String? = null
        var explicit24 = false

        w.take(REL_TIME)?.let { m ->
            val unit = m.groupValues[2]
            val n = if (m.groupValues[1].isEmpty()) 0.5 else m.groupValues[1].toDouble()
            val ms = if (unit == "ساعت") (n * 3_600_000).toLong() else (n * 60_000).toLong()
            relMillis = now + ms
        }
        if (relMillis == null) {
            val colon = TIME_COLON.find(w.s)
            if (colon != null) {
                hour = colon.groupValues[1].toInt(); minute = colon.groupValues[2].toInt()
                explicit24 = colon.groupValues[1].length == 2 && colon.groupValues[1].startsWith("0")
                w.take(TIME_COLON)
            } else {
                val raba = w.take(TIME_RABA_BE)
                if (raba != null) {
                    hour = (raba.groupValues[1].toInt() + 23) % 24; minute = 45
                } else {
                    val saat = w.take(TIME_SAAT)
                    if (saat != null) {
                        hour = saat.groupValues[1].toInt()
                        val extra = saat.groupValues[2]
                        minute = when {
                            extra == "نیم" -> 30
                            extra == "ربع" -> 15
                            saat.groupValues[3].isNotEmpty() -> saat.groupValues[3].toInt()
                            else -> 0
                        }
                    } else {
                        val pn = w.take(TIME_PERIOD_NUM)
                        if (pn != null) {
                            hour = pn.groupValues[1].toInt()
                            minute = when (pn.groupValues[2]) { "نیم" -> 30; "ربع" -> 15; else -> 0 }
                            explicitPeriod = pn.groupValues[3]
                        }
                    }
                }
            }
            if (hour != null && (hour!! !in 0..23 || minute !in 0..59)) {
                warnings += "ساعت نامعتبر بود و نادیده گرفته شد"
                hour = null; minute = 0; explicitPeriod = null
            }
        }
        // period word (may also stand alone: «فردا صبح»)
        val periodMatch = if (relMillis == null) w.take(PERIOD) else null
        val period = explicitPeriod ?: periodMatch?.value
        var tonight = false
        if (period != null) {
            val h = hour
            if (h == null) {
                hour = when (period) {
                    "صبح" -> 8; "ظهر" -> 12; "بعدازظهر" -> 16; "عصر" -> 17; "شب" -> 21
                    "بامداد", "سحر" -> 5; "نیمهشب" -> 0
                    else -> 9
                }
                if (period == "نیمهشب") dayShift = 1
                if (period == "شب") tonight = true
            } else {
                hour = when (period) {
                    "عصر", "بعدازظهر" -> if (h < 12) h + 12 else h
                    "ظهر" -> if (h in 1..4) h + 12 else h
                    "شب" -> when {
                        h in 5..11 -> h + 12
                        h == 12 -> { dayShift = 1; 0 }
                        h in 1..4 -> { dayShift = 1; h }
                        else -> h
                    }
                    else -> h
                }
            }
        } else if (hour != null && !explicit24) {
            // «ساعت ۵» alone: 1..6 almost always means the afternoon; 7..11 is more likely the morning.
            val h = hour!!
            if (h in 1..11) {
                timeInferred = true
                if (h in 1..6) hour = h + 12
            }
        }

        // ── 7. date ──
        var dayStart: Long? = null
        var explicitYear: Int? = null
        var fullDateGiven = false

        fun jalaliDay(year: Int, month: Int, day: Int): Long =
            startOfDay(JalaliCalendar.jalaliToMillis(year, month, day.coerceIn(1, JalaliCalendar.daysInJalaliMonth(year, month)), 12, 0))

        val full = DATE_FULL.find(w.s)
        if (full != null && full.groupValues[1].toInt() in 1300..1500 && full.groupValues[2].toInt() in 1..12 && full.groupValues[3].toInt() in 1..31) {
            explicitYear = full.groupValues[1].toInt()
            dayStart = jalaliDay(explicitYear, full.groupValues[2].toInt(), full.groupValues[3].toInt())
            fullDateGiven = true
            w.take(DATE_FULL)
        }
        if (dayStart == null) {
            val dm = DATE_DAY_MONTH.find(w.s)
            if (dm != null && dm.groupValues[1].toInt() in 1..31) {
                val d = dm.groupValues[1].toInt()
                val mo = JalaliCalendar.monthNames.indexOf(dm.groupValues[2]) + 1
                val y = dm.groupValues[3].toIntOrNull()
                explicitYear = y
                if (y != null) {
                    dayStart = jalaliDay(y, mo, d); fullDateGiven = true
                } else {
                    var cand = jalaliDay(jNow.year, mo, d)
                    if (cand < todayStart) cand = jalaliDay(jNow.year + 1, mo, d)
                    dayStart = cand
                }
                w.take(DATE_DAY_MONTH)
            }
        }
        if (dayStart == null) {
            val last = w.take(DATE_LAST_OF_MONTH)
            if (last != null) {
                val mo = JalaliCalendar.monthNames.indexOf(last.groupValues[1]) + 1
                var y = jNow.year
                var cand = jalaliDay(y, mo, 31)
                if (cand < todayStart) { y++; cand = jalaliDay(y, mo, 31) }
                dayStart = cand
            }
        }
        if (dayStart == null && w.take(DATE_START_OF_NEXT_MONTH) != null) {
            val total = jNow.year * 12 + jNow.month // next month index (0-based month = jNow.month)
            dayStart = jalaliDay(total / 12, total % 12 + 1, 1)
        }
        if (dayStart == null && w.take(DATE_END_OF_MONTH) != null) {
            var cand = jalaliDay(jNow.year, jNow.month, 31)
            if (cand < todayStart) {
                val total = jNow.year * 12 + jNow.month
                cand = jalaliDay(total / 12, total % 12 + 1, 31)
            }
            dayStart = cand
        }
        if (dayStart == null && w.take(DATE_END_OF_WEEK) != null) {
            var d = todayStart
            var guard = 0
            while (dowOf(d) != Calendar.THURSDAY && guard < 8) { d = addDays(d, 1); guard++ }
            dayStart = d
        }
        if (dayStart == null && w.take(DATE_AFTER_TOMORROW) != null) dayStart = addDays(todayStart, 2)
        if (dayStart == null && w.take(DATE_TOMORROW) != null) dayStart = addDays(todayStart, 1)
        if (dayStart == null && w.take(DATE_TONIGHT) != null) {
            dayStart = todayStart
            tonight = true
            if (hour == null) hour = 21
            else if (period == null && hour!! in 1..11) { hour = hour!! + 12; timeInferred = false }
        }
        if (dayStart == null && w.take(DATE_TODAY) != null) dayStart = todayStart
        if (dayStart == null) {
            val rel = w.take(DATE_REL_N)
            if (rel != null) {
                val n = rel.groupValues[1].toInt().coerceIn(0, 3000)
                dayStart = when (rel.groupValues[2]) {
                    "روز" -> addDays(todayStart, n)
                    "هفته" -> addDays(todayStart, 7 * n)
                    "ماه" -> startOfDay(JalaliCalendar.addMonths(now, n))
                    else -> startOfDay(JalaliCalendar.addMonths(now, 12 * n))
                }
            }
        }
        if (dayStart == null) {
            val nx = w.take(DATE_NEXT_UNIT)
            if (nx != null) {
                dayStart = when (nx.groupValues[1]) {
                    "هفته" -> addDays(todayStart, 7)
                    "ماه" -> startOfDay(JalaliCalendar.addMonths(now, 1))
                    else -> startOfDay(JalaliCalendar.addMonths(now, 12))
                }
            }
        }
        if (dayStart == null) {
            val dow = w.take(DATE_DOW)
            if (dow != null) {
                val target = DOW[dow.groupValues[1]]!!
                var d = todayStart
                var guard = 0
                val sameDayOk = dow.value.trim() == dow.groupValues[1]
                if (!(sameDayOk && dowOf(d) == target)) {
                    do { d = addDays(d, 1); guard++ } while (dowOf(d) != target && guard < 8)
                }
                dayStart = d
            }
        }
        if (dayStart == null) {
            val dd = w.take(DATE_DAY_ONLY)
            if (dd != null && dd.groupValues[1].toInt() in 1..31) {
                val d = dd.groupValues[1].toInt()
                var y = jNow.year; var mo = jNow.month
                var cand = jalaliDay(y, mo, d)
                if (cand < todayStart) {
                    val total = y * 12 + mo
                    y = total / 12; mo = total % 12 + 1
                    cand = jalaliDay(y, mo, d)
                }
                dayStart = cand
            }
        }

        // ── 8. bank ──
        var bank: String? = null
        val withWord = BANK_WITH_WORD.find(w.s)
        if (withWord != null) {
            bank = withWord.groupValues[1]
            w.take(BANK_WITH_WORD)
        } else if (financialType == FinancialType.CHECK || financialType == FinancialType.INSTALLMENT) {
            val bare = BANK_BARE.findAll(w.s).firstOrNull { it.groupValues[1] !in AMBIGUOUS_BANKS }
            if (bare != null) {
                bank = bare.groupValues[1]
                w.s = w.s.substring(0, bare.range.first) + " " + w.s.substring(bare.range.last + 1)
            }
        }
        bank = bank?.split(' ')?.joinToString(" ") { it }?.let { b ->
            // restore the spelling the app's bank list uses
            when (b) { "پست بانک" -> "پست‌بانک"; "قرض الحسنه مهر ایران" -> "قرض‌الحسنه مهر ایران"; "قرض الحسنه رسالت" -> "قرض‌الحسنه رسالت"; else -> b }
        }

        // ── 9. counterparty (money matters only) ──
        var counterparty: String? = null
        w.s = w.s.replace(Regex("\\s+"), " ").trim()
        if (financialType != null) {
            val namePrep = Regex("""(?<![\p{L}\d])(?:به نام|به|از|طلب از|بدهی به)\s+((?:[\p{L}]+)(?:\s+[\p{L}]+)?(?:\s+[\p{L}]+)?)""")
            val m = namePrep.find(w.s)
            if (m != null) {
                val tokens = m.groupValues[1].split(' ').toMutableList()
                val picked = ArrayList<String>()
                for (t in tokens) {
                    if (t in STOP_AFTER_PREP) break
                    picked += t
                    if (picked.count { it !in HONORIFICS } >= 2) break
                }
                val real = picked.filter { it !in HONORIFICS }
                if (real.isNotEmpty() && real.none { it.length < 2 }) {
                    counterparty = picked.joinToString(" ")
                    val spanEnd = m.range.first + (m.value.length - m.groupValues[1].length) + picked.joinToString(" ").length
                    w.s = w.s.substring(0, m.range.first) + " " + w.s.substring(minOf(spanEnd, w.s.length))
                }
            }
        }

        // ── 10. category ──
        val isCheckLike = financialType != null
        var category = "عمومی"
        var color = C_GENERAL
        var defaultRepeat: RepeatInterval? = null
        when {
            isCheckLike -> { category = "مالی"; color = C_FINANCE }
            w.has(MED_KW) -> { category = "دارو"; color = C_PERSONAL }
            w.has(BIRTHDAY_KW) -> { category = "تولد و سالگرد"; color = C_PERSONAL; defaultRepeat = RepeatInterval.YEARLY }
            w.has(VEHICLE_KW) || mileageKm != null -> { category = "وسیله نقلیه"; color = C_VEHICLE }
            w.has(PROPERTY_KW) -> { category = "ساختمان"; color = C_GENERAL }
            w.has(PREGNANCY_KW) -> { category = "بارداری"; color = C_PERSONAL }
            w.has(BABY_KW) -> { category = "نوزاد و کودک"; color = C_PERSONAL }
            w.has(RELIGIOUS_KW) -> { category = "مذهبی"; color = C_GENERAL }
            w.has(SCHOOL_KW) -> { category = "مدرسه"; color = C_WORK }
            w.has(MEETING_KW) -> { category = "جلسات"; color = C_WORK }
        }
        if (repeat == RepeatInterval.NONE && defaultRepeat != null) repeat = defaultRepeat

        // ── 11. when exactly ──
        var birthYear: Int? = null
        if (category == "تولد و سالگرد" && fullDateGiven && explicitYear != null && explicitYear < jNow.year && dayStart != null) {
            birthYear = explicitYear
            val j = JalaliCalendar.millisToJalali(dayStart)
            var cand = jalaliDay(jNow.year, j.month, j.day)
            if (cand < todayStart) cand = jalaliDay(jNow.year + 1, j.month, j.day)
            dayStart = cand
        }

        val hasDate = dayStart != null || relMillis != null
        val hasTime = hour != null || relMillis != null
        val trigger: Long
        if (relMillis != null) {
            trigger = relMillis!!
        } else {
            var day = dayStart
            if (day == null && repeat == RepeatInterval.WEEKLY && repeatDays != null) {
                val wanted = repeatDays.split(',').mapNotNull { it.toIntOrNull() }.toSet()
                var d = todayStart
                var guard = 0
                val h0 = hour ?: 9
                while (guard < 8) {
                    if (dowOf(d) in wanted && at(d, h0, minute) > now) break
                    d = addDays(d, 1); guard++
                }
                day = d
            }
            if (day == null) {
                if (hour != null) {
                    val today = at(todayStart, hour!!, minute)
                    day = if (today > now + 30_000 || dayShift > 0) todayStart else addDays(todayStart, 1)
                    if (tonight && today <= now) day = addDays(todayStart, 1)
                } else {
                    day = addDays(todayStart, 1)
                    hour = 9; minute = 0
                    warnings += "زمان مشخص نشد؛ فردا ساعت ۹ صبح گذاشتم"
                }
            }
            if (hour == null) {
                if (day == todayStart && now < at(todayStart, 9, 0) - 300_000L) {
                    hour = 9; minute = 0
                } else if (day == todayStart) {
                    val soon = now + 3_600_000L
                    val cal = Calendar.getInstance().apply { timeInMillis = soon }
                    hour = cal.get(Calendar.HOUR_OF_DAY); minute = cal.get(Calendar.MINUTE)
                    warnings += "ساعت مشخص نشد؛ یک ساعت دیگه گذاشتم"
                } else {
                    hour = 9; minute = 0
                }
            }
            if (dayShift > 0) day = addDays(day, dayShift)
            trigger = at(day, hour!!, minute)
        }

        var trigger2 = trigger
        if (timeInferred && relMillis == null && trigger2 <= now && trigger2 + 12 * 3_600_000L > now &&
            startOfDay(trigger2) == todayStart
        ) trigger2 += 12 * 3_600_000L

        // Past (typed «امروز ساعت ۸» at 10 pm): say so instead of silently saving something that never rings.
        if (trigger2 <= now - 60_000L && relMillis == null) {
            warnings += "این زمان گذشته؛ تاریخ یا ساعت رو درست کن"
        }

        // ── 12. title ──
        var tokens = w.s.split(' ').filter { it.isNotBlank() && it != "و" }
        tokens = tokens.dropWhile { it in FILLER }.dropLastWhile { it in FILLER }
        val kept = tokens.filter { it !in setOf("یادآوری", "بنداز", "یادم", "ام", "لطفا") }
        var title = kept.joinToString(" ").replace(Regex("\\s+"), " ").trim(' ', '.', '-', '،', ':')
        if (title.length > 70) title = title.take(70).trimEnd()
        val genericOnly = setOf("چک", "قسط", "قبض", "بدهی", "طلب", "وام", "اقساط", "قبوض")
        if (financialType != null && title.isNotBlank() && title.split(' ').all { it in WEAK_TITLE }) title = ""
        if (title.isBlank()) {
            title = when (financialType) {
                FinancialType.CHECK -> "چک"
                FinancialType.INSTALLMENT -> "قسط"
                FinancialType.BILL -> "قبض"
                FinancialType.DEBT -> "بدهی"
                FinancialType.CREDIT -> "طلب"
                null -> if (category != "عمومی") category else "یادآوری"
            }
        }
        if (title in genericOnly && counterparty != null) title = "$title $counterparty"
        title = JalaliCalendar.toPersianDigits(title)

        var note = ""
        if (amount != null && financialType == null && category !in setOf("وسیله نقلیه", "ساختمان")) {
            note = "مبلغ: ${JalaliCalendar.toPersianDigits(amount!!.grouped())} تومان"
            amount = null
        }
        if (financialType != null && amount == null && financialType != FinancialType.BILL) {
            warnings += "مبلغی پیدا نشد؛ اگه لازمه بعداً ویرایش کن"
        }

        return ParsedReminder(
            title = title,
            note = note,
            category = category,
            categoryColor = color,
            triggerAtMillis = trigger2,
            repeatInterval = repeat,
            repeatDaysOfWeek = if (repeat == RepeatInterval.WEEKLY) repeatDays else null,
            amount = amount,
            counterparty = counterparty?.let { JalaliCalendar.toPersianDigits(it) },
            financialType = financialType,
            bankName = if (financialType == FinancialType.CHECK || financialType == FinancialType.INSTALLMENT) bank else null,
            mileageTargetKm = if (category == "وسیله نقلیه") mileageKm else null,
            birthYear = birthYear,
            installmentCount = installmentCount,
            critical = critical,
            hasExplicitDate = hasDate,
            hasExplicitTime = hasTime,
            timeInferred = timeInferred && relMillis == null,
            warnings = warnings
        )
    }
}
