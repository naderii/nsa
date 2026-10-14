package ir.naderinia.nsa.data

import androidx.room.Entity
import androidx.room.Index
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

enum class CheckStatus {
    PASSED,  // پاس شد
    BOUNCED, // برگشت خورد
    CASHED   // نقد شد
}

fun CheckStatus.persianLabel(): String = when (this) {
    CheckStatus.PASSED -> "پاس شد"
    CheckStatus.BOUNCED -> "برگشت خورد"
    CheckStatus.CASHED -> "نقد شد"
}

@Entity(
    tableName = "reminders",
    indices = [
        Index("category"),
        Index("financialType"),
        Index("triggerAtMillis")
    ]
)
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
    val repeatDaysOfWeek: String? = null,

    // Only meaningful when financialType == CHECK. null = still pending.
    val checkStatus: CheckStatus? = null,

    // Jalali birth year, used on birthday reminders to show "turns 35".
    val birthYear: Int? = null,

    // Checklist inside the reminder, one item per line as "0|text" / "1|text"
    // (0 = open, 1 = done). See Checklist.kt.
    val checklist: String? = null,

    // Jalali day-of-month a MONTHLY / YEARLY reminder is anchored to. Without it a reminder on
    // the 31st drifts: 31 → 30 (short month) and then stays on the 30th forever.
    // null = not anchored yet (old rows) → the current trigger day is used.
    val repeatAnchorDay: Int? = null
)

/** The Jalali day a monthly/yearly reminder must keep landing on; null for other intervals. */
fun Reminder.effectiveAnchorDay(): Int? =
    if (repeatInterval == RepeatInterval.MONTHLY || repeatInterval == RepeatInterval.YEARLY) {
        repeatAnchorDay ?: ir.naderinia.nsa.util.JalaliCalendar.millisToJalali(triggerAtMillis).day
    } else null

/**
 * Categories that have their own section tile on the overview screen.
 * Anything else typed under the «عمومی» template (لیست خرید، ورزش، ...) is a
 * sub-category of the general section.
 */
val FIXED_SECTION_CATEGORIES = setOf(
    "وسیله نقلیه", "جلسات", "ساختمان", "مالی", "تولد و سالگرد",
    "دارو", "بارداری", "نوزاد و کودک", "مذهبی", "مدرسه"
)

/** Which overview tile (section) this reminder belongs to. */
fun Reminder.sectionKey(): String = when {
    financialType != null -> "مالی"
    category in FIXED_SECTION_CATEGORIES -> category
    else -> "عمومی"
}

/** A bill that repeats (e.g. monthly electricity): it stays open until paid, then rolls to the next cycle. */
fun Reminder.isRecurringBill(): Boolean =
    financialType == FinancialType.BILL && repeatInterval != RepeatInterval.NONE