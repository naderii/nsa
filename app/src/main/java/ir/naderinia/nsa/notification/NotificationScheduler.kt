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
    fun nextOccurrence(currentTriggerMillis: Long, interval: RepeatInterval): Long? {
        if (interval == RepeatInterval.NONE) return null
        val cal = Calendar.getInstance().apply { timeInMillis = currentTriggerMillis }
        when (interval) {
            RepeatInterval.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RepeatInterval.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RepeatInterval.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatInterval.YEARLY -> cal.add(Calendar.YEAR, 1)
            RepeatInterval.NONE -> {}
        }
        return cal.timeInMillis
    }
}
