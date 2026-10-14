package ir.naderinia.nsa.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import ir.naderinia.nsa.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight crash reporting with no server and no stored credentials.
 *
 *  1. When the app crashes, the stack trace + device info is written to a private file.
 *  2. On the next launch the person is asked whether to send it.
 *  3. "Send" opens their own email app with the report pre-filled — nothing leaves
 *     the phone unless they press send there.
 *
 * Only technical info is included (app/Android version, device model, stack trace).
 */
object CrashReporter {

    const val REPORT_EMAIL = "saaly2000@gmail.com"

    private const val FILE_NAME = "last_crash.txt"
    private const val MAX_CHARS = 12_000

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(app.filesDir, FILE_NAME).writeText(buildReport(thread, throwable))
            } catch (_: Throwable) {
                // never let the reporter itself cause trouble
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun readPending(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return try {
            file.readText().takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    fun clearPending(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Throwable) {
        }
    }

    /** Opens the email app with the report. If none exists, copies the report to the clipboard. */
    fun sendByEmail(context: Context, report: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "گزارش خطا - یادآور من ${BuildConfig.VERSION_NAME}")
            putExtra(Intent.EXTRA_TEXT, report)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("crash report", report))
            false
        }
    }

    private fun buildReport(thread: Thread, throwable: Throwable): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val header = buildString {
            appendLine("App: ${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Time: $time")
            appendLine("Thread: ${thread.name}")
            appendLine()
        }
        val trace = Log.getStackTraceString(throwable)
        return (header + trace).take(MAX_CHARS)
    }
}
