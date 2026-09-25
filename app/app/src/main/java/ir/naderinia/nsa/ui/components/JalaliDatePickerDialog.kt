package ir.naderinia.nsa.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.util.JalaliCalendar
import java.util.Calendar

/**
 * Single unified Jalali date+time picker — replaces the old two-step flow
 * (Jalali date dialog, then a separate native Gregorian-styled time dialog)
 * that people found confusing. Year/month/day use scrollable chip rows
 * instead of long dropdown lists; hour/minute are plain numeric fields.
 */
@Composable
fun JalaliDateTimePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (millis: Long) -> Unit
) {
    val initialJalali = remember(initialMillis) { JalaliCalendar.millisToJalali(initialMillis) }
    val initialCal = remember(initialMillis) { Calendar.getInstance().apply { timeInMillis = initialMillis } }

    var year by remember { mutableStateOf(initialJalali.year) }
    var month by remember { mutableStateOf(initialJalali.month) }
    var day by remember { mutableStateOf(initialJalali.day) }
    var hourText by remember { mutableStateOf(initialCal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')) }
    var minuteText by remember { mutableStateOf(initialCal.get(Calendar.MINUTE).toString().padStart(2, '0')) }

    val maxDay = JalaliCalendar.daysInJalaliMonth(year, month)
    if (day > maxDay) day = maxDay

    val thisJalaliYear = remember { JalaliCalendar.millisToJalali(System.currentTimeMillis()).year }
    val yearRange = remember { (thisJalaliYear - 1)..(thisJalaliYear + 30) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ و ساعت (شمسی)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("سال", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(yearRange.toList()) { y ->
                        FilterChip(
                            selected = y == year,
                            onClick = { year = y },
                            label = { Text(JalaliCalendar.toPersianDigits(y.toString())) }
                        )
                    }
                }

                Text("ماه", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(JalaliCalendar.monthNames.size) { idx ->
                        FilterChip(
                            selected = (idx + 1) == month,
                            onClick = { month = idx + 1 },
                            label = { Text(JalaliCalendar.monthNames[idx]) }
                        )
                    }
                }

                Text("روز", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..maxDay).toList()) { d ->
                        FilterChip(
                            selected = d == day,
                            onClick = { day = d },
                            label = { Text(JalaliCalendar.toPersianDigits(d.toString())) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { v -> hourText = v.filter { it.isDigit() }.take(2) },
                        label = { Text("ساعت (۰-۲۳)") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { v -> minuteText = v.filter { it.isDigit() }.take(2) },
                        label = { Text("دقیقه") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hour = (hourText.toIntOrNull() ?: 0).coerceIn(0, 23)
                val minute = (minuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                onConfirm(JalaliCalendar.jalaliToMillis(year, month, day, hour, minute))
            }) { Text("تأیید") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
