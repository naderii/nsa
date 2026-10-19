package ir.naderinia.nsa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.naderinia.nsa.NsaApplication
import ir.naderinia.nsa.data.CheckStatus
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.MedicationLog
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.data.effectiveAnchorDay
import ir.naderinia.nsa.data.isRecurringBill
import ir.naderinia.nsa.data.parseChecklist
import ir.naderinia.nsa.data.serializeChecklist
import ir.naderinia.nsa.notification.BillCycle
import ir.naderinia.nsa.notification.EscalationManager
import ir.naderinia.nsa.notification.NotificationScheduler
import ir.naderinia.nsa.util.FinanceAnalysis
import ir.naderinia.nsa.util.FinanceInsights
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.widget.NsaWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

data class MonthSpend(
    val label: String, // Jalali month name
    val amount: Long
)

data class DashboardStats(
    val todayItems: List<Reminder>,
    val overdueItems: List<Reminder>,
    val upcomingPayments: List<Reminder>, // due within 7 days, unpaid
    val thisMonthSpend: Long,
    val lastMonthSpend: Long,
    val todayTotal: Int = 0,              // everything scheduled for today, done or not
    val todayDone: Int = 0,
    val weekCount: Int = 0,               // not done, due within 7 days
    val weekChecks: Int = 0,              // unpaid cheques due within 7 days
    val nextItem: Reminder? = null,       // the very next not-done reminder
    val monthlySpend: List<MonthSpend> = emptyList(), // last 6 Jalali months, oldest first
    val medicationTakenToday: Int = 0,    // "خوردم" taps today
    val medicationExpectedToday: Int = 0, // daily medication reminders
    val medicationTakenWeek: Int = 0,     // "خوردم" taps in the last 7 days
    val nextBirthday: Reminder? = null    // nearest birthday within 30 days
)

sealed interface ReminderUiEvent {
    data object ReminderSaved : ReminderUiEvent
    data object ReminderUpdated : ReminderUiEvent
    data class ReminderDeleted(val reminder: Reminder) : ReminderUiEvent
    /** A repeating reminder was ticked: this round is done, the series keeps going. */
    data class RepeatingRoundDone(val title: String) : ReminderUiEvent
}

private fun anchorDayFor(interval: RepeatInterval, triggerAtMillis: Long): Int? =
    if (interval == RepeatInterval.MONTHLY || interval == RepeatInterval.YEARLY) {
        JalaliCalendar.millisToJalali(triggerAtMillis).day
    } else null

/** Start, end and name of the Jalali month [monthsAgo] months before the current one. */
private fun jalaliMonthRange(monthsAgo: Int): Triple<Long, Long, String> {
    val now = JalaliCalendar.millisToJalali(System.currentTimeMillis())
    val total = now.year * 12 + (now.month - 1) - monthsAgo
    val year = total / 12
    val month = total % 12 + 1
    val nextTotal = total + 1
    val nextYear = nextTotal / 12
    val nextMonth = nextTotal % 12 + 1

    return Triple(
        JalaliCalendar.jalaliToMillis(year, month, 1, 0, 0),
        JalaliCalendar.jalaliToMillis(nextYear, nextMonth, 1, 0, 0),
        JalaliCalendar.monthNames[month - 1]
    )
}

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val dao =
        (application as NsaApplication).database.reminderDao()

    private val paymentLogDao =
        (application as NsaApplication).database.paymentLogDao()

    private val medicationLogDao =
        (application as NsaApplication).database.medicationLogDao()

    private val _uiEvents = MutableSharedFlow<ReminderUiEvent>()

    val uiEvents = _uiEvents.asSharedFlow()

    val reminders: StateFlow<List<Reminder>> = dao.observeAll()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val knownCategories: StateFlow<List<String>> = dao.observeCategories()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    /** Previously used titles, grouped by category — powers the "آیتم" suggestion
     * chips per template (e.g. category "وسیله نقلیه" → "تعویض روغن", "تعویض لاستیک"...). */
    val knownItemsByCategory: StateFlow<Map<String, List<String>>> =
        dao.observeAll()
            .map { list ->
                list
                    .groupBy { it.category }
                    .mapValues { (_, items) ->
                        items
                            .map { it.title }
                            .distinct()
                    }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyMap()
            )

    /** Every payment ever recorded, newest first — powers the per-item payment history. */
    val paymentLogs: StateFlow<List<PaymentLog>> = paymentLogDao.observeAll()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    /** Every "خوردم" tap, newest first. */
    val medicationLogs: StateFlow<List<MedicationLog>> = medicationLogDao.observeAll()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val financialReminders: StateFlow<List<Reminder>> =
        dao.observeFinancial()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    val financialSummary: StateFlow<FinancialSummary> =
        dao.observeFinancial()
            .map { list ->
                val remaining = { r: Reminder ->
                    (r.amount ?: 0) - r.amountPaid
                }

                FinancialSummary(
                    totalDebtRemaining = list
                        .filter {
                            it.financialType == FinancialType.DEBT &&
                                !it.isDone
                        }
                        .sumOf(remaining),

                    totalCreditRemaining = list
                        .filter {
                            it.financialType == FinancialType.CREDIT &&
                                !it.isDone
                        }
                        .sumOf(remaining)
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                FinancialSummary(0, 0)
            )

    /** Re-evaluates "overdue / today / tomorrow" as time passes, even if the database does not change. */
    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    /**
     * Everything the finance screens and the dashboard need to "think": this month per type,
     * overdue, next 7 days, loans, 6-month forecast and smart insights (see FinanceInsights).
     */
    val financeAnalysis: StateFlow<FinanceAnalysis> = combine(
        dao.observeFinancial(),
        paymentLogDao.observeAll(),
        minuteTicker
    ) { financial, logs, _ ->
        FinanceInsights.analyze(financial, logs, System.currentTimeMillis())
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            FinanceAnalysis.EMPTY
        )

    val dashboardStats: StateFlow<DashboardStats> = combine(
        dao.observeAll(),
        paymentLogDao.observeAll(),
        medicationLogDao.observeAll()
    ) { allReminders, logs, medLogs ->

        val now = System.currentTimeMillis()
        val todayCal = Calendar.getInstance()

        val today = allReminders.filter { reminder ->
            !reminder.isDone &&
                reminder.triggerAtMillis >= now &&
                isSameDay(
                    reminder.triggerAtMillis,
                    todayCal
                )
        }

        val overdue = allReminders.filter {
            !it.isDone &&
                it.triggerAtMillis < now
        }

        val sevenDaysMillis =
            7L * 24 * 60 * 60 * 1000

        val upcomingPayments = allReminders.filter {
            it.financialType != null &&
                it.financialType != FinancialType.CREDIT &&
                !it.isDone &&
                it.triggerAtMillis in
                now..(now + sevenDaysMillis)
        }

        // "طلب من" payments are money coming IN — they are not spending.
        // Only payments of existing, non-credit financial items count: logs left behind by a
        // deleted reminder (e.g. a deleted credit) would otherwise be counted as spending.
        val spendIds = allReminders
            .filter { it.financialType != null && it.financialType != FinancialType.CREDIT }
            .map { it.id }
            .toSet()

        val monthlySpend = (5 downTo 0)
            .map { jalaliMonthRange(it) }
            .map { (start, end, label) ->
                MonthSpend(
                    label = label,
                    amount = logs
                        .filter { it.reminderId in spendIds && it.timestampMillis in start until end }
                        .sumOf { it.amount }
                )
            }

        val medications = allReminders.filter { it.category == "دارو" && !it.isDone }
        val medicationIds = allReminders.filter { it.category == "دارو" }.map { it.id }.toSet()
        val medTaken = medLogs.filter { it.reminderId in medicationIds }

        val scheduledToday = allReminders.filter {
            isSameDay(it.triggerAtMillis, todayCal)
        }

        val weekItems = allReminders.filter {
            !it.isDone &&
                it.triggerAtMillis in now..(now + sevenDaysMillis)
        }

        DashboardStats(
            todayItems = today,
            overdueItems = overdue,
            upcomingPayments = upcomingPayments,
            thisMonthSpend = monthlySpend.last().amount,
            lastMonthSpend = monthlySpend[monthlySpend.size - 2].amount,
            todayTotal = scheduledToday.size,
            todayDone = scheduledToday.count { it.isDone },
            weekCount = weekItems.size,
            weekChecks = upcomingPayments.count {
                it.financialType == FinancialType.CHECK
            },
            nextItem = allReminders
                .filter { !it.isDone && it.triggerAtMillis >= now }
                .minByOrNull { it.triggerAtMillis },
            monthlySpend = monthlySpend,
            medicationTakenToday = medTaken.count { isSameDay(it.takenAtMillis, todayCal) },
            medicationExpectedToday = medications.count { it.repeatInterval == RepeatInterval.DAILY },
            medicationTakenWeek = medTaken.count { it.takenAtMillis >= now - sevenDaysMillis },
            nextBirthday = allReminders
                .filter {
                    it.category == "تولد و سالگرد" &&
                        !it.isDone &&
                        it.triggerAtMillis in now..(now + 30L * 24 * 60 * 60 * 1000)
                }
                .minByOrNull { it.triggerAtMillis }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardStats(
            emptyList(),
            emptyList(),
            emptyList(),
            0,
            0
        )
    )

    private fun isSameDay(
        millis: Long,
        reference: Calendar
    ): Boolean {
        val target = Calendar.getInstance().apply {
            timeInMillis = millis
        }

        return target.get(Calendar.YEAR) ==
            reference.get(Calendar.YEAR) &&
            target.get(Calendar.DAY_OF_YEAR) ==
            reference.get(Calendar.DAY_OF_YEAR)
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
        financialType: FinancialType? = null,
        attachmentUri: String? = null,
        mileageTargetKm: Long? = null,
        location: String? = null,
        bankName: String? = null,
        repeatDaysOfWeek: String? = null,
        birthYear: Int? = null,
        checklist: String? = null,
        priority: Int = 0
    ) {
        viewModelScope.launch {
            @Suppress("NAME_SHADOWING")
            val triggerAtMillis = NotificationScheduler.alignToWeekdays(
                triggerAtMillis, repeatInterval, repeatDaysOfWeek
            )


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
                financialType = financialType,
                attachmentUri = attachmentUri,
                mileageTargetKm = mileageTargetKm,
                location = location,
                bankName = bankName,
                repeatDaysOfWeek = repeatDaysOfWeek,
                birthYear = birthYear,
                checklist = checklist,
                repeatAnchorDay = anchorDayFor(repeatInterval, triggerAtMillis),
                priority = priority
            )

            val id = dao.upsert(reminder)

            NotificationScheduler.schedule(
                getApplication(),
                reminder.copy(id = id)
            )

            NsaWidgetProvider.updateAll(
                getApplication()
            )

            _uiEvents.emit(
                ReminderUiEvent.ReminderSaved
            )
        }
    }

    /**
     * Creates [count] monthly installment reminders ("… — قسط ۳ از ۱۲"), the first
     * one due at [firstTriggerMillis] and each next one a Jalali month later.
     */
    fun addInstallments(
        title: String,
        note: String,
        category: String,
        categoryColor: Long,
        firstTriggerMillis: Long,
        count: Int,
        amountEach: Long?,
        counterparty: String?,
        counterpartyPhone: String?,
        attachmentUri: String?,
        bankName: String?
    ) {
        viewModelScope.launch {

            for (i in 0 until count) {

                val reminder = Reminder(
                    title = "$title — قسط ${i + 1} از $count",
                    note = note,
                    category = category,
                    categoryColor = categoryColor,
                    triggerAtMillis = JalaliCalendar.addMonths(firstTriggerMillis, i),
                    repeatInterval = RepeatInterval.NONE,
                    amount = amountEach,
                    counterparty = counterparty,
                    counterpartyPhone = counterpartyPhone,
                    financialType = FinancialType.INSTALLMENT,
                    attachmentUri = attachmentUri,
                    bankName = bankName
                )

                val id = dao.upsert(reminder)

                NotificationScheduler.schedule(
                    getApplication(),
                    reminder.copy(id = id)
                )
            }

            NsaWidgetProvider.updateAll(
                getApplication()
            )

            _uiEvents.emit(
                ReminderUiEvent.ReminderSaved
            )
        }
    }

    /**
     * Sets a cheque's status. "Passed" / "cashed" settles the cheque: the
     * remaining amount is logged as a payment (so the dashboard's monthly spend
     * stays right) and the alarm is cancelled. "Bounced" keeps it open.
     */
    fun setCheckStatus(reminder: Reminder, status: CheckStatus?) {
        viewModelScope.launch {
            EscalationManager.acknowledge(getApplication(), reminder.id)

            val settled =
                status == CheckStatus.PASSED || status == CheckStatus.CASHED

            if (settled) {

                val total = reminder.amount ?: reminder.amountPaid
                val remaining = total - reminder.amountPaid

                if (remaining > 0) {
                    paymentLogDao.insert(
                        PaymentLog(
                            reminderId = reminder.id,
                            amount = remaining
                        )
                    )
                }

                dao.update(
                    reminder.copy(
                        checkStatus = status,
                        amountPaid = total,
                        isDone = true
                    )
                )

                NotificationScheduler.cancel(
                    getApplication(),
                    reminder.id
                )

            } else {

                // Was it settled before (passed/cashed)? Then the payment that settling logged
                // never really happened (e.g. bounced) → take it back so reports stay true.
                val base = if (reminder.isDone) undoLastPayment(reminder) else reminder
                val reopened = base.copy(
                    checkStatus = status,
                    isDone = false
                )
                dao.update(reopened)

                // It was settled (alarm cancelled) and is open again → ring again if still ahead.
                if (reopened.triggerAtMillis > System.currentTimeMillis()) {
                    NotificationScheduler.schedule(getApplication(), reopened)
                }
            }

            NsaWidgetProvider.updateAll(
                getApplication()
            )
        }
    }

    /** Removes the most recent payment of [reminder] (log + amountPaid) and returns the corrected copy. */
    private suspend fun undoLastPayment(reminder: Reminder): Reminder {
        val last = paymentLogDao.lastForReminder(reminder.id) ?: return reminder
        paymentLogDao.deleteById(last.id)
        return reminder.copy(amountPaid = (reminder.amountPaid - last.amount).coerceAtLeast(0L))
    }

    /**
     * Updates an existing reminder in place (same id), keeping its current
     * isDone/amountPaid state, and reschedules its alarm since the date or
     * repeat interval may have changed.
     */
    fun updateReminder(
        id: Long,
        title: String,
        note: String,
        category: String,
        categoryColor: Long,
        triggerAtMillis: Long,
        repeatInterval: RepeatInterval,
        amount: Long?,
        counterparty: String?,
        counterpartyPhone: String?,
        financialType: FinancialType?,
        attachmentUri: String?,
        mileageTargetKm: Long?,
        location: String?,
        bankName: String?,
        repeatDaysOfWeek: String?,
        birthYear: Int? = null,
        checklist: String? = null,
        priority: Int? = null
    ) {
        viewModelScope.launch {

            val existing = dao.getById(id)
                ?: return@launch

            @Suppress("NAME_SHADOWING")
            val triggerAtMillis = NotificationScheduler.alignToWeekdays(
                triggerAtMillis, repeatInterval, repeatDaysOfWeek
            )

            // A finished reminder whose date is moved into the future (or a paid item whose amount
            // was raised) must come back to life — otherwise it stays "done" and never rings again.
            val movedAhead = existing.isDone &&
                triggerAtMillis != existing.triggerAtMillis &&
                triggerAtMillis > System.currentTimeMillis()
            val fullyPaid = financialType != null && amount != null && existing.amountPaid >= amount
            val reopen = existing.isDone && !fullyPaid &&
                (movedAhead || (financialType != null && amount != null))

            val updated = existing.copy(
                isDone = if (reopen) false else existing.isDone,
                title = title,
                note = note,
                category = category,
                categoryColor = categoryColor,
                triggerAtMillis = triggerAtMillis,
                repeatInterval = repeatInterval,
                amount = amount,
                counterparty = counterparty,
                counterpartyPhone = counterpartyPhone,
                financialType = financialType,
                attachmentUri = attachmentUri,
                mileageTargetKm = mileageTargetKm,
                location = location,
                bankName = bankName,
                repeatDaysOfWeek = repeatDaysOfWeek,
                birthYear = birthYear,
                checklist = checklist,
                priority = priority ?: existing.priority,
                // Same date + same repeat → keep the original anchor (the stored trigger may
                // already be a shortened-month date); otherwise anchor to the newly picked day.
                repeatAnchorDay = if (existing.triggerAtMillis == triggerAtMillis &&
                    existing.repeatInterval == repeatInterval
                ) existing.effectiveAnchorDay()
                else anchorDayFor(repeatInterval, triggerAtMillis)
            )

            dao.update(updated)

            NotificationScheduler.cancel(
                getApplication(),
                id
            )

            if (!updated.isDone) {
                NotificationScheduler.schedule(
                    getApplication(),
                    updated
                )
            }

            NsaWidgetProvider.updateAll(
                getApplication()
            )

            _uiEvents.emit(
                ReminderUiEvent.ReminderUpdated
            )
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            NotificationScheduler.cancel(getApplication(), reminder.id)
    
            dao.delete(reminder)
    
            NsaWidgetProvider.updateAll(getApplication())
    
            _uiEvents.emit(
                ReminderUiEvent.ReminderDeleted(reminder)
            )
        }
    }
    
    fun restoreReminder(reminder: Reminder) {
    viewModelScope.launch {
        dao.upsert(reminder)

        if (!reminder.isDone) {
            NotificationScheduler.schedule(
                getApplication(),
                reminder
            )
        }

        NsaWidgetProvider.updateAll(
            getApplication()
        )
    }
    }

    fun toggleDone(reminder: Reminder) {
        viewModelScope.launch {
            EscalationManager.acknowledge(getApplication(), reminder.id)

            if (!reminder.isDone && reminder.isRecurringBill()) {
                // Ticking a repeating bill means "this month's is paid": log what was still unpaid
                // (otherwise reports, the monthly spend and the forecast never see this payment),
                // then move to the next cycle.
                val unpaid = (reminder.amount ?: 0L) - reminder.amountPaid
                if (unpaid > 0) {
                    paymentLogDao.insert(PaymentLog(reminderId = reminder.id, amount = unpaid))
                }
                val rolled = BillCycle.rolled(reminder)
                dao.update(rolled)
                NotificationScheduler.schedule(getApplication(), rolled)
                NsaWidgetProvider.updateAll(getApplication())
                return@launch
            }

            if (!reminder.isDone && reminder.repeatInterval != RepeatInterval.NONE) {
                // A repeating reminder (weekly shopping list, daily habit...) must not die
                // when ticked: "this round is done" → start the checklist over, stay active.
                val reset = parseChecklist(reminder.checklist).map { it.copy(done = false) }
                val now = System.currentTimeMillis()
                val dueLaterToday = reminder.triggerAtMillis > now &&
                    JalaliCalendar.millisToJalali(reminder.triggerAtMillis) == JalaliCalendar.millisToJalali(now)

                if (dueLaterToday && reminder.category != "دارو") {
                    // Same as the widget's circle: it was done ahead of time, so today's alarm is
                    // skipped and the series continues with its next date. (Medication is excluded:
                    // silently dropping a dose reminder is worse than a duplicate alert.)
                    val anchor = reminder.effectiveAnchorDay()
                    val next = NotificationScheduler.nextFutureOccurrence(
                        reminder.triggerAtMillis, reminder.repeatInterval, reminder.repeatDaysOfWeek, anchor
                    )
                    if (next != null) {
                        val advanced = reminder.copy(
                            triggerAtMillis = next,
                            repeatAnchorDay = anchor,
                            checklist = serializeChecklist(reset)
                        )
                        dao.update(advanced)
                        NotificationScheduler.schedule(getApplication(), advanced)
                    }
                } else {
                    dao.updateChecklist(reminder.id, serializeChecklist(reset))
                }
                NsaWidgetProvider.updateAll(getApplication())
                _uiEvents.emit(ReminderUiEvent.RepeatingRoundDone(reminder.title))
                return@launch
            }

            if (reminder.financialType != null && reminder.repeatInterval == RepeatInterval.NONE) {
                // Ticking a debt / cheque / installment means "paid"; un-ticking takes that back.
                if (!reminder.isDone) {
                    if (reminder.financialType == FinancialType.CHECK) {
                        setCheckStatus(reminder, CheckStatus.PASSED)
                        return@launch
                    }
                    val remaining = (reminder.amount ?: 0L) - reminder.amountPaid
                    if (remaining > 0) {
                        recordPayment(reminder, remaining)
                        return@launch
                    }
                } else {
                    val reopened = undoLastPayment(reminder).copy(
                        isDone = false,
                        checkStatus = if (reminder.financialType == FinancialType.CHECK) null else reminder.checkStatus
                    )
                    dao.update(reopened)
                    if (reopened.triggerAtMillis > System.currentTimeMillis()) {
                        NotificationScheduler.schedule(getApplication(), reopened)
                    }
                    NsaWidgetProvider.updateAll(getApplication())
                    return@launch
                }
            }

            val toggled = reminder.copy(isDone = !reminder.isDone)
            dao.update(toggled)

            // A ticked reminder must not still ring; un-ticking a future one re-arms it.
            if (toggled.isDone) {
                NotificationScheduler.cancel(getApplication(), reminder.id)
            } else if (toggled.triggerAtMillis > System.currentTimeMillis() ||
                toggled.repeatInterval != RepeatInterval.NONE
            ) {
                NotificationScheduler.schedule(getApplication(), toggled)
            }

            NsaWidgetProvider.updateAll(
                getApplication()
            )
        }
    }

    /**
     * Records a partial or full payment against a financial reminder, and
     * logs it with a timestamp so monthly spend reports (dashboard) can
     * compare this month against last month.
     */
    fun recordPayment(
        reminder: Reminder,
        paymentAmount: Long
    ) {
        if (paymentAmount <= 0) return

        viewModelScope.launch {
            EscalationManager.acknowledge(getApplication(), reminder.id)

            val newPaid =
                reminder.amountPaid + paymentAmount

            val fullyPaid =
                reminder.amount != null &&
                    newPaid >= reminder.amount

            paymentLogDao.insert(
                PaymentLog(
                    reminderId = reminder.id,
                    amount = paymentAmount
                )
            )

            if (fullyPaid && reminder.isRecurringBill()) {
                // Repeating bill fully paid → roll to next month, unpaid, same amount.
                val rolled = BillCycle.rolled(reminder.copy(amountPaid = newPaid))
                dao.update(rolled)
                NotificationScheduler.schedule(getApplication(), rolled)
                NsaWidgetProvider.updateAll(getApplication())
                return@launch
            }

            dao.update(
                reminder.copy(
                    amountPaid = newPaid,
                    isDone = fullyPaid
                )
            )

            if (fullyPaid) {
                NotificationScheduler.cancel(
                    getApplication(),
                    reminder.id
                )
            }

            NsaWidgetProvider.updateAll(
                getApplication()
            )
        }
    }

    /** The "خوردم" button on a medication reminder. */
    fun markMedicationTaken(reminder: Reminder) {
        viewModelScope.launch {
            EscalationManager.acknowledge(getApplication(), reminder.id)
            medicationLogDao.insert(MedicationLog(reminderId = reminder.id))
        }
    }

    /** Tick/untick/add/remove items of the checklist inside a reminder. */
    fun updateChecklist(reminder: Reminder, checklist: String?) {
        viewModelScope.launch {
            // Targeted UPDATE: copying the whole (possibly stale) row would overwrite a trigger time
            // the alarm receiver advanced in the background.
            dao.updateChecklist(reminder.id, checklist)
        }
    }
}
