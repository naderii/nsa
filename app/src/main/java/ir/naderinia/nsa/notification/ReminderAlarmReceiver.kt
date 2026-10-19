package ir.naderinia.nsa.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.data.isCritical
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.data.effectiveAnchorDay
import ir.naderinia.nsa.data.isRecurringBill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"

        /** One-shot re-alert created by "snooze" on a repeating reminder. */
        const val ACTION_SNOOZE_FIRE = "ir.naderinia.nsa.action.SNOOZE_FIRE"

        /** Repeat of a «حیاتی» alert nobody reacted to (see EscalationManager). */
        const val ACTION_ESCALATE = EscalationManager.ACTION_ESCALATE

        const val SNOOZE_SHORT_MINUTES = 10
        const val SNOOZE_LONG_MINUTES = 60
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return
        val isSnoozeFire = intent.action == ACTION_SNOOZE_FIRE
        val isEscalation = intent.action == ACTION_ESCALATE

        // Go async so we can do a suspend DB read/write before the receiver dies.
        val pendingResult = goAsync()
        val dao = AppDatabase.getInstance(context).reminderDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = dao.getById(reminderId)
                if (reminder == null) {
                    // Reminder was deleted while a snooze / escalation was pending.
                    NotificationScheduler.clearSnoozeRecord(context, reminderId)
                    EscalationManager.acknowledge(context, reminderId)
                    return@launch
                }

                if (isEscalation) {
                    // A financial item that was fully paid meanwhile must not keep nagging.
                    val paid = reminder.financialType != null && reminder.isDone
                    if (paid) EscalationManager.acknowledge(context, reminderId)
                    else EscalationManager.onFire(context, reminder)
                    return@launch
                }

                if (reminder.isDone) {
                    // stale alarm of something already finished
                    NotificationScheduler.clearSnoozeRecord(context, reminderId)
                    return@launch
                }

                ReminderNotifier.show(context, reminder)
                if (reminder.isCritical()) EscalationManager.start(context, reminderId)

                if (isSnoozeFire) {
                    // Snooze re-alert only: the reminder's own schedule was already advanced.
                    NotificationScheduler.clearSnoozeRecord(context, reminderId)
                    return@launch
                }

                if (reminder.isRecurringBill()) {
                    // A repeating bill stays open (and shows as overdue) until it's paid;
                    // paying it rolls it to the next cycle (see BillCycle).
                } else if (reminder.repeatInterval != RepeatInterval.NONE) {
                    val next = NotificationScheduler.nextFutureOccurrence(
                        reminder.triggerAtMillis,
                        reminder.repeatInterval,
                        reminder.repeatDaysOfWeek,
                        reminder.effectiveAnchorDay()
                    )
                    if (next != null) {
                        val updated = reminder.copy(
                            triggerAtMillis = next,
                            repeatAnchorDay = reminder.effectiveAnchorDay()
                        )
                        dao.update(updated)
                        NotificationScheduler.schedule(context, updated)
                    }
                } else if (reminder.financialType == null) {
                    dao.update(reminder.copy(isDone = true))
                }
                // A one-time financial item (debt, cheque, installment...) is NOT done just because
                // its alarm rang: it stays open (and «عقب‌افتاده») until it is actually paid.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
