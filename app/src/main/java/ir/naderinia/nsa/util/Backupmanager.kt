package ir.naderinia.nsa.util

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.data.CheckStatus
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.MedicationLog
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.data.effectiveAnchorDay
import ir.naderinia.nsa.data.isRecurringBill
import ir.naderinia.nsa.notification.NotificationScheduler
import ir.naderinia.nsa.widget.NsaWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup file = a ZIP with
 *   backup.json            reminders, payment history, medication ("خوردم") history
 *   attachments/<name>     every attached photo / document, so they survive a new phone
 *
 * Format 1 (older versions) was a bare JSON file. Restoring still accepts it.
 */
object BackupManager {

    private const val FORMAT_VERSION = 2
    private const val JSON_ENTRY = "backup.json"
    private const val ATTACHMENTS_DIR = "attachments"

    suspend fun exportToUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val db = AppDatabase.getInstance(context)
        val reminders = db.reminderDao().getAllOnce()
        val logs = db.paymentLogDao().getAllOnce()
        val medLogs = db.medicationLogDao().getAllOnce()

        val out = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("فایل برای نوشتن باز نشد")

        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            // 1) Attachments. The same file can be shared by several reminders (installments),
            //    so each distinct uri is stored once.
            val entryByUri = HashMap<String, String>()
            reminders.mapNotNull { it.attachmentUri }.distinct().forEachIndexed { index, uriString ->
                runCatching {
                    val parsed = Uri.parse(uriString)
                    val ext = MimeTypeMap.getSingleton()
                        .getExtensionFromMimeType(context.contentResolver.getType(parsed))
                        ?: "bin"
                    val input = context.contentResolver.openInputStream(parsed) ?: return@runCatching
                    input.use {
                        val name = "$ATTACHMENTS_DIR/a$index.$ext"
                        zip.putNextEntry(ZipEntry(name))
                        it.copyTo(zip)
                        zip.closeEntry()
                        entryByUri[uriString] = name
                    }
                }
                // A missing / unreadable attachment must not break the whole backup.
            }

            // 2) The data itself.
            val root = JSONObject().apply {
                put("formatVersion", FORMAT_VERSION)
                put("exportedAt", System.currentTimeMillis())
                put("reminders", JSONArray(reminders.map { reminderToJson(it, entryByUri[it.attachmentUri]) }))
                put("paymentLogs", JSONArray(logs.map { logToJson(it) }))
                put("medicationLogs", JSONArray(medLogs.map { medLogToJson(it) }))
            }
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(root.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /** Restores reminders, payment + medication history and attachments, then re-arms the
     * alarms. Returns how many reminders were restored. Safe to run twice on the same file
     * (replaces by id). Reads both the ZIP format and the old bare-JSON format. */
    suspend fun importFromUri(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("فایل خوانده نشد")
        val input = BufferedInputStream(raw)

        input.mark(4)
        val b0 = input.read()
        val b1 = input.read()
        input.reset()
        val isZip = b0 == 'P'.code && b1 == 'K'.code

        var jsonText: String? = null
        val restoredFiles = HashMap<String, File>()
        val stamp = System.currentTimeMillis()

        if (isZip) {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == JSON_ENTRY ->
                            jsonText = zip.readBytes().toString(Charsets.UTF_8)

                        name.startsWith("$ATTACHMENTS_DIR/") && !entry.isDirectory -> {
                            // File(name).name drops any "../" — never write outside our folder.
                            val dir = File(context.filesDir, ATTACHMENTS_DIR).apply { mkdirs() }
                            val target = File(dir, "restored_${stamp}_${File(name).name}")
                            FileOutputStream(target).use { zip.copyTo(it) }
                            restoredFiles[name] = target
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } else {
            jsonText = input.use { it.readBytes().toString(Charsets.UTF_8) }
        }

        val root = JSONObject(jsonText ?: throw IllegalStateException("فایل پشتیبان قابل‌خواندن نبود"))
        val remindersArray = root.optJSONArray("reminders") ?: JSONArray()
        val logsArray = root.optJSONArray("paymentLogs") ?: JSONArray()
        val medArray = root.optJSONArray("medicationLogs") ?: JSONArray()

        val db = AppDatabase.getInstance(context)
        val parsedReminders = ArrayList<Reminder>()

        for (i in 0 until remindersArray.length()) {
            val obj = remindersArray.getJSONObject(i)
            var reminder = jsonToReminder(obj)

            val attachmentFile = if (obj.isNull("attachmentFile")) null else obj.optString("attachmentFile")
            val restored = attachmentFile?.let { restoredFiles[it] }
            if (restored != null && restored.exists()) {
                reminder = reminder.copy(
                    attachmentUri = FileProvider
                        .getUriForFile(context, "${context.packageName}.fileprovider", restored)
                        .toString()
                )
            }

            // A repeating reminder whose time passed while it was backed up: move it forward
            // instead of firing a stale alarm right now.
            if (!reminder.isDone &&
                reminder.repeatInterval != RepeatInterval.NONE &&
                !reminder.isRecurringBill() &&
                reminder.triggerAtMillis <= System.currentTimeMillis()
            ) {
                NotificationScheduler.nextFutureOccurrence(
                    reminder.triggerAtMillis,
                    reminder.repeatInterval,
                    reminder.repeatDaysOfWeek,
                    reminder.effectiveAnchorDay()
                )?.let {
                    reminder = reminder.copy(
                        triggerAtMillis = it,
                        repeatAnchorDay = reminder.effectiveAnchorDay()
                    )
                }
            }

            parsedReminders.add(reminder)
        }

        // Parse EVERYTHING first: a damaged file must fail before anything is written.
        val parsedLogs = (0 until logsArray.length()).map { jsonToLog(logsArray.getJSONObject(it)) }
        val parsedMeds = (0 until medArray.length()).map { jsonToMedLog(medArray.getJSONObject(it)) }

        // All-or-nothing write, so a failed restore never leaves half the data behind.
        db.withTransaction {
            parsedReminders.forEach { db.reminderDao().upsert(it) }
            parsedLogs.forEach { db.paymentLogDao().insertFromBackup(it) }
            parsedMeds.forEach { db.medicationLogDao().insertFromBackup(it) }
        }

        parsedReminders.forEach {
            if (it.isDone) NotificationScheduler.cancel(context, it.id)
            else NotificationScheduler.schedule(context, it)
        }
        NsaWidgetProvider.updateAll(context)

        parsedReminders.size
    }

    private fun reminderToJson(r: Reminder, attachmentFile: String?): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("title", r.title)
        put("note", r.note)
        put("category", r.category)
        put("categoryColor", r.categoryColor)
        put("triggerAtMillis", r.triggerAtMillis)
        put("repeatInterval", r.repeatInterval.name)
        put("isDone", r.isDone)
        put("amount", r.amount ?: JSONObject.NULL)
        put("counterparty", r.counterparty ?: JSONObject.NULL)
        put("counterpartyPhone", r.counterpartyPhone ?: JSONObject.NULL)
        put("amountPaid", r.amountPaid)
        put("financialType", r.financialType?.name ?: JSONObject.NULL)
        put("attachmentUri", r.attachmentUri ?: JSONObject.NULL)
        put("attachmentFile", attachmentFile ?: JSONObject.NULL)
        put("mileageTargetKm", r.mileageTargetKm ?: JSONObject.NULL)
        put("location", r.location ?: JSONObject.NULL)
        put("bankName", r.bankName ?: JSONObject.NULL)
        put("repeatDaysOfWeek", r.repeatDaysOfWeek ?: JSONObject.NULL)
        put("checkStatus", r.checkStatus?.name ?: JSONObject.NULL)
        put("birthYear", r.birthYear ?: JSONObject.NULL)
        put("checklist", r.checklist ?: JSONObject.NULL)
        put("repeatAnchorDay", r.repeatAnchorDay ?: JSONObject.NULL)
        put("priority", r.priority)
    }

    private fun jsonToReminder(o: JSONObject): Reminder = Reminder(
        id = o.optLong("id", 0),
        title = o.getString("title"),
        note = o.optString("note", ""),
        category = o.optString("category", "عمومی"),
        categoryColor = o.optLong("categoryColor", 0xFF6750A4),
        triggerAtMillis = o.getLong("triggerAtMillis"),
        repeatInterval = runCatching {
            RepeatInterval.valueOf(o.optString("repeatInterval", "NONE"))
        }.getOrDefault(RepeatInterval.NONE),
        isDone = o.optBoolean("isDone", false),
        amount = if (o.isNull("amount")) null else o.optLong("amount"),
        counterparty = if (o.isNull("counterparty")) null else o.optString("counterparty"),
        counterpartyPhone = if (o.isNull("counterpartyPhone")) null else o.optString("counterpartyPhone"),
        amountPaid = o.optLong("amountPaid", 0),
        financialType = if (o.isNull("financialType")) null else runCatching {
            FinancialType.valueOf(o.getString("financialType"))
        }.getOrNull(),
        attachmentUri = if (o.isNull("attachmentUri")) null else o.optString("attachmentUri"),
        mileageTargetKm = if (o.isNull("mileageTargetKm")) null else o.optLong("mileageTargetKm"),
        location = if (o.isNull("location")) null else o.optString("location"),
        bankName = if (o.isNull("bankName")) null else o.optString("bankName"),
        repeatDaysOfWeek = if (o.isNull("repeatDaysOfWeek")) null else o.optString("repeatDaysOfWeek"),
        checkStatus = if (o.isNull("checkStatus")) null else runCatching {
            CheckStatus.valueOf(o.getString("checkStatus"))
        }.getOrNull(),
        birthYear = if (o.isNull("birthYear")) null else o.optInt("birthYear"),
        checklist = if (o.isNull("checklist")) null else o.optString("checklist"),
        repeatAnchorDay = if (o.isNull("repeatAnchorDay")) null else o.optInt("repeatAnchorDay"),
        priority = o.optInt("priority", 0)
    )

    private fun logToJson(l: PaymentLog): JSONObject = JSONObject().apply {
        put("id", l.id)
        put("reminderId", l.reminderId)
        put("amount", l.amount)
        put("timestampMillis", l.timestampMillis)
    }

    private fun jsonToLog(o: JSONObject): PaymentLog = PaymentLog(
        id = o.optLong("id", 0),
        reminderId = o.getLong("reminderId"),
        amount = o.getLong("amount"),
        timestampMillis = o.getLong("timestampMillis")
    )

    private fun medLogToJson(l: MedicationLog): JSONObject = JSONObject().apply {
        put("id", l.id)
        put("reminderId", l.reminderId)
        put("takenAtMillis", l.takenAtMillis)
    }

    private fun jsonToMedLog(o: JSONObject): MedicationLog = MedicationLog(
        id = o.optLong("id", 0),
        reminderId = o.getLong("reminderId"),
        takenAtMillis = o.getLong("takenAtMillis")
    )
}
