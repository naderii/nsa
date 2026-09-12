package ir.naderinia.nsa.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.ui.theme.CategoryColors
import ir.naderinia.nsa.util.resolveContact
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderScreen(
    knownCategories: List<String>,
    onSave: (
        title: String,
        note: String,
        category: String,
        categoryColor: Long,
        triggerAtMillis: Long,
        repeatInterval: RepeatInterval,
        amount: Long?,
        counterparty: String?,
        counterpartyPhone: String?
    ) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("عمومی") }
    var categoryColor by remember { mutableStateOf(CategoryColors.first()) }
    var repeatInterval by remember { mutableStateOf(RepeatInterval.NONE) }
    var isFinancial by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var counterpartyPhone by remember { mutableStateOf<String?>(null) }

    val pickContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            resolveContact(context, it)?.let { picked ->
                counterparty = picked.displayName
                counterpartyPhone = picked.phoneNumber
            }
        }
    }

    val requestContactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pickContactLauncher.launch(null)
    }

    fun pickFromContacts() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            pickContactLauncher.launch(null)
        } else {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val calendar = remember { Calendar.getInstance() }
    var triggerMillis by remember { mutableStateOf(calendar.timeInMillis) }
    val dateFormatter = remember { SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()) }

    var repeatMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("یادآوری جدید") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("توضیحات (اختیاری)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("دسته‌بندی") },
                modifier = Modifier.fillMaxWidth()
            )

            if (knownCategories.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(knownCategories) { existingCategory ->
                        AssistChip(
                            onClick = { category = existingCategory },
                            label = { Text(existingCategory) }
                        )
                    }
                }
            }

            Button(onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        calendar.set(Calendar.YEAR, year)
                        calendar.set(Calendar.MONTH, month)
                        calendar.set(Calendar.DAY_OF_MONTH, day)
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                calendar.set(Calendar.HOUR_OF_DAY, hour)
                                calendar.set(Calendar.MINUTE, minute)
                                calendar.set(Calendar.SECOND, 0)
                                triggerMillis = calendar.timeInMillis
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            true
                        ).show()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }) {
                Text("انتخاب تاریخ و ساعت: ${dateFormatter.format(triggerMillis)}")
            }

            ExposedDropdownMenuBox(
                expanded = repeatMenuExpanded,
                onExpandedChange = { repeatMenuExpanded = it }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = repeatInterval.persianLabel(),
                    onValueChange = {},
                    label = { Text("تکرار") },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = repeatMenuExpanded,
                    onDismissRequest = { repeatMenuExpanded = false }
                ) {
                    RepeatInterval.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.persianLabel()) },
                            onClick = {
                                repeatInterval = option
                                repeatMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isFinancial, onCheckedChange = { isFinancial = it })
                Text("این یک یادآوری مالی است (مبلغ/بدهی/طلب)")
            }

            if (isFinancial) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("مبلغ (تومان)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = counterparty,
                    onValueChange = {
                        counterparty = it
                        counterpartyPhone = null // typed manually, no longer tied to a contact
                    },
                    label = { Text("طرف حساب") },
                    trailingIcon = {
                        IconButton(onClick = { pickFromContacts() }) {
                            Icon(Icons.Default.Contacts, contentDescription = "انتخاب از مخاطبین")
                        }
                    },
                    supportingText = {
                        if (counterpartyPhone != null) {
                            Text("از مخاطبین: $counterpartyPhone")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        onSave(
                            title,
                            note,
                            category.ifBlank { "عمومی" },
                            categoryColor,
                            triggerMillis,
                            repeatInterval,
                            if (isFinancial) amountText.toLongOrNull() else null,
                            if (isFinancial) counterparty.ifBlank { null } else null,
                            if (isFinancial) counterpartyPhone else null
                        )
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("ذخیره")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("انصراف")
                }
            }
        }
    }
}

private fun RepeatInterval.persianLabel(): String = when (this) {
    RepeatInterval.NONE -> "بدون تکرار"
    RepeatInterval.DAILY -> "روزانه"
    RepeatInterval.WEEKLY -> "هفتگی"
    RepeatInterval.MONTHLY -> "ماهانه"
    RepeatInterval.YEARLY -> "سالانه"
}
