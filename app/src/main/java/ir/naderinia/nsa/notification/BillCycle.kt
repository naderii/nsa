package ir.naderinia.nsa.notification

import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.effectiveAnchorDay

/** Rolling a paid recurring bill (e.g. monthly electricity) over to its next cycle. */
object BillCycle {

    /** First due date after "now", stepping forward from the cycle that was just paid. */
    fun nextDue(reminder: Reminder): Long {
        val now = System.currentTimeMillis()
        var next = reminder.triggerAtMillis
        var guard = 0
        do {
            next = NotificationScheduler.nextOccurrence(
                next,
                reminder.repeatInterval,
                reminder.repeatDaysOfWeek,
                reminder.effectiveAnchorDay()
            ) ?: return reminder.triggerAtMillis
            guard++
        } while (next <= now && guard < 60)
        return next
    }

    /** Same bill, next cycle: new due date, nothing paid yet. The amount is kept as "last amount". */
    fun rolled(reminder: Reminder): Reminder =
        reminder.copy(
            triggerAtMillis = nextDue(reminder),
            repeatAnchorDay = reminder.effectiveAnchorDay(),
            amountPaid = 0,
            isDone = false,
            checkStatus = null
        )
}
