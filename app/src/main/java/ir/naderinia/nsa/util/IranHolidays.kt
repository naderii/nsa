package ir.naderinia.nsa.util

import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.util.Calendar

/**
 * Iran's official non-working days, fully offline.
 *
 *  • Solar holidays (Nowruz, 12/13 Farvardin, 14/15 Khordad, 22 Bahman, 29 Esfand) are the same every
 *    Jalali year, so they are exact for any year.
 *  • Religious (lunar) holidays move ~11 days a year and depend on moon sighting. For years listed in
 *    [LUNAR_TABLE] the dates are the OFFICIAL calendar; for any other year they are computed from the
 *    Islamic calendar and flagged `approximate` (can be off by a day) — the UI says so.
 *
 * ⇒ Once a year (around Bahman/Esfand) add the next year's official lunar dates to [LUNAR_TABLE].
 *
 * Every Friday is a day off. Thursday is a working day for most offices but most BANK branches are
 * closed, so [isBankDay] treats Thursday as closed (this is what matters for cheques / installments).
 */
object IranHolidays {

    enum class Kind { WORKING, THURSDAY, FRIDAY, HOLIDAY }

    data class DayStatus(val kind: Kind, val name: String? = null, val approximate: Boolean = false) {
        /** Closed for government / most offices (holiday or Friday). */
        val isOffDay: Boolean get() = kind == Kind.HOLIDAY || kind == Kind.FRIDAY
    }

    data class Holiday(val jy: Int, val jm: Int, val jd: Int, val name: String, val approximate: Boolean)

    // (month, day, name) — identical every year
    private val SOLAR = listOf(
        Triple(1, 1, "نوروز"), Triple(1, 2, "نوروز"), Triple(1, 3, "نوروز"), Triple(1, 4, "نوروز"),
        Triple(1, 12, "روز جمهوری اسلامی"), Triple(1, 13, "سیزده‌به‌در"),
        Triple(3, 14, "رحلت امام خمینی"), Triple(3, 15, "قیام ۱۵ خرداد"),
        Triple(11, 22, "پیروزی انقلاب اسلامی"), Triple(12, 29, "ملی شدن صنعت نفت")
    )

    /** Official religious holidays per Jalali year: (month, day, name). Verified against the 1405 official calendar. */
    private val LUNAR_TABLE: Map<Int, List<Triple<Int, Int, String>>> = mapOf(
        1405 to listOf(
            Triple(1, 1, "عید سعید فطر"),
            Triple(1, 2, "تعطیل به مناسبت عید فطر"),
            Triple(1, 25, "شهادت امام جعفر صادق (ع)"),
            Triple(3, 6, "عید سعید قربان"),
            Triple(3, 14, "عید سعید غدیر خم"),
            Triple(4, 3, "تاسوعای حسینی"),
            Triple(4, 4, "عاشورای حسینی"),
            Triple(5, 13, "اربعین حسینی"),
            Triple(5, 21, "رحلت پیامبر (ص) و شهادت امام حسن (ع)"),
            Triple(5, 22, "شهادت امام رضا (ع)"),
            Triple(5, 30, "شهادت امام حسن عسکری (ع)"),
            Triple(6, 8, "میلاد پیامبر (ص) و امام صادق (ع)"),
            Triple(8, 22, "شهادت حضرت فاطمه (س)"),
            Triple(10, 2, "ولادت امام علی (ع) و روز پدر"),
            Triple(10, 16, "مبعث پیامبر (ص)"),
            Triple(11, 4, "ولادت امام زمان (عج)"),
            Triple(12, 9, "شهادت امام علی (ع)"),
            Triple(12, 19, "عید سعید فطر"),
            Triple(12, 20, "تعطیل به مناسبت عید فطر")
        )
    )

    // (hijri month, hijri day or 0 = last day of month, name)
    private val LUNAR_RULES = listOf(
        Triple(1, 9, "تاسوعای حسینی"), Triple(1, 10, "عاشورای حسینی"),
        Triple(2, 20, "اربعین حسینی"), Triple(2, 28, "رحلت پیامبر (ص) و شهادت امام حسن (ع)"),
        Triple(2, 0, "شهادت امام رضا (ع)"),
        Triple(3, 8, "شهادت امام حسن عسکری (ع)"), Triple(3, 17, "میلاد پیامبر (ص) و امام صادق (ع)"),
        Triple(6, 3, "شهادت حضرت فاطمه (س)"),
        Triple(7, 13, "ولادت امام علی (ع) و روز پدر"), Triple(7, 27, "مبعث پیامبر (ص)"),
        Triple(8, 15, "ولادت امام زمان (عج)"),
        Triple(9, 21, "شهادت امام علی (ع)"),
        Triple(10, 1, "عید سعید فطر"), Triple(10, 2, "تعطیل به مناسبت عید فطر"),
        Triple(10, 25, "شهادت امام جعفر صادق (ع)"),
        Triple(12, 10, "عید سعید قربان"), Triple(12, 18, "عید سعید غدیر خم")
    )

    private val cache = HashMap<Int, Map<Int, Holiday>>() // jalali year → (month*100+day → holiday)

    @Synchronized
    private fun year(jy: Int): Map<Int, Holiday> = cache.getOrPut(jy) { build(jy) }

    private fun key(jm: Int, jd: Int) = jm * 100 + jd

    private fun build(jy: Int): Map<Int, Holiday> {
        val out = LinkedHashMap<Int, Holiday>()
        fun add(jm: Int, jd: Int, name: String, approx: Boolean) {
            val k = key(jm, jd)
            val old = out[k]
            out[k] = if (old == null) Holiday(jy, jm, jd, name, approx)
            else if (old.name == name) old
            else old.copy(name = "${old.name} و $name", approximate = old.approximate || approx)
        }
        SOLAR.forEach { (m, d, n) -> add(m, d, n, false) }

        val official = LUNAR_TABLE[jy]
        if (official != null) {
            official.forEach { (m, d, n) -> add(m, d, n, false) }
        } else {
            approximateLunar(jy).forEach { (m, d, n) -> add(m, d, n, true) }
        }
        return out
    }

    /** Religious holidays computed from the Islamic (Umm al-Qura) calendar for a year with no official table. */
    private fun approximateLunar(jy: Int): List<Triple<Int, Int, String>> {
        val result = ArrayList<Triple<Int, Int, String>>()
        val startMillis = JalaliCalendar.jalaliToMillis(jy, 1, 1, 12, 0)
        val endMillis = JalaliCalendar.jalaliToMillis(jy, 12, JalaliCalendar.daysInJalaliMonth(jy, 12), 12, 0)
        val gStart = Calendar.getInstance().apply { timeInMillis = startMillis }.get(Calendar.YEAR)
        val hijriStartYear = try {
            HijrahDate.from(LocalDate.of(gStart, 1, 1)).get(java.time.temporal.ChronoField.YEAR)
        } catch (_: Exception) {
            return result
        }
        for (hy in (hijriStartYear - 1)..(hijriStartYear + 2)) {
            for ((hm, hd, name) in LUNAR_RULES) {
                try {
                    val len = HijrahDate.of(hy, hm, 1).lengthOfMonth()
                    val day = if (hd == 0) len else hd
                    if (day > len) continue
                    val g = LocalDate.from(HijrahDate.of(hy, hm, day))
                    val cal = Calendar.getInstance().apply {
                        clear(); set(g.year, g.monthValue - 1, g.dayOfMonth, 12, 0, 0)
                    }
                    if (cal.timeInMillis < startMillis - 86_400_000L || cal.timeInMillis > endMillis + 86_400_000L) continue
                    val j = JalaliCalendar.millisToJalali(cal.timeInMillis)
                    if (j.year == jy) result += Triple(j.month, j.day, name)
                } catch (_: Exception) {
                    // a Hijri year outside the supported range: skip
                }
            }
        }
        return result
    }

    // ───────────── public API ─────────────

    fun status(millis: Long): DayStatus {
        val j = JalaliCalendar.millisToJalali(millis)
        val h = year(j.year)[key(j.month, j.day)]
        if (h != null) return DayStatus(Kind.HOLIDAY, h.name, h.approximate)
        return when (Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)) {
            Calendar.FRIDAY -> DayStatus(Kind.FRIDAY, "جمعه")
            Calendar.THURSDAY -> DayStatus(Kind.THURSDAY, "پنجشنبه")
            else -> DayStatus(Kind.WORKING)
        }
    }

    fun isOffDay(millis: Long): Boolean = status(millis).isOffDay

    /** Banks are closed on holidays, Fridays and (for most branches) Thursdays. */
    fun isBankDay(millis: Long): Boolean = status(millis).kind == Kind.WORKING

    /** Same time of day, moved back to the nearest bank day (never more than 10 days, just a safety stop). */
    fun previousBankDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        var guard = 0
        do {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            guard++
        } while (!isBankDay(cal.timeInMillis) && guard < 10)
        return cal.timeInMillis
    }

    fun nextBankDay(millis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        var guard = 0
        do {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            guard++
        } while (!isBankDay(cal.timeInMillis) && guard < 10)
        return cal.timeInMillis
    }

    /** Official holidays (not plain Fridays) from [fromMillis] on, soonest first, as (day-start millis, holiday). */
    fun upcoming(fromMillis: Long, count: Int): List<Pair<Long, Holiday>> {
        val out = ArrayList<Pair<Long, Holiday>>()
        val cal = Calendar.getInstance().apply {
            timeInMillis = fromMillis
            set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        var guard = 0
        while (out.size < count && guard < 800) {
            val j = JalaliCalendar.millisToJalali(cal.timeInMillis)
            year(j.year)[key(j.month, j.day)]?.let { out += cal.timeInMillis to it }
            cal.add(Calendar.DAY_OF_YEAR, 1)
            guard++
        }
        return out
    }

    /** True when this Jalali year's religious dates are official (not computed). */
    fun isOfficialYear(jy: Int): Boolean = LUNAR_TABLE.containsKey(jy)

    /** One-line explanation for a financial item due on a closed day, or null when it is a normal bank day. */
    fun dueWarning(millis: Long): String? {
        val s = status(millis)
        return when (s.kind) {
            Kind.HOLIDAY -> "سررسید روی تعطیل رسمی (${s.name}) افتاده" + if (s.approximate) " — تاریخ تعطیلات مذهبی این سال تقریبی است" else ""
            Kind.FRIDAY -> "سررسید روی جمعه افتاده و بانک‌ها بسته‌اند"
            Kind.THURSDAY -> "سررسید روی پنجشنبه است و بیشتر شعبه‌های بانک بسته‌اند"
            Kind.WORKING -> null
        }
    }
}
