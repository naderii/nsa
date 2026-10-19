package ir.naderinia.nsa.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.naderinia.nsa.MainActivity
import ir.naderinia.nsa.R
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.isCritical
import ir.naderinia.nsa.data.isRecurringBill
import ir.naderinia.nsa.util.JalaliCalendar

/**
 * Builds and posts the notification of one reminder. Shared by the normal alarm and by the
 * escalation re-alerts of a «حیاتی» reminder, so both look and behave the same (same buttons,
 * same notification id → a re-alert replaces the previous one instead of stacking up).
 */
object ReminderNotifier {

    /** Extra on the notification's tap intent: opening it counts as "seen" and stops escalation. */
    const val EXTRA_ACK_REMINDER_ID = "ack_reminder_id"

    fun doneLabel(reminder: Reminder): String = when {
        reminder.category == "دارو" -> "خوردم ✓"
        reminder.isRecurringBill() -> "پرداخت شد ✓"
        reminder.financialType == FinancialType.CREDIT -> "دریافت شد ✓"
        reminder.financialType != null -> "پرداخت شد ✓"
        else -> "انجام شد ✓"
    }

    /**
     * @param escalationStage 0 for the first alert, 1.. for the repeated ones of a critical reminder.
     */
    fun show(context: Context, reminder: Reminder, escalationStage: Int = 0) {
        if (lacksNotificationPermission(context)) return

        val critical = reminder.isCritical()
        if (critical) NotificationHelper.ensureCriticalChannel(context) else NotificationHelper.ensureChannel(context)
        val channelId = if (critical) NotificationHelper.CRITICAL_CHANNEL_ID else NotificationHelper.currentChannelId(context)

        val id = reminder.id
        val baseText = reminder.note.ifBlank { "یادآوری در دسته «${reminder.category}»" }
        val text = if (escalationStage > 0) {
            "⏰ تذکر ${JalaliCalendar.toPersianDigits(escalationStage.toString())}: هنوز تأیید نکردی — $baseText"
        } else baseText

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_ACK_REMINDER_ID, id)
        }
        val contentIntent = PendingIntent.getActivity(
            context, id.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (critical) "❗ ${reminder.title}" else reminder.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (critical) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (critical) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, doneLabel(reminder), actionIntent(context, id, ReminderActionReceiver.ACTION_DONE, 0))
            .addAction(
                0, "${ReminderAlarmReceiver.SNOOZE_SHORT_MINUTES} دقیقه بعد",
                actionIntent(context, id, ReminderActionReceiver.ACTION_SNOOZE, ReminderAlarmReceiver.SNOOZE_SHORT_MINUTES)
            )
            .addAction(
                0, "1 ساعت بعد",
                actionIntent(context, id, ReminderActionReceiver.ACTION_SNOOZE, ReminderAlarmReceiver.SNOOZE_LONG_MINUTES)
            )

        try {
            NotificationManagerCompat.from(context).notify(id.toInt(), builder.build())
        } catch (_: SecurityException) {
            // notification permission was revoked: nothing to show
        }
    }

    private fun lacksNotificationPermission(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    private fun actionIntent(context: Context, id: Long, action: String, minutes: Int): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            // data keeps "10 min" and "1 hour" PendingIntents distinct (extras don't count for identity)
            data = Uri.parse("nsa://reminder/$id/$action/$minutes")
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, id)
            putExtra(ReminderActionReceiver.EXTRA_MINUTES, minutes)
        }
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
