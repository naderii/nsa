package ir.naderinia.nsa.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.util.JalaliCalendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JalaliDatePickerDialog(
    initialYear: Int,
    initialMonth: Int,
    initialDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (year: Int, month: Int, day: Int) -> Unit
) {
    var year by remember { mutableStateOf(initialYear) }
    var month by remember { mutableStateOf(initialMonth) }
    var day by remember { mutableStateOf(initialDay) }

    var yearMenuExpanded by remember { mutableStateOf(false) }
    var monthMenuExpanded by remember { mutableStateOf(false) }
    var dayMenuExpanded by remember { mutableStateOf(false) }

    val maxDay = JalaliCalendar.daysInJalaliMonth(year, month)
    if (day > maxDay) day = maxDay
    val yearRange = (initialYear - 1)..(initialYear + 10)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ (شمسی)") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = dayMenuExpanded,
                    onExpandedChange = { dayMenuExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = JalaliCalendar.toPersianDigits(day.toString()),
                        onValueChange = {},
                        label = { Text("روز") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = dayMenuExpanded, onDismissRequest = { dayMenuExpanded = false }) {
                        (1..maxDay).forEach { d ->
                            DropdownMenuItem(
                                text = { Text(JalaliCalendar.toPersianDigits(d.toString())) },
                                onClick = { day = d; dayMenuExpanded = false }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = monthMenuExpanded,
                    onExpandedChange = { monthMenuExpanded = it },
                    modifier = Modifier.weight(1.4f)
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = JalaliCalendar.monthNames[month - 1],
                        onValueChange = {},
                        label = { Text("ماه") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = monthMenuExpanded, onDismissRequest = { monthMenuExpanded = false }) {
                        JalaliCalendar.monthNames.forEachIndexed { index, name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { month = index + 1; monthMenuExpanded = false }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = yearMenuExpanded,
                    onExpandedChange = { yearMenuExpanded = it },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        readOnly = true,
                        value = JalaliCalendar.toPersianDigits(year.toString()),
                        onValueChange = {},
                        label = { Text("سال") },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                        yearRange.forEach { y ->
                            DropdownMenuItem(
                                text = { Text(JalaliCalendar.toPersianDigits(y.toString())) },
                                onClick = { year = y; yearMenuExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(year, month, day) }) { Text("تأیید") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
