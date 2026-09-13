package ir.naderinia.nsa.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RepeatInterval {
    NONE, DAILY, WEEKLY, MONTHLY, YEARLY
}

enum class FinancialType {
    DEBT,        // بدهی من — من به کسی بدهکارم
    CREDIT,      // طلب من — کسی به من بدهکاره
    CHECK,       // چک
    INSTALLMENT, // قسط یا وام
    BILL         // قبض
}

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String = "",
    val category: String = "عمومی",
    val categoryColor: Long = 0xFF6750A4, // ARGB stored as Long for Room

    // Epoch millis of the next time this reminder should fire.
    val triggerAtMillis: Long,

    val repeatInterval: RepeatInterval = RepeatInterval.NONE,

    // Whether it has been marked done (only meaningful for non-repeating reminders).
    val isDone: Boolean = false,

    // Optional financial fields (Phase 2 groundwork, present from day one
    // so the schema does not need to change later).
    val amount: Long? = null,          // amount in Toman, null = not a financial reminder
    val counterparty: String? = null,  // who owes / is owed
    val counterpartyPhone: String? = null,
    val amountPaid: Long = 0,
    val financialType: FinancialType? = null // null when amount is null (not a financial reminder)
)
