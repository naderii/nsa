package ir.naderinia.nsa.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.data.effectiveAnchorDay
import ir.naderinia.nsa.data.isRecurringBill
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.util.PermissionStatus
import java.util.Calendar

/**
 * Wraps AlarmManager to schedule exact, wake-from-idle alarms per reminder.
 * We deliberately use setExactAndAllowWhileIdle (not WorkManager) because
 * WorkManager's minimum granularity and Doze batching make it unsuitable
 * for "fire at this exact minute" reminders.
 */
object NotificationScheduler {

    fun schedule(context: Context, reminder: Reminder) {
        // AlarmManager fires an alarm set in the past IMMEDIATELY. Saving / editing / restoring an
        // overdue reminder must not make the phone ring right now: it simply stays «عقب‌افتاده».
        if (reminder.triggerAtMillis <= System.currentTimeMillis()) {
            cancel(context, reminder.id)
            return
        }
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

        setAlarm(context, alarmManager, reminder.triggerAtMillis, pendingIntent)
    }

    /**
     * Exact alarm when allowed. On Android 12+ the user can revoke "alarms & reminders";
     * calling the exact API then throws SecurityException and would crash the app while saving.
     * Fall back to an inexact (but still Doze-safe) alarm instead of crashing.
     */
    internal fun setAlarm(context: Context, alarmManager: AlarmManager, atMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (PermissionStatus.canScheduleExactAlarms(context)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntent)
            } catch (_: Exception) {
                // never crash the app because an alarm could not be armed
            }
        } catch (e: IllegalStateException) {
            // Android allows an app at most 500 pending alarms; many installments can reach it.
            // Skipping one alarm is better than crashing while saving.
        }
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
        cancelSnooze(context, reminderId)
        // deleted / edited / finished from the app: no more escalation re-alerts either
        EscalationManager.acknowledge(context, reminderId)
    }

    // ───────────── Snooze (one-shot re-alert that doesn't touch the reminder's own schedule) ─────────────

    private const val SNOOZE_PREFS = "snooze_prefs"

    private fun snoozePrefs(context: Context) =
        context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE)

    private fun snoozeFirePendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_SNOOZE_FIRE
            // data makes this PendingIntent distinct from the regular alarm of the same reminder
            data = Uri.parse("nsa://reminder/$reminderId/snooze_fire")
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Re-alerts at [atMillis] without changing the reminder's repeat schedule. Survives reboot. */
    fun scheduleSnooze(context: Context, reminderId: Long, atMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        snoozePrefs(context).edit().putLong("s_$reminderId", atMillis).apply()
        setAlarm(context, alarmManager, atMillis, snoozeFirePendingIntent(context, reminderId))
    }

    fun cancelSnooze(context: Context, reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(snoozeFirePendingIntent(context, reminderId))
        clearSnoozeRecord(context, reminderId)
    }

    fun clearSnoozeRecord(context: Context, reminderId: Long) {
        snoozePrefs(context).edit().remove("s_$reminderId").apply()
    }

    /** Called after boot: AlarmManager forgets alarms on reboot, so re-arm pending snoozes. */
    fun rescheduleSnoozes(context: Context) {
        val prefs = snoozePrefs(context)
        val now = System.currentTimeMillis()
        prefs.all.forEach { (key, value) ->
            val at = value as? Long ?: return@forEach
            val id = key.removePrefix("s_").toLongOrNull() ?: return@forEach
            if (at > now) scheduleSnooze(context, id, at) else clearSnoozeRecord(context, id)
        }
    }

    /** Computes the next trigger time for a repeating reminder, given the one that just fired. */
    fun nextOccurrence(
        currentTriggerMillis: Long,
        interval: RepeatInterval,
        repeatDaysOfWeek: String? = null,
        anchorDay: Int? = null
    ): Long? {
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
            // Jalali months/years, so "1 مهر" comes back as "1 آبان" (Gregorian steps drift by a day or two).
            RepeatInterval.MONTHLY -> return JalaliCalendar.addMonths(currentTriggerMillis, 1, anchorDay)
            RepeatInterval.YEARLY -> return JalaliCalendar.addMonths(currentTriggerMillis, 12, anchorDay)
            else -> {}
        }
        return cal.timeInMillis
    }

    /**
     * Like [nextOccurrence] but never returns a time in the past: if the phone was off, or the
     * alarm was delayed for days, keep stepping until the result is in the future. Without this
     * a repeating reminder is left with a past trigger and fires a burst of catch-up alarms.
     */
    fun nextFutureOccurrence(
        currentTriggerMillis: Long,
        interval: RepeatInterval,
        repeatDaysOfWeek: String? = null,
        anchorDay: Int? = null
    ): Long? {
        val now = System.currentTimeMillis()
        var next = nextOccurrence(currentTriggerMillis, interval, repeatDaysOfWeek, anchorDay) ?: return null
        var guard = 0
        while (next <= now && guard < 2000) {
            next = nextOccurrence(next, interval, repeatDaysOfWeek, anchorDay) ?: return next
            guard++
        }
        return next
    }

    /**
     * Weekly reminder with chosen weekdays: the first alarm must land on one of THOSE days.
     * (Picked date = Wednesday but only Saturday/Monday chosen → move to Saturday, same time.)
     */
    fun alignToWeekdays(millis: Long, repeatInterval: RepeatInterval, repeatDaysOfWeek: String?): Long {
        if (repeatInterval != RepeatInterval.WEEKLY) return millis
        val days = parseDaysOfWeek(repeatDaysOfWeek)
        if (days.isEmpty()) return millis
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        repeat(7) {
            if (cal.get(Calendar.DAY_OF_WEEK) in days) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return millis
    }

    /**
     * Re-arms every alarm from the database. AlarmManager forgets alarms after a reboot, after the
     * app is force-stopped (battery managers do this), and after a restore on a new phone — so this
     * runs on boot AND whenever the app is opened.
     * A repeating reminder that is already overdue by more than [overdueGraceMillis] is moved to its
     * next future occurrence (the grace keeps us away from an alarm that is being delivered right now).
     */
    suspend fun rescheduleAll(context: Context, overdueGraceMillis: Long = 0L) {
        val dao = AppDatabase.getInstance(context).reminderDao()
        val now = System.currentTimeMillis()
        dao.getAllOnce()
            .filter { !it.isDone }
            .forEach { reminder ->
                when {
                    reminder.triggerAtMillis > now -> schedule(context, reminder)

                    reminder.repeatInterval != RepeatInterval.NONE &&
                        !reminder.isRecurringBill() &&
                        reminder.triggerAtMillis < now - overdueGraceMillis -> {
                        val anchor = reminder.effectiveAnchorDay()
                        val next = nextFutureOccurrence(
                            reminder.triggerAtMillis,
                            reminder.repeatInterval,
                            reminder.repeatDaysOfWeek,
                            anchor
                        )
                        if (next != null) {
                            val updated = reminder.copy(triggerAtMillis = next, repeatAnchorDay = anchor)
                            dao.update(updated)
                            schedule(context, updated)
                        }
                    }
                    // One-time overdue reminders stay as they are (shown as «عقب‌افتاده»).
                }
            }
        rescheduleSnoozes(context)
        EscalationManager.rescheduleAfterBoot(context)
        FinanceDigest.reschedule(context)
    }

    private fun parseDaysOfWeek(csv: String?): Set<Int> =
        csv?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
}
