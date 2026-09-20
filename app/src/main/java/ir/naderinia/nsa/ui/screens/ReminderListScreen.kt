package ir.naderinia.nsa.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.ui.theme.onSuccessContainerColor
import ir.naderinia.nsa.ui.theme.successContainerColor
import ir.naderinia.nsa.util.CarPrefs
import ir.naderinia.nsa.util.JalaliCalendar
import java.util.Calendar

private enum class ReminderGroup(val label: String) {
    OVERDUE("عقب‌افتاده"),
    TODAY("امروز"),
    TOMORROW("فردا"),
    THIS_WEEK("این هفته"),
    LATER("بعداً"),
    DONE("انجام‌شده")
}

/** Three-tier urgency used for card color-coding, independent of grouping. */
private enum class Urgency { RED, YELLOW, GREEN, NEUTRAL }

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun groupFor(reminder: Reminder): ReminderGroup {
    if (reminder.isDone) return ReminderGroup.DONE

    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = reminder.triggerAtMillis }

    return when {
        reminder.triggerAtMillis < System.currentTimeMillis() -> ReminderGroup.OVERDUE
        isSameDay(now, target) -> ReminderGroup.TODAY
        isSameDay((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }, target) -> ReminderGroup.TOMORROW
        target.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            target.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR) -> ReminderGroup.THIS_WEEK
        else -> ReminderGroup.LATER
    }
}

/** Mileage overrun (car service due by km) always escalates to RED, even if the date is far away. */
private fun urgencyFor(reminder: Reminder, group: ReminderGroup, currentKm: Long): Urgency {
    if (reminder.isDone) return Urgency.NEUTRAL
    val mileageDue = reminder.mileageTargetKm != null && currentKm >= reminder.mileageTargetKm
    return when {
        group == ReminderGroup.OVERDUE || mileageDue -> Urgency.RED
        group == ReminderGroup.TODAY || group == ReminderGroup.TOMORROW -> Urgency.YELLOW
        else -> Urgency.GREEN
    }
}

/**
 * Pure content — no Scaffold/TopAppBar/FAB of its own. This is embedded as
 * a tab inside HomeScreen, which owns the single shared Scaffold (top bar +
 * bottom nav bar + the "add" FAB).
 */
@Composable
fun ReminderListContent(
    reminders: List<Reminder>,
    onToggleDone: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onRecordPayment: (Reminder, Long) -> Unit,
    onEdit: (Reminder) -> Unit
) {
    val context = LocalContext.current
    val currentKm = remember { CarPrefs.getCurrentKm(context) }

    if (reminders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.EventAvailable,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "هنوز یادآوری‌ای ثبت نکردی",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "با دکمه‌ی پایین صفحه اولین یادآوریت رو بساز",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val grouped = reminders.groupBy { groupFor(it) }
    val orderedGroups = listOf(
        ReminderGroup.OVERDUE,
        ReminderGroup.TODAY,
        ReminderGroup.TOMORROW,
        ReminderGroup.THIS_WEEK,
        ReminderGroup.LATER,
        ReminderGroup.DONE
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        orderedGroups.forEach { group ->
            val items = grouped[group].orEmpty()
            if (items.isNotEmpty()) {
                item(key = "header_${group.name}") {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (group == ReminderGroup.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(items, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        urgency = urgencyFor(reminder, group, currentKm),
                        onToggleDone = onToggleDone,
                        onDelete = onDelete,
                        onRecordPayment = onRecordPayment,
                        onEdit = onEdit
                    )
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    urgency: Urgency,
    onToggleDone: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onRecordPayment: (Reminder, Long) -> Unit,
    onEdit: (Reminder) -> Unit
) {
    val context = LocalContext.current
    var showPaymentDialog by remember { mutableStateOf(false) }

    val containerColor = when (urgency) {
        Urgency.RED -> MaterialTheme.colorScheme.errorContainer
        Urgency.YELLOW -> MaterialTheme.colorScheme.tertiaryContainer
        Urgency.GREEN -> successContainerColor()
        Urgency.NEUTRAL -> MaterialTheme.colorScheme.surface
    }
    val onContainerColor = when (urgency) {
        Urgency.RED -> MaterialTheme.colorScheme.onErrorContainer
        Urgency.YELLOW -> MaterialTheme.colorScheme.onTertiaryContainer
        Urgency.GREEN -> onSuccessContainerColor()
        Urgency.NEUTRAL -> Color.Unspecified
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reminder.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = onContainerColor,
                            textDecoration = if (reminder.isDone) TextDecoration.LineThrough else null
                        )
                        if (reminder.attachmentUri != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { openAttachment(context, reminder.attachmentUri) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = "باز کردن پیوست",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${reminder.category} • ${JalaliCalendar.formatDateTime(reminder.triggerAtMillis)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (reminder.mileageTargetKm != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "کیلومتر هدف: ${reminder.mileageTargetKm}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (reminder.location != null) {
                        Text(
                            text = "📍 ${reminder.location}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (reminder.amount != null) {
                        val remaining = reminder.amount - reminder.amountPaid
                        val financialLine = buildString {
                            append("مبلغ: ${reminder.amount} تومان")
                            reminder.counterparty?.let { append(" • $it") }
                            if (reminder.amountPaid > 0 && !reminder.isDone) {
                                append(" • باقی‌مانده: $remaining")
                            }
                        }
                        Text(text = financialLine, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Checkbox(checked = reminder.isDone, onCheckedChange = { onToggleDone(reminder) })
                IconButton(onClick = { onEdit(reminder) }) {
                    Icon(Icons.Default.Edit, contentDescription = "ویرایش")
                }
                IconButton(onClick = { onDelete(reminder) }) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف")
                }
            }

            if (reminder.financialType != null && !reminder.isDone) {
                TextButton(
                    onClick = { showPaymentDialog = true },
                    modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp)
                ) {
                    Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ثبت پرداخت")
                }
            }
        }
    }

    if (showPaymentDialog) {
        PaymentDialog(
            reminder = reminder,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amount ->
                onRecordPayment(reminder, amount)
                showPaymentDialog = false
            }
        )
    }
}

private fun openAttachment(context: android.content.Context, uriString: String) {
    try {
        val uri = Uri.parse(uriString)
        val mime = context.contentResolver.getType(uri) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "برنامه‌ای برای باز کردن این فایل پیدا نشد", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun PaymentDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val remaining = (reminder.amount ?: 0) - reminder.amountPaid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت پرداخت") },
        text = {
            Column {
                Text("باقی‌مانده فعلی: $remaining تومان")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("مبلغ پرداخت‌شده (تومان)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                amountText.toLongOrNull()?.let { onConfirm(it) }
            }) {
                Text("ثبت")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        }
    )
}
