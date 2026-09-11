package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.Reminder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderListScreen(
    reminders: List<Reminder>,
    onAddClick: () -> Unit,
    onToggleDone: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("یادآوری‌های من") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "افزودن یادآوری")
            }
        }
    ) { padding ->
        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("هنوز یادآوری‌ای ثبت نکردی. با دکمه‌ی + شروع کن.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(reminders, key = { it.id }) { reminder ->
                    ReminderCard(reminder, onToggleDone, onDelete)
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    onToggleDone: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    val formatter = remember(reminder.id) { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(reminder.categoryColor))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (reminder.isDone) TextDecoration.LineThrough else null
                )
                Text(
                    text = "${reminder.category} • ${formatter.format(Date(reminder.triggerAtMillis))}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (reminder.amount != null) {
                    Text(
                        text = "مبلغ: ${reminder.amount} تومان" +
                            (reminder.counterparty?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Checkbox(checked = reminder.isDone, onCheckedChange = { onToggleDone(reminder) })
            IconButton(onClick = { onDelete(reminder) }) {
                Icon(Icons.Default.Delete, contentDescription = "حذف")
            }
        }
    }
}
