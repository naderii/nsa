package ir.naderinia.nsa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ir.naderinia.nsa.NsaApplication
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.notification.NotificationScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReminderViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as NsaApplication).database.reminderDao()

    val reminders: StateFlow<List<Reminder>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addReminder(
        title: String,
        note: String,
        category: String,
        categoryColor: Long,
        triggerAtMillis: Long,
        repeatInterval: RepeatInterval,
        amount: Long? = null,
        counterparty: String? = null
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
                counterparty = counterparty
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
}
