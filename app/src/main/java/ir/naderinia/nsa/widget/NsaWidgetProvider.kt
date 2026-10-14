package ir.naderinia.nsa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import ir.naderinia.nsa.MainActivity
import ir.naderinia.nsa.NsaApplication
import ir.naderinia.nsa.R
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.notification.ReminderActionReceiver
import ir.naderinia.nsa.notification.ReminderAlarmReceiver
import ir.naderinia.nsa.util.JalaliCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val WEEKDAY_NAMES = arrayOf(
    "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"
)

class NsaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id ->
                    updateWidget(
                        context,
                        appWidgetManager,
                        id
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(
            context.packageName,
            R.layout.widget_today
        )

        // ---------------------------------------------------------
        // Open app
        // ---------------------------------------------------------

        val openIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("nsa://widget/open")
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(
            R.id.widget_title,
            openPendingIntent
        )

        // ---------------------------------------------------------
        // Quick add
        // ---------------------------------------------------------

        val addIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP

            data = Uri.parse("nsa://widget/add")

            putExtra(
                MainActivity.EXTRA_NAVIGATE_TO_ADD,
                true
            )
        }

        val addPendingIntent = PendingIntent.getActivity(
            context,
            2,
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(
            R.id.widget_add,
            addPendingIntent
        )

        // ---------------------------------------------------------
        // Database
        // ---------------------------------------------------------

        val dao =
            (context.applicationContext as NsaApplication)
                .database
                .reminderDao()

        val all = dao.getAllOnce()

        val now = System.currentTimeMillis()
        val today = Calendar.getInstance()

        // ---------------------------------------------------------
        // What to show: missed tasks first, then what's left of today
        // ---------------------------------------------------------

        val todayTasks = all
            .filter { reminder ->
                !reminder.isDone &&
                    reminder.triggerAtMillis >= now &&
                    isSameDay(reminder.triggerAtMillis, today)
            }
            .sortedBy { it.triggerAtMillis }

        val overdueTasks = all
            .filter { !it.isDone && it.triggerAtMillis < now }
            .sortedBy { it.triggerAtMillis }

        val overdueCount = overdueTasks.size

        // Header: weekday + Jalali date + how many are left.
        val jalali = JalaliCalendar.millisToJalali(now)
        val weekday = WEEKDAY_NAMES[today.get(Calendar.DAY_OF_WEEK) - 1]
        val dateText = JalaliCalendar.toPersianDigits(
            "${jalali.day} ${JalaliCalendar.monthNames[jalali.month - 1]}"
        )

        views.setTextViewText(
            R.id.widget_today_count,
            "$weekday $dateText · " + when (todayTasks.size) {
                0 -> "کاری نمونده"
                else -> JalaliCalendar.toPersianDigits("${todayTasks.size} کار مونده")
            }
        )

        views.removeAllViews(R.id.widget_tasks_container)

        // ---------------------------------------------------------
        // Widget size
        // ---------------------------------------------------------

        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        /*
         * Small  -> 2 tasks
         * Medium -> 4 tasks
         * Large  -> 7 tasks
         */
        val visibleTaskCount = when {
            minHeight < 150 -> 2
            minHeight < 220 -> 4
            else -> 7
        }

        // At most 2 missed tasks, so they never crowd out today's.
        val shownOverdue = overdueTasks.take(2)
        val allShown = shownOverdue + todayTasks
        val visibleTasks = allShown.take(visibleTaskCount)

        visibleTasks.forEach { reminder ->
            views.addView(
                R.id.widget_tasks_container,
                buildTaskRow(context, reminder, overdue = reminder in shownOverdue)
            )
        }

        // ---------------------------------------------------------
        // Nothing left today: say so, and show what's next
        // ---------------------------------------------------------

        if (visibleTasks.isEmpty()) {

            val emptyView = RemoteViews(context.packageName, R.layout.widget_task_item)
            emptyView.setTextViewText(R.id.widget_task_bullet, "✓")
            emptyView.setTextViewText(R.id.widget_task_title_text, "همه کارهای امروز انجام شده 🎉")
            emptyView.setTextViewText(R.id.widget_task_time, "")
            views.addView(R.id.widget_tasks_container, emptyView)

            val upcoming = all
                .filter { !it.isDone && it.triggerAtMillis >= now }
                .minByOrNull { it.triggerAtMillis }

            if (upcoming != null && visibleTaskCount > 2) {
                val nextView = RemoteViews(context.packageName, R.layout.widget_task_item)
                nextView.setTextViewText(R.id.widget_task_bullet, "›")
                nextView.setTextViewText(R.id.widget_task_title_text, "بعدی: ${upcoming.title}")
                nextView.setTextViewText(
                    R.id.widget_task_time,
                    shortDate(upcoming.triggerAtMillis) + " " + formatTime(upcoming.triggerAtMillis)
                )
                views.addView(R.id.widget_tasks_container, nextView)
            }
        }

        // ---------------------------------------------------------
        // More tasks
        // ---------------------------------------------------------

        if (allShown.size > visibleTaskCount) {

            val moreView = RemoteViews(context.packageName, R.layout.widget_task_item)
            moreView.setTextViewText(R.id.widget_task_bullet, "")
            moreView.setTextViewText(
                R.id.widget_task_title_text,
                "+ ${allShown.size - visibleTaskCount} کار دیگر"
            )
            moreView.setTextViewText(R.id.widget_task_time, "")
            moreView.setOnClickPendingIntent(R.id.widget_task_title_text, openPendingIntent)
            views.addView(R.id.widget_tasks_container, moreView)
        }

        // ---------------------------------------------------------
        // Overdue
        // ---------------------------------------------------------

        views.setTextViewText(
            R.id.widget_overdue_count,
            if (overdueCount > 0) {
                "⚠ $overdueCount کار عقب‌افتاده"
            } else {
                "همه کارها به‌روز هستند ✓"
            }
        )

        // Open app when overdue section is tapped.
        views.setOnClickPendingIntent(
            R.id.widget_overdue_count,
            openPendingIntent
        )

        // ---------------------------------------------------------
        // Update widget
        // ---------------------------------------------------------

        appWidgetManager.updateAppWidget(
            widgetId,
            views
        )
    }

    /**
     * One row. Tapping the circle completes the task right from the home
     * screen (for medication it logs "خوردم"); tapping the row opens it in the app.
     */
    private fun buildTaskRow(context: Context, reminder: Reminder, overdue: Boolean): RemoteViews {

        val row = RemoteViews(context.packageName, R.layout.widget_task_item)

        row.setTextViewText(R.id.widget_task_title_text, reminder.title)
        row.setTextViewText(
            R.id.widget_task_time,
            if (overdue) shortDate(reminder.triggerAtMillis) else formatTime(reminder.triggerAtMillis)
        )
        row.setTextViewText(
            R.id.widget_task_bullet,
            if (reminder.category == "دارو") "💊" else "○"
        )

        if (overdue) {
            row.setTextColor(R.id.widget_task_title_text, 0xFFFFCC80.toInt())
            row.setTextColor(R.id.widget_task_time, 0xFFFFCC80.toInt())
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("nsa://widget/open/${reminder.id}")
            putExtra("reminder_id", reminder.id)
        }

        row.setOnClickPendingIntent(
            R.id.widget_task_title,
            PendingIntent.getActivity(
                context,
                reminder.id.toInt(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val doneIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_WIDGET_DONE
            data = Uri.parse("nsa://widget/done/${reminder.id}")
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
        }

        row.setOnClickPendingIntent(
            R.id.widget_task_bullet,
            PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                doneIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        return row
    }

    private fun shortDate(millis: Long): String {
        val d = JalaliCalendar.millisToJalali(millis)
        return JalaliCalendar.toPersianDigits("${d.day} ${JalaliCalendar.monthNames[d.month - 1]}")
    }

    private fun formatTime(
        millis: Long
    ): String {

        return ir.naderinia.nsa.util.JalaliCalendar.toPersianDigits(
            SimpleDateFormat("HH:mm", Locale.US).format(millis)
        )
    }

    private fun isSameDay(
        millis: Long,
        reference: Calendar
    ): Boolean {

        val target =
            Calendar.getInstance().apply {
                timeInMillis = millis
            }

        return target.get(Calendar.YEAR) ==
            reference.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) ==
            reference.get(Calendar.DAY_OF_YEAR)
    }

    companion object {

        fun updateAll(
            context: Context
        ) {

            val manager =
                AppWidgetManager.getInstance(
                    context
                )

            val ids =
                manager.getAppWidgetIds(
                    ComponentName(
                        context,
                        NsaWidgetProvider::class.java
                    )
                )

            if (ids.isNotEmpty()) {

                val intent = Intent(
                    context,
                    NsaWidgetProvider::class.java
                ).apply {

                    action =
                        AppWidgetManager.ACTION_APPWIDGET_UPDATE

                    putExtra(
                        AppWidgetManager.EXTRA_APPWIDGET_IDS,
                        ids
                    )
                }

                context.sendBroadcast(intent)
            }
        }
    }
}