package ir.naderinia.nsa.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.data.MedicationLog
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.data.effectiveAnchorDay
import ir.naderinia.nsa.data.isRecurringBill
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.widget.NsaWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the buttons on a reminder notification ("انجام شد" / snooze) so the
 * person never has to open the app just to deal with an alert.
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DONE = "ir.naderinia.nsa.action.DONE"
        const val ACTION_SNOOZE = "ir.naderinia.nsa.action.SNOOZE"

        /** The circle next to a task on the home-screen widget. */
        const val ACTION_WIDGET_DONE = "ir.naderinia.nsa.action.WIDGET_DONE"
        const val EXTRA_MINUTES = "extra_minutes"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return
        val action = intent.action ?: return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, ReminderAlarmReceiver.SNOOZE_SHORT_MINUTES)

        // Buttons don't auto-dismiss the notification — do it ourselves right away.
        NotificationManagerCompat.from(context).cancel(reminderId.toInt())

        val pendingResult = goAsync()
        val dao = AppDatabase.getInstance(context).reminderDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = dao.getById(reminderId) ?: return@launch

                when (action) {
                    ACTION_DONE -> {
                        // Drop any pending snooze for this reminder.
                        NotificationScheduler.cancelSnooze(context, reminderId)

                        when {
                            reminder.category == "دارو" -> {
                                // "خوردم": remember that this dose was taken.
                                AppDatabase.getInstance(context).medicationLogDao()
                                    .insert(MedicationLog(reminderId = reminderId))
                                toast(context, "ثبت شد ✓")
                                if (reminder.repeatInterval == RepeatInterval.NONE && !reminder.isDone) {
                                    dao.update(reminder.copy(isDone = true))
                                }
                            }

                            reminder.isRecurringBill() -> {
                                // "پرداخت شد": log what was still unpaid, then roll to next cycle.
                                val remaining = (reminder.amount ?: 0L) - reminder.amountPaid
                                if (remaining > 0) {
                                    AppDatabase.getInstance(context).paymentLogDao()
                                        .insert(PaymentLog(reminderId = reminderId, amount = remaining))
                                }
                                val rolled = BillCycle.rolled(reminder)
                                dao.update(rolled)
                                NotificationScheduler.schedule(context, rolled)
                                toast(context, "پرداخت ثبت شد ✓")
                            }

                            reminder.financialType != null && reminder.repeatInterval == RepeatInterval.NONE -> {
                                FinancialSettle.settle(context, reminder)
                                toast(context, "ثبت شد ✓")
                            }

                            reminder.repeatInterval == RepeatInterval.NONE && !reminder.isDone -> {
                                dao.update(reminder.copy(isDone = true))
                            }
                        }
                    }

                    ACTION_WIDGET_DONE -> {
                        NotificationScheduler.cancelSnooze(context, reminderId)

                        when {
                            reminder.category == "دارو" -> {
                                AppDatabase.getInstance(context).medicationLogDao()
                                    .insert(MedicationLog(reminderId = reminderId))
                                toast(context, "ثبت شد ✓")
                            }

                            reminder.isRecurringBill() -> {
                                val remaining = (reminder.amount ?: 0L) - reminder.amountPaid
                                if (remaining > 0) {
                                    AppDatabase.getInstance(context).paymentLogDao()
                                        .insert(PaymentLog(reminderId = reminderId, amount = remaining))
                                }
                                val rolled = BillCycle.rolled(reminder)
                                dao.update(rolled)
                                NotificationScheduler.schedule(context, rolled)
                                toast(context, "پرداخت ثبت شد ✓")
                            }

                            reminder.financialType != null && reminder.repeatInterval == RepeatInterval.NONE -> {
                                FinancialSettle.settle(context, reminder)
                                toast(context, "ثبت شد ✓")
                            }

                            reminder.repeatInterval == RepeatInterval.NONE -> {
                                dao.update(reminder.copy(isDone = true))
                                NotificationScheduler.cancel(context, reminderId)
                            }

                            else -> {
                                // Repeating task done early: skip to its next occurrence.
                                val next = NotificationScheduler.nextOccurrence(
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
                            }
                        }
                    }

                    ACTION_SNOOZE -> {
                        val at = System.currentTimeMillis() + minutes * 60_000L
                        if (reminder.repeatInterval == RepeatInterval.NONE) {
                            // One-time reminder: it was marked done when it fired. Un-done it and
                            // move it to the snoozed time so the list, widget and reboot
                            // rescheduling all keep working through the normal path.
                            val updated = reminder.copy(isDone = false, triggerAtMillis = at)
                            dao.update(updated)
                            NotificationScheduler.schedule(context, updated)
                        } else {
                            // Repeating reminder: its series already advanced; add a one-off re-alert.
                            NotificationScheduler.scheduleSnooze(context, reminderId, at)
                        }
                        toast(context, "یادآوری شد تا ${snoozeLabel(minutes)} دیگه")
                    }
                }

                NsaWidgetProvider.updateAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun snoozeLabel(minutes: Int): String {
        val text = if (minutes % 60 == 0) {
            val h = minutes / 60
            if (h == 1) "یک ساعت" else "$h ساعت"
        } else {
            "$minutes دقیقه"
        }
        return JalaliCalendar.toPersianDigits(text)
    }

    private fun toast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
