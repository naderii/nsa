package ir.naderinia.nsa.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import java.util.Calendar

/**
 * Wraps AlarmManager to schedule exact, wake-from-idle alarms per reminder.
 * We deliberately use setExactAndAllowWhileIdle (not WorkManager) because
 * WorkManager's minimum granularity and Doze batching make it unsuitable
 * for "fire at this exact minute" reminders.
 */
object NotificationScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            reminder.triggerAtMillis,
            pendingIntent
        )
    }

    fun cancel(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /** Computes the next trigger time for a repeating reminder, given the one that just fired. */
    fun nextOccurrence(currentTriggerMillis: Long, interval: RepeatInterval, repeatDaysOfWeek: String? = null): Long? {
        if (interval == RepeatInterval.NONE) return null
        val cal = Calendar.getInstance().apply { timeInMillis = currentTriggerMillis }

        if (interval == RepeatInterval.WEEKLY) {
            val selectedDays = parseDaysOfWeek(repeatDaysOfWeek)
            if (selectedDays.isNotEmpty()) {
                // Step forward day by day (never more than 7 tries) until we
                // land on one of the selected weekdays, keeping the same time-of-day.
                repeat(7) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    if (cal.get(Calendar.DAY_OF_WEEK) in selectedDays) return cal.timeInMillis
                }
                return cal.timeInMillis
            }
            cal.add(Calendar.WEEK_OF_YEAR, 1)
            return cal.timeInMillis
        }

        when (interval) {
            RepeatInterval.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RepeatInterval.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatInterval.YEARLY -> cal.add(Calendar.YEAR, 1)
            else -> {}
        }
        return cal.timeInMillis
    }

    private fun parseDaysOfWeek(csv: String?): Set<Int> =
        csv?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
}
