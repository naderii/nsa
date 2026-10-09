package ir.naderinia.nsa.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.data.RepeatInterval
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
                        // One-time reminders are normally marked done when they fire; be defensive.
                        if (reminder.repeatInterval == RepeatInterval.NONE && !reminder.isDone) {
                            dao.update(reminder.copy(isDone = true))
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
