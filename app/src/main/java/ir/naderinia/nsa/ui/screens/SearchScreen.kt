package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.MedicationLog
import ir.naderinia.nsa.data.Reminder

/** Arabic ي/ك and Persian ی/ک are the same to a person typing — treat them alike. */
private fun normalize(text: String): String =
    text.trim().lowercase()
        .replace('ي', 'ی')
        .replace('ك', 'ک')

private fun Reminder.matches(query: String): Boolean =
    listOfNotNull(title, note, category, counterparty, location, bankName)
        .any { normalize(it).contains(query) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    reminders: List<Reminder>,
    onToggleDone: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onRecordPayment: (Reminder, Long) -> Unit,
    onEdit: (Reminder) -> Unit,
    medicationLogs: List<MedicationLog>,
    onMedicationTaken: (Reminder) -> Unit,
    onChecklistChange: (Reminder, String?) -> Unit,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val normalizedQuery = normalize(query)
    val results = if (normalizedQuery.isBlank()) emptyList()
    else reminders.filter { it.matches(normalizedQuery) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("جست‌وجو در یادآوری‌ها…") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                normalizedQuery.isBlank() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "عنوان، توضیحات، دسته، طرف حساب، محل یا بانک رو بنویس",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                results.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "چیزی پیدا نشد",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }

                else -> ReminderListContent(
                    reminders = results,
                    onToggleDone = onToggleDone,
                    onDelete = onDelete,
                    onRecordPayment = onRecordPayment,
                    onEdit = onEdit,
                    medicationLogs = medicationLogs,
                    onMedicationTaken = onMedicationTaken,
                    onChecklistChange = onChecklistChange
                )
            }
        }
    }
}
