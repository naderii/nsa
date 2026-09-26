package ir.naderinia.nsa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ir.naderinia.nsa.MainActivity
import ir.naderinia.nsa.NsaApplication
import ir.naderinia.nsa.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

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

        // Open app when the widget title is tapped.
        val openIntent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
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

        // Database.
        val dao = (context.applicationContext as NsaApplication)
            .database
            .reminderDao()

        val all = dao.getAllOnce()

        val now = System.currentTimeMillis()
        val today = Calendar.getInstance()

        // Today's unfinished tasks.
        val todayTasks = all
            .filter { reminder ->
                !reminder.isDone &&
                    reminder.triggerAtMillis >= now &&
                    isSameDay(
                        reminder.triggerAtMillis,
                        today
                    )
            }
            .sortedBy {
                it.triggerAtMillis
            }

        // Overdue unfinished tasks.
        val overdueCount = all.count { reminder ->
            !reminder.isDone &&
                reminder.triggerAtMillis < now
        }

        // Total number of today's tasks.
        views.setTextViewText(
            R.id.widget_today_count,
            "${todayTasks.size} کار"
        )

        // Remove previous dynamic task views.
        views.removeAllViews(
            R.id.widget_tasks_container
        )

        // Get current widget size.
        val options = appWidgetManager.getAppWidgetOptions(
            widgetId
        )

        val minHeight = options.getInt(
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
        )

        android.util.Log.d(
        "NsaWidget",
        "widgetId=$widgetId minHeight=${minHeight}dp"
        )

        /*
         * Determine how many tasks should be visible
         * based on the widget height.
         *
         * Small  -> 2 tasks
         * Medium -> 4 tasks
         * Large  -> 7 tasks
         */
        val visibleTaskCount = when {
            minHeight < 150 -> 2
            minHeight < 220 -> 4
            else -> 7
        }

        val visibleTasks = todayTasks.take(
            visibleTaskCount
        )

        // Add today's tasks.
        visibleTasks.forEach { reminder ->

            val taskView = RemoteViews(
                context.packageName,
                R.layout.widget_task_item
            )

            taskView.setTextViewText(
                R.id.widget_task_title,
                "• ${reminder.title}"
            )

            // Open the app when a task is tapped.
            val taskIntent = Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP

                putExtra(
                    "reminder_id",
                    reminder.id
                )
            }

            val taskPendingIntent = PendingIntent.getActivity(
                context,
                reminder.id.toInt(),
                taskIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            taskView.setOnClickPendingIntent(
                R.id.widget_task_title,
                taskPendingIntent
            )

            views.addView(
                R.id.widget_tasks_container,
                taskView
            )
        }

        // No tasks today.
        if (todayTasks.isEmpty()) {

            val emptyView = RemoteViews(
                context.packageName,
                R.layout.widget_task_item
            )

            emptyView.setTextViewText(
                R.id.widget_task_title,
                "امروز کاری ندارید 🎉"
            )

            views.addView(
                R.id.widget_tasks_container,
                emptyView
            )
        }

        // More tasks are available.
        if (todayTasks.size > visibleTaskCount) {

            val moreView = RemoteViews(
                context.packageName,
                R.layout.widget_task_item
            )

            moreView.setTextViewText(
                R.id.widget_task_title,
                "+ ${todayTasks.size - visibleTaskCount} کار دیگر"
            )

            moreView.setOnClickPendingIntent(
                R.id.widget_task_title,
                openPendingIntent
            )

            views.addView(
                R.id.widget_tasks_container,
                moreView
            )
        }

        // Overdue count.
        views.setTextViewText(
            R.id.widget_overdue_count,
            if (overdueCount > 0) {
                "عقب‌افتاده: $overdueCount"
            } else {
                "همه کارها به‌روز هستند"
            }
        )

        // Update widget.
        appWidgetManager.updateAppWidget(
            widgetId,
            views
        )
    }

    private fun isSameDay(
        millis: Long,
        reference: Calendar
    ): Boolean {

        val target = Calendar.getInstance().apply {
            timeInMillis = millis
        }

        return target.get(Calendar.YEAR) ==
            reference.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) ==
            reference.get(Calendar.DAY_OF_YEAR)
    }

    companion object {

        fun updateAll(context: Context) {

            val manager = AppWidgetManager.getInstance(
                context
            )

            val ids = manager.getAppWidgetIds(
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