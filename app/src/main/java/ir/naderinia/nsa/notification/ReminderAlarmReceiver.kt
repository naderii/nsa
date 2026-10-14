package ir.naderinia.nsa.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.naderinia.nsa.MainActivity
import ir.naderinia.nsa.R
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.data.FinancialType
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

        const val SNOOZE_SHORT_MINUTES = 10
        const val SNOOZE_LONG_MINUTES = 60
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return
        val isSnoozeFire = intent.action == ACTION_SNOOZE_FIRE

        // Go async so we can do a suspend DB read/write before the receiver dies.
        val pendingResult = goAsync()
        val dao = AppDatabase.getInstance(context).reminderDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = dao.getById(reminderId)
                if (reminder == null) {
                    // Reminder was deleted while a snooze was pending.
                    NotificationScheduler.clearSnoozeRecord(context, reminderId)
                    return@launch
                }

                if (reminder.isDone) {
                    // stale alarm of something already finished
                    NotificationScheduler.clearSnoozeRecord(context, reminderId)
                    return@launch
                }

                NotificationHelper.ensureChannel(context)
                showNotification(
                    context = context,
                    id = reminderId,
                    title = reminder.title,
                    text = reminder.note.ifBlank { "یادآوری در دسته «${reminder.category}»" },
                    doneLabel = when {
                        reminder.category == "دارو" -> "خوردم ✓"
                        reminder.isRecurringBill() -> "پرداخت شد ✓"
                        reminder.financialType == FinancialType.CREDIT -> "دریافت شد ✓"
                        reminder.financialType != null -> "پرداخت شد ✓"
                        else -> "انجام شد ✓"
                    }
                )

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

    private fun showNotification(context: Context, id: Long, title: String, text: String, doneLabel: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.currentChannelId(context))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, doneLabel, actionIntent(context, id, ReminderActionReceiver.ACTION_DONE, 0))
            .addAction(
                0,
                "${SNOOZE_SHORT_MINUTES} دقیقه بعد",
                actionIntent(context, id, ReminderActionReceiver.ACTION_SNOOZE, SNOOZE_SHORT_MINUTES)
            )
            .addAction(
                0,
                "1 ساعت بعد",
                actionIntent(context, id, ReminderActionReceiver.ACTION_SNOOZE, SNOOZE_LONG_MINUTES)
            )
            .build()

        NotificationManagerCompat.from(context).notify(id.toInt(), notification)
    }

    private fun actionIntent(context: Context, id: Long, action: String, minutes: Int): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            // data keeps "10 min" and "1 hour" PendingIntents distinct (extras don't count for identity)
            data = Uri.parse("nsa://reminder/$id/$action/$minutes")
            putExtra(EXTRA_REMINDER_ID, id)
            putExtra(ReminderActionReceiver.EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
