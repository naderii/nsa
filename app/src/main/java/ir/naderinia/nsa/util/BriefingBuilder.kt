package ir.naderinia.nsa.util

import ir.naderinia.nsa.data.Reminder
import java.util.Calendar

/** متن «خلاصه‌ی روز» که با صدا خونده می‌شه (اعداد به حروف، جمله‌های کوتاه و واضح). */
object BriefingBuilder {

    private const val MAX_LISTED = 6

    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun count(n: Int) = PersianWords.of(n)

    fun daily(reminders: List<Reminder>, now: Long = System.currentTimeMillis()): String {
        val dayStart = startOfDay(now)
        val dayEnd = dayStart + 86_400_000L
        val weekEnd = dayStart + 8 * 86_400_000L

        val open = reminders.filter { !it.isDone }
        val overdue = open.filter { it.triggerAtMillis < dayStart }.sortedBy { it.triggerAtMillis }
        val today = open.filter { it.triggerAtMillis in dayStart until dayEnd }.sortedBy { it.triggerAtMillis }
        val doneToday = reminders.count { it.isDone && it.triggerAtMillis in dayStart until dayEnd }
        val moneySoon = open.count { it.financialType != null && it.triggerAtMillis in dayEnd until weekEnd }

        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 4..11 -> "صبح بخیر"
            in 12..16 -> "ظهر بخیر"
            in 17..19 -> "عصر بخیر"
            else -> "شب بخیر"
        }
        val j = JalaliCalendar.millisToJalali(now)
        val weekday = when (Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY -> "شنبه"
            Calendar.SUNDAY -> "یکشنبه"
            Calendar.MONDAY -> "دوشنبه"
            Calendar.TUESDAY -> "سه‌شنبه"
            Calendar.WEDNESDAY -> "چهارشنبه"
            Calendar.THURSDAY -> "پنجشنبه"
            else -> "جمعه"
        }

        val sb = StringBuilder()
        sb.append("$greeting. امروز $weekday ${PersianWords.of(j.day)} ${JalaliCalendar.monthNames[j.month - 1]} است. ")

        if (overdue.isEmpty() && today.isEmpty()) {
            sb.append("امروز کار باقی‌مانده‌ای نداری. ")
        }

        if (overdue.isNotEmpty()) {
            sb.append("${count(overdue.size)} کار عقب‌افتاده داری: ")
            sb.append(overdue.take(3).joinToString("، ") { it.title })
            if (overdue.size > 3) sb.append("، و ${count(overdue.size - 3)} مورد دیگه")
            sb.append(". ")
        }

        if (today.isNotEmpty()) {
            sb.append(
                if (today.size == 1) "برای امروز یک کار داری. "
                else "برای امروز ${count(today.size)} کار داری. "
            )
            today.take(MAX_LISTED).forEach {
                sb.append("${VoiceReminderParser.spokenClock(it.triggerAtMillis)}، ${it.title}. ")
            }
            if (today.size > MAX_LISTED) sb.append("و ${count(today.size - MAX_LISTED)} مورد دیگه. ")
        } else {
            val next = open.filter { it.triggerAtMillis >= dayEnd }.minByOrNull { it.triggerAtMillis }
            if (next != null) {
                val nj = JalaliCalendar.millisToJalali(next.triggerAtMillis)
                val days = ((startOfDay(next.triggerAtMillis) - dayStart) / 86_400_000L).toInt()
                val whenWord = when (days) {
                    1 -> "فردا"
                    2 -> "پس‌فردا"
                    else -> "${PersianWords.of(nj.day)} ${JalaliCalendar.monthNames[nj.month - 1]}"
                }
                sb.append("کار بعدی‌ات $whenWord، ${VoiceReminderParser.spokenClock(next.triggerAtMillis)}: ${next.title}. ")
            }
        }

        if (doneToday > 0) sb.append("${count(doneToday)} کار امروزت رو انجام دادی. آفرین! ")
        if (moneySoon > 0) sb.append("${count(moneySoon)} مورد مالی هم تا یک هفته‌ی دیگه سررسید داری. ")

        return sb.toString().trim()
    }

    /** متن کوتاهی که موقع اعلان خونده می‌شه. */
    fun forNotification(title: String, note: String): String =
        if (note.isBlank()) "یادآوری: $title" else "یادآوری: $title. ${note.take(160)}"
}
