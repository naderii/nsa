package ir.naderinia.nsa.util

import android.content.Context
import android.net.Uri
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.notification.NotificationScheduler
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    private const val FORMAT_VERSION = 1

    suspend fun exportToUri(context: Context, uri: Uri) {
        val db = AppDatabase.getInstance(context)
        val reminders = db.reminderDao().getAllOnce()
        val logs = db.paymentLogDao().getAllOnce()

        val root = JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("reminders", JSONArray(reminders.map { reminderToJson(it) }))
            put("paymentLogs", JSONArray(logs.map { logToJson(it) }))
        }

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("فایل برای نوشتن باز نشد")
    }

    /** Restores reminders + payment history from a backup file, and reschedules
     * alarms for anything that isn't done yet. Returns how many reminders were
     * restored. Safe to run twice on the same file (replaces by id). */
    suspend fun importFromUri(context: Context, uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("فایل خوانده نشد")

        val root = JSONObject(text)
        val remindersArray = root.optJSONArray("reminders") ?: JSONArray()
        val logsArray = root.optJSONArray("paymentLogs") ?: JSONArray()

        val db = AppDatabase.getInstance(context)
        var restoredCount = 0

        for (i in 0 until remindersArray.length()) {
            val reminder = jsonToReminder(remindersArray.getJSONObject(i))
            db.reminderDao().upsert(reminder)
            if (!reminder.isDone) {
                NotificationScheduler.schedule(context, reminder)
            }
            restoredCount++
        }

        for (i in 0 until logsArray.length()) {
            db.paymentLogDao().insertFromBackup(jsonToLog(logsArray.getJSONObject(i)))
        }

        return restoredCount
    }

    private fun reminderToJson(r: Reminder): JSONObject = JSONObject().apply {
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
        put("mileageTargetKm", r.mileageTargetKm ?: JSONObject.NULL)
        put("location", r.location ?: JSONObject.NULL)
        put("bankName", r.bankName ?: JSONObject.NULL)
        put("repeatDaysOfWeek", r.repeatDaysOfWeek ?: JSONObject.NULL)
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
        repeatDaysOfWeek = if (o.isNull("repeatDaysOfWeek")) null else o.optString("repeatDaysOfWeek")
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
}