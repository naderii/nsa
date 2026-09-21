package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.naderinia.nsa.data.Reminder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    title: String,
    reminders: List<Reminder>,
    onToggleDone: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onRecordPayment: (Reminder, Long) -> Unit,
    onEdit: (Reminder) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            ReminderListContent(
                reminders = reminders,
                onToggleDone = onToggleDone,
                onDelete = onDelete,
                onRecordPayment = onRecordPayment,
                onEdit = onEdit
            )
        }
    }
}
