package ir.naderinia.nsa.util

import ir.naderinia.nsa.data.RepeatInterval
import java.util.Calendar

/** اعداد فارسی به حروف، برای اینکه صدای گوینده در همه‌ی موتورهای TTS درست خونده بشه. */
object PersianWords {
    private val units = arrayOf(
        "صفر", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه", "ده",
        "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده"
    )
    private val tens = arrayOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")

    /** 0..99 به حروف؛ بیشتر از اون همون رقم برمی‌گرده. */
    fun of(n: Int): String {
        if (n < 0 || n > 99) return n.toString()
        if (n < 20) return units[n]
        val t = n / 10
        val u = n % 10
        return if (u == 0) tens[t] else tens[t] + " و " + units[u]
    }
}

/** یک یادآوری که از روی جمله‌ی گفتاری ساخته شده. */
data class VoiceReminder(
    val title: String,
    val triggerAtMillis: Long,
    val category: String,
    val repeat: RepeatInterval,
    /** مقادیر Calendar.DAY_OF_WEEK با کاما (فقط برای تکرار هفتگی)، وگرنه null */
    val repeatDaysOfWeek: String?,
    /** برای نمایش روی صفحه (با رقم فارسی) */
    val whenText: String,
    /** برای خوندن با صدا (اعداد به حروف) */
    val whenSpoken: String
)

sealed interface VoiceParseResult {
    data class Ok(val reminder: VoiceReminder) : VoiceParseResult
    /** [message] هم نوشته می‌شه و هم با صدا خونده می‌شه. */
    data class Problem(val message: String, val heard: String) : VoiceParseResult
}

/**
 * تبدیل جمله‌ی گفتاری فارسی به یادآوری — کاملاً آفلاین و بدون کتابخونه‌ی بیرونی.
 *
 * نمونه‌ها:
 *  «فردا ساعت ۸ صبح قرص فشار»
 *  «دو ساعت دیگه زنگ بزنم به مامان»
 *  «هر دوشنبه ساعت ۵ عصر کلاس زبان»
 *  «۲۵ آبان ساعت ربع به ۹ شب جلسه با مدیر»
 */
object VoiceReminderParser {

    private val unitWords = mapOf(
        "صفر" to 0, "یک" to 1, "یه" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "چار" to 4,
        "پنج" to 5, "شش" to 6, "شیش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10,
        "یازده" to 11, "دوازده" to 12, "سیزده" to 13, "چهارده" to 14, "پانزده" to 15,
        "پونزده" to 15, "شانزده" to 16, "شونزده" to 16, "هفده" to 17, "هجده" to 18, "نوزده" to 19
    )
    private val tensWords = mapOf(
        "بیست" to 20, "سی" to 30, "چهل" to 40, "پنجاه" to 50,
        "شصت" to 60, "هفتاد" to 70, "هشتاد" to 80, "نود" to 90
    )
    private val ordinalWords = mapOf(
        "اول" to 1, "یکم" to 1, "دوم" to 2, "سوم" to 3, "چهارم" to 4, "پنجم" to 5, "ششم" to 6,
        "هفتم" to 7, "هشتم" to 8, "نهم" to 9, "دهم" to 10, "یازدهم" to 11, "دوازدهم" to 12,
        "سیزدهم" to 13, "چهاردهم" to 14, "پانزدهم" to 15, "پونزدهم" to 15, "شانزدهم" to 16,
        "هفدهم" to 17, "هجدهم" to 18, "نوزدهم" to 19, "بیستم" to 20, "سیام" to 30
    )
    private val monthWords = mapOf(
        "فروردین" to 1, "اردیبهشت" to 2, "خرداد" to 3, "تیر" to 4, "مرداد" to 5, "شهریور" to 6,
        "مهر" to 7, "آبان" to 8, "ابان" to 8, "آذر" to 9, "اذر" to 9, "دی" to 10,
        "بهمن" to 11, "اسفند" to 12
    )
    private val weekdayWords = mapOf(
        "شنبه" to Calendar.SATURDAY, "یکشنبه" to Calendar.SUNDAY, "دوشنبه" to Calendar.MONDAY,
        "سهشنبه" to Calendar.TUESDAY, "چهارشنبه" to Calendar.WEDNESDAY,
        "پنجشنبه" to Calendar.THURSDAY, "جمعه" to Calendar.FRIDAY
    )
    private val weekdayNames = mapOf(
        Calendar.SATURDAY to "شنبه", Calendar.SUNDAY to "یکشنبه", Calendar.MONDAY to "دوشنبه",
        Calendar.TUESDAY to "سه‌شنبه", Calendar.WEDNESDAY to "چهارشنبه",
        Calendar.THURSDAY to "پنجشنبه", Calendar.FRIDAY to "جمعه"
    )
    private val periods = setOf("صبح", "ظهر", "بعدازظهر", "عصر", "شب", "بامداد", "نیمهشب")
    private val periodDefaultHour = mapOf(
        "صبح" to 8, "ظهر" to 12, "بعدازظهر" to 15, "عصر" to 17,
        "شب" to 21, "بامداد" to 5, "نیمهشب" to 0
    )
    private val durationMarkers = setOf("دیگه", "دیگر", "بعد", "بعدش")
    private val edgeStopWords = setOf(
        "و", "که", "به", "را", "رو", "تا", "برای", "هم", "ساعت", "دیگه", "دیگر",
        "بعد", "این", "همین", "یه", "من", "حدود"
    )

    /** الگوهای عبارت‌های اضافه که از عنوان حذف می‌شن. ترتیب مهمه (طولانی‌ها اول). */
    private val fillerRegexes = listOf(
        "یه یادآوری", "یک یادآوری", "یاد آوری", "یادآوری بساز", "یادآوری کن", "یادآوری",
        "یادم بنداز", "یادم بیار", "یادم باشه", "یادم نره", "ثبت کن", "بساز", "لطفا", "برام",
        "برای من", "به من"
    ).map { Regex(it) }

    private val categoryKeywords: List<Pair<String, List<String>>> = listOf(
        "دارو" to listOf("قرص", "دارو", "کپسول", "شربت", "آمپول", "انسولین", "ویتامین", "قطره"),
        "بارداری" to listOf("بارداری", "سونوگرافی"),
        "نوزاد و کودک" to listOf("نوزاد", "واکسن", "شیر خشک", "پوشک"),
        "تولد و سالگرد" to listOf("تولد", "سالگرد"),
        "وسیله نقلیه" to listOf("ماشین", "خودرو", "موتور", "روغن", "لاستیک", "تایر", "معاینه فنی", "کارواش"),
        "جلسات" to listOf("جلسه", "ملاقات", "قرار", "نوبت", "دکتر", "دندانپزشک", "پزشک"),
        "مذهبی" to listOf("نماز", "روزه", "قرآن", "زیارت", "دعا"),
        "مدرسه" to listOf("مدرسه", "امتحان", "کلاس", "تکلیف", "مشق", "دانشگاه"),
        "ساختمان" to listOf("ساختمان", "شارژ", "آسانسور")
    )

    // ───────────────────────── نرمال‌سازی ─────────────────────────

    private fun normalize(raw: String): String {
        val sb = StringBuilder()
        for (c in raw.trim()) {
            sb.append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    'ي', 'ى' -> 'ی'
                    'ك' -> 'ک'
                    'ۀ' -> 'ه'
                    '\u200c', '\u200f', '\u200e', 'ـ' -> ' '
                    '،', '؛', '؟', ',', '.', '!', '?', '"', '«', '»', '(', ')', '…' -> ' '
                    else -> c.lowercaseChar()
                }
            )
        }
        var t = sb.toString().replace(Regex("\\s+"), " ").trim()
        t = t.replace(Regex("(یک|دو|سه|چهار|پنج) ?شنبه"), "$1شنبه")
        t = t.replace(Regex("پس ?فردا"), "پسفردا")
        t = t.replace(Regex("بعد ?از ?ظهر"), "بعدازظهر")
        t = t.replace(Regex("نیمه ?شب"), "نیمهشب")
        t = t.replace(Regex("(\\d)\\s?(ام|م)\\b"), "$1$2")
        t = t.replace(Regex("(\\d{1,2}):(\\d{2})"), "$1:$2")
        return t
    }

    private class State(val tokens: List<String>) {
        val used = BooleanArray(tokens.size)
        fun free(i: Int) = i in tokens.indices && !used[i]
        fun at(i: Int): String? = if (free(i)) tokens[i] else null
        fun mark(from: Int, len: Int) {
            for (k in from until from + len) if (k in used.indices) used[k] = true
        }
    }

    // ───────────────────────── اعداد ─────────────────────────

    private fun number(st: State, i: Int): Pair<Int, Int>? {
        val t = st.at(i) ?: return null
        if (t.length in 1..4 && t.all { it in '0'..'9' }) return t.toInt() to 1
        tensWords[t]?.let { tv ->
            if (st.at(i + 1) == "و") {
                val u = unitWords[st.at(i + 2) ?: ""]
                if (u != null && u in 1..9) return (tv + u) to 3
            }
            return tv to 1
        }
        unitWords[t]?.let { return it to 1 }
        return null
    }

    /** شماره‌ی روز ماه: «۲۵»، «۲۵م»، «پانزدهم»، «بیست و یکم»، «بیست و پنج». */
    private fun dayNumber(st: State, i: Int): Pair<Int, Int>? {
        val t = st.at(i) ?: return null
        Regex("^(\\d{1,2})(ام|م)?$").find(t)?.let {
            val v = it.groupValues[1].toInt()
            return if (v in 1..31) v to 1 else null
        }
        ordinalWords[t]?.let { return it to 1 }
        tensWords[t]?.let { tv ->
            if (st.at(i + 1) == "و") {
                val o = ordinalWords[st.at(i + 2) ?: ""]
                if (o != null && o in 1..9) return (tv + o) to 3
            }
            if (tv == 30 && st.at(i + 1) == "ام") return 30 to 2
        }
        val n = number(st, i) ?: return null
        return if (n.first in 1..31) n else null
    }

    // ───────────────────────── تکرار ─────────────────────────

    private class RepeatSpec(var interval: RepeatInterval = RepeatInterval.NONE, var dow: Int? = null)

    private fun parseRepeat(st: State): RepeatSpec {
        val r = RepeatSpec()
        for (i in st.tokens.indices) {
            if (!st.free(i)) continue
            when (st.tokens[i]) {
                "روزانه", "هرروزه" -> { r.interval = RepeatInterval.DAILY; st.mark(i, 1) }
                "هفتگی" -> { r.interval = RepeatInterval.WEEKLY; st.mark(i, 1) }
                "ماهانه" -> { r.interval = RepeatInterval.MONTHLY; st.mark(i, 1) }
                "سالانه" -> { r.interval = RepeatInterval.YEARLY; st.mark(i, 1) }
                "هر" -> {
                    val next = st.at(i + 1) ?: continue
                    when {
                        next == "روز" -> { r.interval = RepeatInterval.DAILY; st.mark(i, 2) }
                        next in setOf("صبح", "ظهر", "عصر", "شب", "بعدازظهر") -> {
                            // «هر شب»: فقط «هر» رو برمی‌داریم؛ «شب» برای ساعت لازمه
                            r.interval = RepeatInterval.DAILY; st.mark(i, 1)
                        }
                        next == "هفته" -> { r.interval = RepeatInterval.WEEKLY; st.mark(i, 2) }
                        next == "ماه" -> { r.interval = RepeatInterval.MONTHLY; st.mark(i, 2) }
                        next == "سال" -> { r.interval = RepeatInterval.YEARLY; st.mark(i, 2) }
                        weekdayWords.containsKey(next) -> {
                            r.interval = RepeatInterval.WEEKLY
                            r.dow = weekdayWords[next]
                            st.mark(i, 2)
                        }
                    }
                }
            }
            if (r.interval != RepeatInterval.NONE) break
        }
        return r
    }

    // ───────────────────────── «X دیگه» ─────────────────────────

    private class Dur(var minutes: Int = 0, var days: Int = 0, var months: Int = 0)

    private fun durationComponent(st: State, j: Int, dur: Dur, allowBare: Boolean): Int? {
        val t = st.at(j) ?: return null
        when (t) {
            "نیم" -> {
                dur.minutes += 30
                return if (st.at(j + 1) == "ساعت") 2 else if (allowBare) 1 else null
            }
            "ربع" -> {
                dur.minutes += 15
                return if (st.at(j + 1) == "ساعت") 2 else 1
            }
            "هفته" -> { dur.days += 7; return 1 }
            "ماه" -> { dur.months += 1; return 1 }
        }
        val (n, len) = number(st, j) ?: return null
        return when (st.at(j + len)) {
            "دقیقه" -> { dur.minutes += n; len + 1 }
            "ساعت" -> { dur.minutes += 60 * n; len + 1 }
            "روز" -> { dur.days += n; len + 1 }
            "هفته" -> { dur.days += 7 * n; len + 1 }
            "ماه" -> { dur.months += n; len + 1 }
            "ربع" -> { dur.minutes += 15 * n; len + 1 }
            else -> null
        }
    }

    private fun parseDuration(st: State): Dur? {
        for (start in st.tokens.indices) {
            if (!st.free(start)) continue
            var j = start
            if (st.at(j) == "تا" || st.at(j) == "حدود") j++
            val dur = Dur()
            var comps = 0
            while (true) {
                val len = durationComponent(st, j, dur, comps > 0) ?: break
                j += len
                comps++
                if (st.at(j) == "و" && st.free(j + 1)) {
                    val probe = Dur()
                    if (durationComponent(st, j + 1, probe, true) != null) {
                        j++
                        continue
                    }
                }
                break
            }
            if (comps > 0 && st.at(j) in durationMarkers) {
                st.mark(start, j + 1 - start)
                return dur
            }
            // شکست خورد؛ از اول دوباره امتحان می‌کنیم (Dur جدید ساخته می‌شه)
        }
        return null
    }

    // ───────────────────────── تاریخ ─────────────────────────

    private class DateSpec(var base: Calendar? = null, var isToday: Boolean = false)

    private fun startOfDay(ms: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    private fun parseDate(st: State, now: Long, periodOut: Array<String?>): DateSpec {
        val spec = DateSpec()
        val today = startOfDay(now)
        for (i in st.tokens.indices) {
            if (!st.free(i)) continue
            val t = st.tokens[i]
            when {
                t == "امروز" -> { spec.base = today.clone() as Calendar; spec.isToday = true; st.mark(i, 1) }
                t == "امشب" -> {
                    spec.base = today.clone() as Calendar; spec.isToday = true
                    periodOut[0] = "شب"; st.mark(i, 1)
                }
                t == "فردا" -> { spec.base = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }; st.mark(i, 1) }
                t == "پسفردا" -> { spec.base = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 2) }; st.mark(i, 1) }
                weekdayWords.containsKey(t) -> {
                    val target = weekdayWords.getValue(t)
                    var diff = (target - today.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    if (diff == 0) diff = 7
                    spec.base = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, diff) }
                    st.mark(i, 1)
                }
                Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})$").matches(t) -> {
                    val g = Regex("^(\\d{4})/(\\d{1,2})/(\\d{1,2})$").find(t)!!.groupValues
                    val y = g[1].toInt(); val m = g[2].toInt(); val d = g[3].toInt()
                    if (m in 1..12 && d in 1..JalaliCalendar.daysInJalaliMonth(y, m)) {
                        spec.base = startOfDay(JalaliCalendar.jalaliToMillis(y, m, d, 0, 0))
                        st.mark(i, 1)
                    }
                }
                else -> {
                    val dn = dayNumber(st, i)
                    if (dn != null) {
                        val monthIdx = i + dn.second
                        val month = monthWords[st.at(monthIdx) ?: ""]
                        if (month != null) {
                            var len = dn.second + 1
                            if (st.at(i + len) == "ماه") len++
                            var year: Int? = null
                            val yt = st.at(i + len)
                            if (yt != null && Regex("^1[34]\\d{2}$").matches(yt)) { year = yt.toInt(); len++ }
                            val nowJ = JalaliCalendar.millisToJalali(now)
                            var y = year ?: nowJ.year
                            val day = minOf(dn.first, JalaliCalendar.daysInJalaliMonth(y, month))
                            var base = startOfDay(JalaliCalendar.jalaliToMillis(y, month, day, 0, 0))
                            if (year == null && base.before(today)) {
                                y += 1
                                val d2 = minOf(dn.first, JalaliCalendar.daysInJalaliMonth(y, month))
                                base = startOfDay(JalaliCalendar.jalaliToMillis(y, month, d2, 0, 0))
                            }
                            spec.base = base
                            st.mark(i, len)
                        }
                    }
                }
            }
        }
        return spec
    }

    // ───────────────────────── ساعت ─────────────────────────

    private class TimeSpec(
        var hour: Int? = null,
        var minute: Int = 0,
        var delta: Int = 0,
        var period: String? = null
    )

    private fun takePeriodAround(st: State, from: Int, to: Int, spec: TimeSpec) {
        val after = st.at(to)
        if (after in periods) { spec.period = after; st.mark(to, 1); return }
        val before = st.at(from - 1)
        if (before in periods) { spec.period = before; st.mark(from - 1, 1) }
    }

    private fun parseTime(st: State, presetPeriod: String?): TimeSpec {
        val spec = TimeSpec(period = presetPeriod)
        val clock = Regex("^(\\d{1,2}):(\\d{2})$")

        // ۱) «۸:۳۰» ، «ساعت ۸:۳۰»
        for (i in st.tokens.indices) {
            val m = clock.find(st.at(i) ?: continue) ?: continue
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            if (h > 24 || mi > 59) continue
            spec.hour = h % 24; spec.minute = mi
            var from = i
            if (st.at(i - 1) == "ساعت") from = i - 1
            st.mark(from, i - from + 1)
            takePeriodAround(st, from, i + 1, spec)
            return spec
        }

        // ۲) «ساعت N [و نیم | و ربع | و M [دقیقه]]»
        for (i in st.tokens.indices) {
            if (st.at(i) != "ساعت") continue
            val (h, len) = number(st, i + 1) ?: continue
            if (h > 24) continue
            var end = i + 1 + len
            var minute = 0
            if (st.at(end) == "و") {
                when (val nx = st.at(end + 1)) {
                    "نیم" -> { minute = 30; end += 2 }
                    "ربع" -> { minute = 15; end += 2 }
                    else -> if (nx != null) {
                        val mm = number(st, end + 1)
                        if (mm != null && mm.first in 1..59) {
                            minute = mm.first
                            end += 1 + mm.second
                            if (st.at(end) == "دقیقه") end++
                        }
                    }
                }
            }
            spec.hour = h % 24; spec.minute = minute
            st.mark(i, end - i)
            takePeriodAround(st, i, end, spec)
            return spec
        }

        // ۳) «ربع به ۹» ، «ده دقیقه به ۹»
        for (i in st.tokens.indices) {
            if (!st.free(i)) continue
            var delta: Int? = null
            var len = 0
            if (st.at(i) == "ربع") { delta = -15; len = 1 }
            else {
                val n = number(st, i)
                if (n != null && st.at(i + n.second) == "دقیقه") { delta = -n.first; len = n.second + 1 }
            }
            if (delta == null || st.at(i + len) != "به") continue
            val (h, hl) = number(st, i + len + 1) ?: continue
            if (h !in 1..24) continue
            spec.hour = h % 24; spec.delta = delta
            val end = i + len + 1 + hl
            st.mark(i, end - i)
            takePeriodAround(st, i, end, spec)
            return spec
        }

        // ۴) «۸ صبح» ، «۱۰ شب» (عدد درست کنار بخش روز)
        for (i in st.tokens.indices) {
            val (h, len) = number(st, i) ?: continue
            val p = st.at(i + len) ?: continue
            if (p in periods && h <= 24) {
                spec.hour = h % 24; spec.period = p
                st.mark(i, len + 1)
                return spec
            }
        }

        // ۵) فقط «صبح / ظهر / عصر / شب …»
        for (i in st.tokens.indices) {
            val t = st.at(i) ?: continue
            if (t in periods) { spec.period = t; st.mark(i, 1); break }
        }
        return spec
    }

    /** ساعت N (۱ تا ۱۲) با توجه به «صبح/ظهر/عصر/شب» به ساعت ۲۴ ساعته. */
    private fun applyPeriod(n: Int, period: String): Int = when (period) {
        "صبح", "بامداد" -> if (n == 12) 0 else n
        "ظهر" -> if (n in 1..4) n + 12 else n
        "بعدازظهر", "عصر" -> if (n < 12) n + 12 else n
        "شب" -> when { n == 12 -> 0; n in 6..11 -> n + 12; else -> n }
        "نیمهشب" -> if (n == 12) 0 else n
        else -> n
    }

    // ───────────────────────── خروجی ─────────────────────────

    fun parse(rawText: String, now: Long = System.currentTimeMillis()): VoiceParseResult {
        val text = normalize(rawText)
        if (text.isBlank()) return VoiceParseResult.Problem("چیزی نشنیدم. دوباره امتحان کن.", "")

        // دسته‌بندی از روی کل جمله
        val category = categoryKeywords.firstOrNull { (_, words) -> words.any { text.contains(it) } }?.first
            ?: "عمومی"

        var cleaned = text
        for (re in fillerRegexes) cleaned = re.replace(cleaned, " ")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        val st = State(cleaned.split(" ").filter { it.isNotEmpty() })

        val repeat = parseRepeat(st)
        val dur = parseDuration(st)
        val periodBox = arrayOfNulls<String>(1)
        val date = if (dur == null || dur.days > 0 || dur.months > 0) parseDate(st, now, periodBox) else DateSpec()
        val time = if (dur != null && dur.days == 0 && dur.months == 0) TimeSpec() else parseTime(st, periodBox[0])

        val title = buildTitle(st).ifBlank { if (category != "عمومی") category else "یادآوری صوتی" }

        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val hasTime = time.hour != null || time.period != null
        val hasDur = dur != null
        val hasRepeat = repeat.interval != RepeatInterval.NONE
        if (date.base == null && !hasTime && !hasDur && !hasRepeat) {
            return VoiceParseResult.Problem(
                "زمان یادآوری رو متوجه نشدم. مثلاً بگو: فردا ساعت هشت صبح $title",
                cleaned
            )
        }

        val target: Calendar
        if (dur != null && dur.days == 0 && dur.months == 0) {
            target = (nowCal.clone() as Calendar).apply {
                add(Calendar.MINUTE, dur.minutes)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
        } else {
            var baseDate: Calendar? = date.base
            if (dur != null) {
                var b = startOfDay(now)
                if (dur.months > 0) b = startOfDay(JalaliCalendar.addMonths(b.timeInMillis, dur.months))
                b.add(Calendar.DAY_OF_YEAR, dur.days)
                baseDate = b
            }
            if (baseDate == null && repeat.dow != null) {
                val today = startOfDay(now)
                val diff = (repeat.dow!! - today.get(Calendar.DAY_OF_WEEK) + 7) % 7
                val b = today.clone() as Calendar
                b.add(Calendar.DAY_OF_YEAR, diff)
                baseDate = b
            }
            val explicitDate = baseDate != null
            val base = (baseDate ?: startOfDay(now)).clone() as Calendar
            val farDate = explicitDate && base.after(startOfDay(now))

            val hour: Int
            var minute = time.minute
            val h = time.hour
            if (h != null) {
                hour = when {
                    h == 0 || h >= 13 -> h
                    time.period != null -> applyPeriod(h, time.period!!)
                    farDate -> if (h in 1..6) h + 12 else h
                    h == 12 -> 12
                    else -> {
                        // امروز یا بدون تاریخ: نزدیک‌ترین زمان آینده
                        val am = (base.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, minute) }
                        if (am.timeInMillis > now) h
                        else {
                            val pm = (base.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, h + 12); set(Calendar.MINUTE, minute) }
                            if (pm.timeInMillis > now) h + 12 else if (h in 1..6) h + 12 else h
                        }
                    }
                }
            } else {
                hour = periodDefaultHour[time.period ?: ""] ?: 9
                minute = 0
            }
            target = (base.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (time.delta != 0) add(Calendar.MINUTE, time.delta)
            }
            if (target.timeInMillis <= now) {
                if (explicitDate && (date.isToday || !farDate) && repeat.dow == null && !hasRepeat) {
                    return VoiceParseResult.Problem(
                        "این زمان گذشته. یه زمان بعدی بگو.", cleaned
                    )
                }
                // بدون تاریخ (یا تکرار روزانه) یعنی نوبت بعدی
                target.add(Calendar.DAY_OF_YEAR, if (repeat.interval == RepeatInterval.WEEKLY && repeat.dow != null) 7 else 1)
            }
        }
        val dowCsv = repeat.dow?.toString()
        val whenText = describe(target, now, repeat, spoken = false)
        val whenSpoken = describe(target, now, repeat, spoken = true)
        return VoiceParseResult.Ok(
            VoiceReminder(
                title = title,
                triggerAtMillis = target.timeInMillis,
                category = category,
                repeat = repeat.interval,
                repeatDaysOfWeek = dowCsv,
                whenText = whenText,
                whenSpoken = whenSpoken
            )
        )
    }

    private fun buildTitle(st: State): String {
        val words = st.tokens.filterIndexed { i, _ -> !st.used[i] }.toMutableList()
        while (words.isNotEmpty() && words.first() in edgeStopWords) words.removeAt(0)
        while (words.isNotEmpty() && words.last() in edgeStopWords) words.removeAt(words.size - 1)
        return words.joinToString(" ")
    }

    // ───────────────────────── توضیح زمان ─────────────────────────

    /** «ساعت هشت و نیم صبح» — برای خوندن با صدا. */
    fun spokenClock(millis: Long): String =
        clockPhrase(Calendar.getInstance().apply { timeInMillis = millis }, spoken = true)

    private fun clockPhrase(target: Calendar, spoken: Boolean): String {
        fun num(n: Int) = if (spoken) PersianWords.of(n) else JalaliCalendar.toPersianDigits(n.toString())
        fun hourAndPeriod(h: Int): Pair<Int, String> = when {
            h == 0 -> 12 to "شب"
            h < 12 -> h to "صبح"
            h == 12 -> 12 to "ظهر"
            h < 17 -> h - 12 to "بعدازظهر"
            h < 20 -> h - 12 to "عصر"
            else -> h - 12 to "شب"
        }
        val h = target.get(Calendar.HOUR_OF_DAY)
        val m = target.get(Calendar.MINUTE)
        val (h12, period) = hourAndPeriod(h)
        return when (m) {
            0 -> "ساعت ${num(h12)} $period"
            30 -> "ساعت ${num(h12)} و نیم $period"
            15 -> "ساعت ${num(h12)} و ربع $period"
            45 -> {
                val (nh, np) = hourAndPeriod((h + 1) % 24)
                "ربع به ${num(nh)} $np"
            }
            else -> "ساعت ${num(h12)} و ${num(m)} دقیقه $period"
        }
    }

    private fun describe(target: Calendar, now: Long, repeat: RepeatSpec, spoken: Boolean): String {
        fun num(n: Int) = if (spoken) PersianWords.of(n) else JalaliCalendar.toPersianDigits(n.toString())
        val clock = clockPhrase(target, spoken)

        val today = startOfDay(now)
        val day = startOfDay(target.timeInMillis)
        val diffDays = Math.round((day.timeInMillis - today.timeInMillis) / 86_400_000.0).toInt()
        val j = JalaliCalendar.millisToJalali(target.timeInMillis)
        val dayLabel = when (diffDays) {
            0 -> "امروز"
            1 -> "فردا"
            2 -> "پس‌فردا"
            else -> "${weekdayNames[target.get(Calendar.DAY_OF_WEEK)]} ${num(j.day)} ${JalaliCalendar.monthNames[j.month - 1]}"
        }
        val weekday = weekdayNames[target.get(Calendar.DAY_OF_WEEK)] ?: ""
        return when (repeat.interval) {
            RepeatInterval.NONE -> "$dayLabel، $clock"
            RepeatInterval.DAILY -> "هر روز، $clock"
            RepeatInterval.WEEKLY ->
                if (repeat.dow != null) "هر $weekday، $clock" else "هر هفته ($dayLabel)، $clock"
            RepeatInterval.MONTHLY -> "هر ماه، از $dayLabel، $clock"
            RepeatInterval.YEARLY -> "هر سال، از $dayLabel، $clock"
        }
    }
}
