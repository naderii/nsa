package ir.naderinia.nsa.notification

import android.content.Context
import ir.naderinia.nsa.data.AppDatabase
import ir.naderinia.nsa.data.CheckStatus
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.Reminder

/**
 * "Done" on a one-time financial reminder (notification button, widget circle) means PAID:
 * the unpaid rest is logged as a payment (so reports/spend stay right) and the item is closed.
 * Just flipping isDone would leave amountPaid = 0 and no payment record.
 */
object FinancialSettle {

    suspend fun settle(context: Context, reminder: Reminder) {
        val db = AppDatabase.getInstance(context)
        val total = reminder.amount ?: reminder.amountPaid
        val remaining = total - reminder.amountPaid
        if (remaining > 0) {
            db.paymentLogDao().insert(PaymentLog(reminderId = reminder.id, amount = remaining))
        }
        val status = if (reminder.financialType == FinancialType.CHECK) {
            if (reminder.checkStatus == CheckStatus.CASHED) CheckStatus.CASHED else CheckStatus.PASSED
        } else reminder.checkStatus
        db.reminderDao().update(
            reminder.copy(amountPaid = total, isDone = true, checkStatus = status)
        )
        NotificationScheduler.cancel(context, reminder.id)
    }
}
