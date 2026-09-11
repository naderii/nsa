package ir.naderinia.nsa.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val CHANNEL_ID = "reminders_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "یادآوری‌ها",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "یادآوری‌های زمان‌بندی‌شده نرم‌افزار NSA"
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
