package ir.naderinia.nsa

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.notification.NotificationHelper
import ir.naderinia.nsa.util.CrashReporter
import ir.naderinia.nsa.widget.NsaWidgetProvider

class NsaApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        NotificationHelper.ensureChannel(this)
        NsaWidgetProvider.updateAll(this)
        cleanOrphanAttachments()
    }

    /**
     * Photos taken for a form that was then cancelled, attachments that were removed, and files of
     * deleted reminders stay in our storage forever otherwise. Delete files that no reminder points
     * to. Files younger than a day are kept (a form may be open, an undo may be pending).
     */
    private fun cleanOrphanAttachments() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val dir = File(filesDir, "attachments")
                val files = dir.listFiles() ?: return@runCatching
                val used = database.reminderDao().getAllOnce()
                    .mapNotNull { it.attachmentUri }
                    .mapNotNull { Uri.parse(it).lastPathSegment }
                    .toSet()
                val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
                files.filter { it.isFile && it.name !in used && it.lastModified() < cutoff }
                    .forEach { it.delete() }
            }
        }
    }
}
