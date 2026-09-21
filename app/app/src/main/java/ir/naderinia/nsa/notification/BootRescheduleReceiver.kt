package ir.naderinia.nsa.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ir.naderinia.nsa.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val dao = AppDatabase.getInstance(context).reminderDao()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                dao.getAllOnce()
                    .filter { !it.isDone && it.triggerAtMillis > now }
                    .forEach { NotificationScheduler.schedule(context, it) }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
