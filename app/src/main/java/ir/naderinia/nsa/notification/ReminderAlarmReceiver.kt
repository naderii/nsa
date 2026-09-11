package ir.naderinia.nsa.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.naderinia.nsa.MainActivity
import ir.naderinia.nsa.R
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.data.RepeatInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        // Go async so we can do a suspend DB read/write before the receiver dies.
        val pendingResult = goAsync()
        val dao = AppDatabase.getInstance(context).reminderDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = dao.getById(reminderId) ?: return@launch

                NotificationHelper.ensureChannel(context)
                showNotification(
                    context = context,
                    id = reminderId,
                    title = reminder.title,
                    text = reminder.note.ifBlank { "یادآوری در دسته «${reminder.category}»" }
                )

                if (reminder.repeatInterval != RepeatInterval.NONE) {
                    val next = NotificationScheduler.nextOccurrence(
                        reminder.triggerAtMillis,
                        reminder.repeatInterval
                    )
                    if (next != null) {
                        val updated = reminder.copy(triggerAtMillis = next)
                        dao.update(updated)
                        NotificationScheduler.schedule(context, updated)
                    }
                } else {
                    dao.update(reminder.copy(isDone = true))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, id: Long, title: String, text: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            id.toInt(),
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(id.toInt(), notification)
    }
}
