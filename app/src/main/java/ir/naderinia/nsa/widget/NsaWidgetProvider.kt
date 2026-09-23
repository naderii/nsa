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
                    updateWidget(context, appWidgetManager, id)
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

        // Open app when widget title is tapped.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val openPendingIntent = PendingIntent.getActivity(
            context,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(
            R.id.widget_title,
            openPendingIntent
        )

        val dao = (context.applicationContext as NsaApplication)
            .database
            .reminderDao()

        val all = dao.getAllOnce()

        val now = System.currentTimeMillis()
        val today = Calendar.getInstance()

        val todayTasks = all
            .filter { reminder ->
                !reminder.isDone &&
                    reminder.triggerAtMillis >= now &&
                    isSameDay(reminder.triggerAtMillis, today)
            }
            .sortedBy { it.triggerAtMillis }

        val overdueCount = all.count { reminder ->
            !reminder.isDone &&
                reminder.triggerAtMillis < now
        }

        // Number of today's tasks.
        views.setTextViewText(
            R.id.widget_today_count,
            "${todayTasks.size} کار"
        )

        // Remove all dynamically added task views.
        views.removeAllViews(R.id.widget_tasks_container)

        // Show maximum 5 tasks.
        val visibleTasks = todayTasks.take(5)

        visibleTasks.forEachIndexed { index, reminder ->

            val taskView = RemoteViews(
                context.packageName,
                R.layout.widget_task_item
            )

            taskView.setTextViewText(
                R.id.widget_task_title,
                "• ${reminder.title}"
            )

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

        // More than 5 tasks.
        if (todayTasks.size > 5) {
            val moreView = RemoteViews(
                context.packageName,
                R.layout.widget_task_item
            )

            moreView.setTextViewText(
                R.id.widget_task_title,
                "+ ${todayTasks.size - 5} کار دیگر"
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

        views.setTextViewText(
            R.id.widget_overdue_count,
            if (overdueCount > 0) {
                "عقب‌افتاده: $overdueCount"
            } else {
                "همه کارها به‌روز هستند"
            }
        )

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
            val manager = AppWidgetManager.getInstance(context)

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
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
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