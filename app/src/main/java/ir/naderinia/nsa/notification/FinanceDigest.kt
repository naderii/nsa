package ir.naderinia.nsa.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.naderinia.nsa.MainActivity
import ir.naderinia.nsa.R
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.util.FinanceInsights
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The daily "finance briefing": one notification each morning that says what is overdue and what
 * falls due today / tomorrow (and, on the 1st of a Jalali month, what the month will cost).
 * It stays silent on days when there is nothing worth interrupting for.
 *
 * The setting lives in plain SharedPreferences (not the database), so it adds no schema change.
 */
object FinanceDigest {

    private const val PREFS = "finance_digest_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_HOUR = "hour"
    private const val REQUEST_CODE = 9_000_001
    const val NOTIFICATION_ID = 9_000_001
    const val DEFAULT_HOUR = 8

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun hour(context: Context): Int = prefs(context).getInt(KEY_HOUR, DEFAULT_HOUR)

    fun configure(context: Context, enabled: Boolean, hour: Int) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).putInt(KEY_HOUR, hour).apply()
        if (enabled) schedule(context) else cancel(context)
    }

    /** Re-arms the daily alarm (after boot, app start or a settings change). */
    fun reschedule(context: Context) {
        if (isEnabled(context)) schedule(context) else cancel(context)
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, FinanceDigestReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun nextTrigger(context: Context): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour(context))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // a few seconds of slack so an alarm that is just being delivered does not re-arm itself for "now"
        if (cal.timeInMillis <= System.currentTimeMillis() + 5_000) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        NotificationScheduler.setAlarm(context, alarmManager, nextTrigger(context), pendingIntent(context))
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }
}

class FinanceDigestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Always line up tomorrow's briefing first, so a failure below never ends the series.
                FinanceDigest.reschedule(context)
                if (!FinanceDigest.isEnabled(context)) return@launch

                val db = AppDatabase.getInstance(context)
                val now = System.currentTimeMillis()
                val financial = db.reminderDao().getAllOnce().filter { it.financialType != null }
                val logs = db.paymentLogDao().getAllOnce()
                val analysis = FinanceInsights.analyze(financial, logs, now)
                val (title, text) = FinanceInsights.digest(analysis, now) ?: return@launch

                NotificationHelper.ensureChannel(context)
                val open = PendingIntent.getActivity(
                    context,
                    FinanceDigest.NOTIFICATION_ID,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, NotificationHelper.currentChannelId(context))
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(text.lineSequence().first())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .build()
                try {
                    NotificationManagerCompat.from(context).notify(FinanceDigest.NOTIFICATION_ID, notification)
                } catch (_: SecurityException) {
                    // notification permission was revoked: nothing to show
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
