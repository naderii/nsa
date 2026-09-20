package ir.naderinia.nsa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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

/**
 * A lightweight home-screen widget. Counts are read with a one-shot query
 * (not observed continuously — widgets don't have a long-lived UI tree),
 * refreshed on the system's own update schedule and whenever [updateAll]
 * is called after the reminder list changes (add/complete/delete/payment).
 */
class NsaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Without goAsync(), Android considers this receiver's work done the
        // moment onUpdate() returns (immediately, since the DB read below is
        // async) and may kill the process before the coroutine finishes —
        // which is exactly why the widget was stuck on "در حال بارگذاری...".
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_today)

        val addIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(MainActivity.EXTRA_NAVIGATE_TO_ADD, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val addPendingIntent = PendingIntent.getActivity(
            context, 0, addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_add_button, addPendingIntent)

        // Also let tapping the widget body open the app.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_title, openPendingIntent)

        // Fill in the live counts BEFORE the first (and only, now) push —
        // no more "push placeholder, then push again later" two-step.
        val dao = (context.applicationContext as NsaApplication).database.reminderDao()
        val all = dao.getAllOnce()
        val now = System.currentTimeMillis()
        val todayCal = Calendar.getInstance()

        val todayCount = all.count { r ->
            !r.isDone && r.triggerAtMillis >= now && isSameDay(r.triggerAtMillis, todayCal)
        }
        val overdueCount = all.count { !it.isDone && it.triggerAtMillis < now }

        views.setTextViewText(R.id.widget_today_count, "امروز: $todayCount کار")
        views.setTextViewText(R.id.widget_overdue_count, "عقب‌افتاده: $overdueCount")

        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun isSameDay(millis: Long, reference: Calendar): Boolean {
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        return target.get(Calendar.YEAR) == reference.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == reference.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        /** Call after any reminder change so the widget doesn't wait for its next scheduled refresh. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, NsaWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                val intent = Intent(context, NsaWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }
}
