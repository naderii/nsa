package ir.naderinia.nsa.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.naderinia.nsa.data.Reminder

/**
 * «هشدار پله‌ای» for reminders marked «حیاتی».
 *
 * After the normal alert, if the person does nothing, the alert is repeated — louder (alarm sound,
 * its own channel) — at +5, +15, +35 and +65 minutes after the first one. It stops as soon as the person
 * taps «انجام شد» / «بعداً», opens the notification, finishes / deletes / edits the reminder in the app.
 * Swiping the notification away does NOT stop it: that is the whole point of «حیاتی».
 *
 * State lives in SharedPreferences (key "e_<id>" = "<stage>|<next alarm millis>") so it survives a reboot.
 */
object EscalationManager {

    const val ACTION_ESCALATE = "ir.naderinia.nsa.action.ESCALATE"

    /** Minutes to wait before each repeat (1st repeat after 5 min, 2nd 10 min later, …). */
    private val DELAYS_MINUTES = intArrayOf(5, 10, 20, 30)

    private const val PREFS = "escalation_prefs"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun pendingIntent(context: Context, reminderId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_ESCALATE
            data = Uri.parse("nsa://reminder/$reminderId/escalate")
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getBroadcast(
            context, reminderId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun arm(context: Context, reminderId: Long, stage: Int, atMillis: Long) {
        prefs(context).edit().putString("e_$reminderId", "$stage|$atMillis").apply()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        NotificationScheduler.setAlarm(context, alarmManager, atMillis, pendingIntent(context, reminderId))
    }

    /** Called right after the first alert of a critical reminder was shown. */
    fun start(context: Context, reminderId: Long) {
        arm(context, reminderId, 0, System.currentTimeMillis() + DELAYS_MINUTES[0] * 60_000L)
    }

    /** The person saw / handled it (or it no longer exists): stop repeating. Safe to call any time. */
    fun acknowledge(context: Context, reminderId: Long) {
        if (!prefs(context).contains("e_$reminderId")) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, reminderId))
        prefs(context).edit().remove("e_$reminderId").apply()
    }

    fun isActive(context: Context, reminderId: Long): Boolean = prefs(context).contains("e_$reminderId")

    /** An escalation alarm fired: repeat the alert and line up the next one (or finish). */
    fun onFire(context: Context, reminder: Reminder) {
        val saved = prefs(context).getString("e_${reminder.id}", null) ?: return // acknowledged meanwhile
        val stage = (saved.substringBefore('|').toIntOrNull() ?: 0) + 1
        ReminderNotifier.show(context, reminder, escalationStage = stage)
        if (stage < DELAYS_MINUTES.size) {
            arm(context, reminder.id, stage, System.currentTimeMillis() + DELAYS_MINUTES[stage] * 60_000L)
        } else {
            prefs(context).edit().remove("e_${reminder.id}").apply()
        }
    }

    /** After a reboot AlarmManager forgot everything: re-arm what was still pending. */
    fun rescheduleAfterBoot(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).all.forEach { (key, value) ->
            val id = key.removePrefix("e_").toLongOrNull() ?: return@forEach
            val parts = (value as? String)?.split('|') ?: return@forEach
            val stage = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val at = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            // an alarm missed while the phone was off fires shortly after boot instead of being lost
            arm(context, id, stage, if (at > now) at else now + 30_000L)
        }
    }
}
