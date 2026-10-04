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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
        // Today's unfinished tasks
        // ---------------------------------------------------------

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

        // ---------------------------------------------------------
        // Overdue unfinished tasks
        // ---------------------------------------------------------

        val overdueCount = all.count { reminder ->
            !reminder.isDone &&
                reminder.triggerAtMillis < now
        }

        // ---------------------------------------------------------
        // Today's count
        // ---------------------------------------------------------

        views.setTextViewText(
            R.id.widget_today_count,
            when (todayTasks.size) {
                0 -> "امروز کاری نداری"
                1 -> "امروز · ۱ کار"
                else -> "امروز · ${todayTasks.size} کار"
            }
        )

        // ---------------------------------------------------------
        // Remove old dynamic items
        // ---------------------------------------------------------

        views.removeAllViews(
            R.id.widget_tasks_container
        )

        // ---------------------------------------------------------
        // Widget size
        // ---------------------------------------------------------

        val options =
            appWidgetManager.getAppWidgetOptions(
                widgetId
            )

        val minHeight =
            options.getInt(
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
            )

        android.util.Log.d(
            "NsaWidget",
            "widgetId=$widgetId minHeight=${minHeight}dp"
        )

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

        val visibleTasks =
            todayTasks.take(visibleTaskCount)

        // ---------------------------------------------------------
        // Add today's tasks
        // ---------------------------------------------------------

        visibleTasks.forEach { reminder ->

            val taskView = RemoteViews(
                context.packageName,
                R.layout.widget_task_item
            )

            taskView.setTextViewText(
                R.id.widget_task_title_text,
                reminder.title
            )

            taskView.setTextViewText(
                R.id.widget_task_time,
                formatTime(
                    reminder.triggerAtMillis
                )
            )

            // Open the selected reminder.
            val taskIntent = Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP

                putExtra(
                    "reminder_id",
                    reminder.id
                )
            }

            val taskPendingIntent =
                PendingIntent.getActivity(
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

        // ---------------------------------------------------------
        // No tasks today
        // ---------------------------------------------------------

        if (todayTasks.isEmpty()) {

            val emptyView = RemoteViews(
                context.packageName,
                R.layout.widget_task_item
            )

            emptyView.setTextViewText(
                R.id.widget_task_title_text,
                "همه کارهای امروز انجام شده 🎉"
            )

            emptyView.setTextViewText(
                R.id.widget_task_time,
                ""
            )

            views.addView(
                R.id.widget_tasks_container,
                emptyView
            )
        }

        // ---------------------------------------------------------
        // More tasks
        // ---------------------------------------------------------

        if (todayTasks.size > visibleTaskCount) {

            val moreView = RemoteViews(
                context.packageName,
                R.layout.widget_task_item
            )

            moreView.setTextViewText(
                R.id.widget_task_title_text,
                "+ ${todayTasks.size - visibleTaskCount} کار دیگر"
            )

            moreView.setTextViewText(
                R.id.widget_task_time,
                ""
            )

            moreView.setOnClickPendingIntent(
                R.id.widget_task_title_text,
                openPendingIntent
            )

            views.addView(
                R.id.widget_tasks_container,
                moreView
            )
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

    private fun formatTime(
        millis: Long
    ): String {

        return SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        ).format(millis)
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