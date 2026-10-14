package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ir.naderinia.nsa.data.MedicationLog
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
    onAddClick: () -> Unit,
    medicationLogs: List<MedicationLog>,
    onMedicationTaken: (Reminder) -> Unit,
    onChecklistChange: (Reminder, String?) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("یادآوری جدید") }
            )
        }
    ) { padding ->
        // «عمومی» collects sub-categories (لیست خرید، ورزش، ...): let the user filter them.
        val subCategories = if (title == "عمومی") reminders.map { it.category }.distinct() else emptyList()
        var selectedSub by remember { mutableStateOf<String?>(null) }
        val activeSub = selectedSub?.takeIf { it in subCategories }
        val shown = if (activeSub == null) reminders else reminders.filter { it.category == activeSub }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (subCategories.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = activeSub == null,
                            onClick = { selectedSub = null },
                            label = { Text("همه") }
                        )
                    }
                    items(subCategories) { sub ->
                        FilterChip(
                            selected = activeSub == sub,
                            onClick = { selectedSub = if (activeSub == sub) null else sub },
                            label = { Text(sub) }
                        )
                    }
                }
            }
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            ReminderListContent(
                reminders = shown,
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
