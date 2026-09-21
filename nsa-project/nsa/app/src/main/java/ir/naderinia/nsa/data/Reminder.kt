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
    val financialType: FinancialType? = null, // null when amount is null (not a financial reminder)

    // URI (content:// or a file:// under our own storage) of an attached
    // photo or document — contract, warranty, insurance card, ID, receipt.
    val attachmentUri: String? = null,

    // Optional odometer reading (km) at which this service is due, e.g.
    // "next oil change at 52000 km". Independent of triggerAtMillis — a
    // car-service reminder can be due by date OR by mileage, whichever
    // comes first, so both fields are checked.
    val mileageTargetKm: Long? = null,

    // Where a meeting/appointment takes place.
    val location: String? = null,

    // Bank/institution name — relevant for checks and installments/loans.
    val bankName: String? = null,

    // Comma-separated java.util.Calendar.DAY_OF_WEEK values (1=Sunday..7=Saturday),
    // used only when repeatInterval == WEEKLY. Null/blank means "repeat on
    // whatever day the original triggerAtMillis falls on" (old behavior).
    val repeatDaysOfWeek: String? = null
)
