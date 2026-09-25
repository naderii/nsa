package ir.naderinia.nsa.util

import java.util.Calendar

data class JalaliDate(val year: Int, val month: Int, val day: Int)

/**
 * Pure-Kotlin Gregorian↔Jalali conversion (no external library, no network).
 * Port of the well-known "jalaali-js" algorithm (Pournader/Toossi), which is
 * the astronomically accurate version used across nearly every serious
 * Jalali-calendar implementation (Python's jdatetime, PHP jalali, etc).
 */
object JalaliCalendar {

    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    )

    val monthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    private data class JalCalResult(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): JalCalResult {
        val bl = breaks.size
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        var i = 1
        while (i < bl) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + ((jump % 33) / 4)
            jp = jm
            i++
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33 + 3) / 4)
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = ((n + 1) % 33 - 1) % 4
        if (leap == -1) leap = 4
        return JalCalResult(leap, gy, march)
    }

    fun isLeapJalaliYear(jy: Int): Boolean = jalCal(jy).leap == 0

    fun daysInJalaliMonth(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (isLeapJalaliYear(jy)) 30 else 29
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Long {
        var d = ((gy + (gm - 8) / 6 + 100100).toLong() * 1461) / 4 +
            (153L * ((gm + 9) % 12) + 2) / 5 +
            gd - 34840408L
        d -= (((gy + 100100 + (gm - 8) / 6) / 100).toLong() * 3) / 4
        d += 752
        return d
    }

    private fun d2g(jdn: Long): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139361631L
        j += (((4 * jdn + 183187720L) / 146097L) * 3 / 4) * 4 - 3908
        val i = ((j % 1461) / 4) * 5 + 308
        val gd = ((i % 153) / 5 + 1).toInt()
        val gm = ((i / 153) % 12 + 1).toInt()
        val gy = (j / 1461 - 100100 + (8 - gm) / 6).toInt()
        return Triple(gy, gm, gd)
    }

    private fun jalaliToJdn(jy: Int, jm: Int, jd: Int): Long {
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1
    }

    private fun jdnToJalali(jdn: Long): JalaliDate {
        val gy0 = d2g(jdn).first
        var jy = gy0 - 621
        val r = jalCal(jy)
        val jdn1f = g2d(r.gy, 3, r.march)
        var k = jdn - jdn1f
        val jm: Int
        val jd: Int
        if (k >= 0) {
            if (k <= 185) {
                return JalaliDate(jy, 1 + (k / 31).toInt(), (k % 31).toInt() + 1)
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        jm = 7 + (k / 30).toInt()
        jd = (k % 30).toInt() + 1
        return JalaliDate(jy, jm, jd)
    }

    fun millisToJalali(millis: Long): JalaliDate {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val jdn = g2d(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        return jdnToJalali(jdn)
    }

    /** Converts a Jalali date + time-of-day into epoch millis (Gregorian under the hood). */
    fun jalaliToMillis(jy: Int, jm: Int, jd: Int, hour: Int, minute: Int): Long {
        val jdn = jalaliToJdn(jy, jm, jd)
        val (gy, gm, gd) = d2g(jdn)
        val cal = Calendar.getInstance()
        cal.set(gy, gm - 1, gd, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private val persianDigitChars = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun toPersianDigits(input: String): String =
        input.map { c -> if (c.isDigit()) persianDigitChars[c - '0'] else c }.joinToString("")

    fun formatDate(millis: Long): String {
        val d = millisToJalali(millis)
        return toPersianDigits("%04d/%02d/%02d".format(d.year, d.month, d.day))
    }

    fun formatDateTime(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val time = toPersianDigits("%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
        return "${formatDate(millis)} - $time"
    }
}
