package ir.naderinia.nsa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.naderinia.nsa.NsaApplication
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.notification.NotificationScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class FinancialSummary(
    val totalDebtRemaining: Long,
    val totalCreditRemaining: Long
) {
    val net: Long get() = totalCreditRemaining - totalDebtRemaining
}

data class DashboardStats(
    val todayItems: List<Reminder>,
    val overdueItems: List<Reminder>,
    val upcomingPayments: List<Reminder>, // due within 7 days, unpaid
    val thisMonthSpend: Long,
    val lastMonthSpend: Long
)

private fun monthRange(monthsAgo: Int): Pair<Long, Long> {
    val start = Calendar.getInstance().apply {
        add(Calendar.MONTH, -monthsAgo)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
    return start.timeInMillis to end.timeInMillis
}

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as NsaApplication).database.reminderDao()
    private val paymentLogDao = (application as NsaApplication).database.paymentLogDao()

    val reminders: StateFlow<List<Reminder>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val knownCategories: StateFlow<List<String>> = dao.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialReminders: StateFlow<List<Reminder>> = dao.observeFinancial()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val financialSummary: StateFlow<FinancialSummary> = dao.observeFinancial()
        .map { list ->
            val remaining = { r: Reminder -> (r.amount ?: 0) - r.amountPaid }
            FinancialSummary(
                totalDebtRemaining = list.filter { it.financialType == FinancialType.DEBT && !it.isDone }
                    .sumOf(remaining),
                totalCreditRemaining = list.filter { it.financialType == FinancialType.CREDIT && !it.isDone }
                    .sumOf(remaining)
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialSummary(0, 0))

    val dashboardStats: StateFlow<DashboardStats> = combine(
        dao.observeAll(),
        paymentLogDao.observeAll()
    ) { allReminders, logs ->
        val now = System.currentTimeMillis()
        val todayCal = Calendar.getInstance()

        val today = allReminders.filter { r ->
            !r.isDone && r.triggerAtMillis >= now && isSameDay(r.triggerAtMillis, todayCal)
        }
        val overdue = allReminders.filter { !it.isDone && it.triggerAtMillis < now }
        val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000
        val upcomingPayments = allReminders.filter {
            it.amount != null && !it.isDone &&
                it.triggerAtMillis in now..(now + sevenDaysMillis)
        }

        val (thisMonthStart, thisMonthEnd) = monthRange(0)
        val (lastMonthStart, lastMonthEnd) = monthRange(1)
        val thisMonthSpend = logs.filter { it.timestampMillis in thisMonthStart until thisMonthEnd }
            .sumOf { it.amount }
        val lastMonthSpend = logs.filter { it.timestampMillis in lastMonthStart until lastMonthEnd }
            .sumOf { it.amount }

        DashboardStats(
            todayItems = today,
            overdueItems = overdue,
            upcomingPayments = upcomingPayments,
            thisMonthSpend = thisMonthSpend,
            lastMonthSpend = lastMonthSpend
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardStats(emptyList(), emptyList(), emptyList(), 0, 0)
    )

    private fun isSameDay(millis: Long, reference: Calendar): Boolean {
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        return target.get(Calendar.YEAR) == reference.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) == reference.get(Calendar.DAY_OF_YEAR)
    }

    fun addReminder(
        title: String,
        note: String,
        category: String,
        categoryColor: Long,
        triggerAtMillis: Long,
        repeatInterval: RepeatInterval,
        amount: Long? = null,
        counterparty: String? = null,
        counterpartyPhone: String? = null,
        financialType: FinancialType? = null
    ) {
        viewModelScope.launch {
            val reminder = Reminder(
                title = title,
                note = note,
                category = category,
                categoryColor = categoryColor,
                triggerAtMillis = triggerAtMillis,
                repeatInterval = repeatInterval,
                amount = amount,
                counterparty = counterparty,
                counterpartyPhone = counterpartyPhone,
                financialType = financialType
            )
            val id = dao.upsert(reminder)
            NotificationScheduler.schedule(getApplication(), reminder.copy(id = id))
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            NotificationScheduler.cancel(getApplication(), reminder.id)
            dao.delete(reminder)
        }
    }

    fun toggleDone(reminder: Reminder) {
        viewModelScope.launch {
            dao.update(reminder.copy(isDone = !reminder.isDone))
        }
    }

    /**
     * Records a partial or full payment against a financial reminder, and
     * logs it with a timestamp so monthly spend reports (dashboard) can
     * compare this month against last month.
     */
    fun recordPayment(reminder: Reminder, paymentAmount: Long) {
        if (paymentAmount <= 0) return
        viewModelScope.launch {
            val newPaid = reminder.amountPaid + paymentAmount
            val fullyPaid = reminder.amount != null && newPaid >= reminder.amount
            dao.update(reminder.copy(amountPaid = newPaid, isDone = fullyPaid))
            paymentLogDao.insert(PaymentLog(reminderId = reminder.id, amount = paymentAmount))
            if (fullyPaid) {
                NotificationScheduler.cancel(getApplication(), reminder.id)
            }
        }
    }
}
