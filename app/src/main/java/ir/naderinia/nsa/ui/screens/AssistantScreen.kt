package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.util.AskAnswer
import ir.naderinia.nsa.util.AskEngine
import ir.naderinia.nsa.util.IranHolidays
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.util.ParsedReminder
import ir.naderinia.nsa.util.QuickParser
import ir.naderinia.nsa.util.grouped
import java.util.Calendar

private val CREATE_EXAMPLES = listOf(
    "فردا ساعت ۵ عصر جلسه با آقای احمدی",
    "قسط ۱۲ میلیونی بانک ملی ۱۲ قسط از ۵ آذر",
    "چک ۴۰ میلیون ۲۰ آذر به نام رضایی",
    "هر روز ساعت ۸ صبح قرص فشار",
    "تعویض روغن ماشین ۵۰ هزار کیلومتر"
)

private const val HALF_DAY = 12 * 3_600_000L

private fun weekdayName(dow: Int): String = when (dow) {
    Calendar.SATURDAY -> "شنبه"
    Calendar.SUNDAY -> "یکشنبه"
    Calendar.MONDAY -> "دوشنبه"
    Calendar.TUESDAY -> "سه‌شنبه"
    Calendar.WEDNESDAY -> "چهارشنبه"
    Calendar.THURSDAY -> "پنجشنبه"
    else -> "جمعه"
}

private fun repeatLabel(p: ParsedReminder): String = when (p.repeatInterval) {
    RepeatInterval.NONE -> ""
    RepeatInterval.DAILY -> "هر روز"
    RepeatInterval.WEEKLY -> {
        val days = p.repeatDaysOfWeek?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        if (days.isEmpty()) "هر هفته" else "هر " + days.joinToString(" و ") { weekdayName(it) }
    }
    RepeatInterval.MONTHLY -> "هر ماه"
    RepeatInterval.YEARLY -> "هر سال"
}

private fun typeLabel(t: FinancialType): String = when (t) {
    FinancialType.CHECK -> "چک"
    FinancialType.INSTALLMENT -> "قسط / وام"
    FinancialType.BILL -> "قبض"
    FinancialType.DEBT -> "بدهی من"
    FinancialType.CREDIT -> "طلب من"
}

private fun fa(s: String) = JalaliCalendar.toPersianDigits(s)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    reminders: List<Reminder>,
    onCreate: (ParsedReminder) -> Unit,
    onOpenReminder: (Reminder) -> Unit,
    onBack: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    // null = decide by the sentence, true = force «بپرس», false = force «یادآوری جدید»
    var forcedAsk by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var answer by remember { mutableStateOf<AskAnswer?>(null) }
    val savedTitles = remember { mutableStateListOf<String>() }

    val isAsk = forcedAsk ?: QuickParser.looksLikeQuestion(text)

    fun ask(q: String) {
        if (q.isNotBlank()) answer = AskEngine.answer(q, reminders)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دستیار", fontWeight = FontWeight.Bold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    answer = null
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                label = { Text("بنویس چی رو یادت بندازم، یا یه سؤال بپرس") },
                placeholder = { Text("مثلاً: فردا ساعت ۵ عصر جلسه با آقای احمدی") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (isAsk) ask(text) }),
                trailingIcon = {
                    if (isAsk) {
                        IconButton(onClick = { ask(text) }, enabled = text.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "بپرس")
                        }
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    }
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isAsk,
                    onClick = { forcedAsk = false },
                    label = { Text("یادآوری جدید") }
                )
                FilterChip(
                    selected = isAsk,
                    onClick = { forcedAsk = true },
                    label = { Text("بپرس") }
                )
            }

            if (savedTitles.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "ثبت شد: " + savedTitles.takeLast(3).joinToString("، ") { "«$it»" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            when {
                text.isBlank() -> Examples(
                    onPickCreate = { forcedAsk = false; text = it },
                    onPickAsk = { forcedAsk = true; text = it; ask(it) }
                )

                isAsk -> {
                    val a = answer
                    if (a == null) {
                        Button(onClick = { ask(text) }, modifier = Modifier.fillMaxWidth()) { Text("بپرس") }
                    } else {
                        AnswerCard(a, reminders, onOpenReminder, onPickAsk = { forcedAsk = true; text = it; ask(it) })
                    }
                }

                else -> {
                    val parsed = remember(text) { QuickParser.parse(text) }
                    if (parsed != null) {
                        PreviewCard(
                            key = text,
                            parsed = parsed,
                            onSave = { finalParsed ->
                                onCreate(finalParsed)
                                savedTitles += finalParsed.title
                                text = ""
                                answer = null
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Examples(onPickCreate: (String) -> Unit, onPickAsk: (String) -> Unit) {
    Text("یادآوری بساز:", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CREATE_EXAMPLES.forEach { ex -> AssistChip(onClick = { onPickCreate(ex) }, label = { Text(ex) }) }
    }
    Text("یا بپرس:", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AskEngine.EXAMPLES.forEach { ex -> AssistChip(onClick = { onPickAsk(ex) }, label = { Text(ex) }) }
    }
    Text(
        "همه‌چیز روی خود گوشی و بدون اینترنت انجام می‌شه.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewCard(key: String, parsed: ParsedReminder, onSave: (ParsedReminder) -> Unit) {
    // Corrections the person makes on the preview; they reset whenever the sentence changes.
    var triggerOverride by remember(key) { mutableStateOf<Long?>(null) }
    var shifted by remember(key) { mutableStateOf(false) }
    var criticalOverride by remember(key) { mutableStateOf<Boolean?>(null) }

    val base = triggerOverride ?: parsed.triggerAtMillis
    val hourOfBase = Calendar.getInstance().apply { timeInMillis = base }.get(Calendar.HOUR_OF_DAY)
    val delta = if (hourOfBase < 12) HALF_DAY else -HALF_DAY
    val trigger = base + if (shifted) delta else 0L
    val critical = criticalOverride ?: parsed.critical

    val dow = Calendar.getInstance().apply { timeInMillis = trigger }.get(Calendar.DAY_OF_WEEK)
    val moneyType = parsed.financialType != null && parsed.financialType != FinancialType.CREDIT
    val closedWarning = if (moneyType) IranHolidays.dueWarning(trigger) else null
    val dayStatus = IranHolidays.status(trigger)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("پیش‌نمایش — هنوز ذخیره نشده", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(parsed.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SuggestionChip(onClick = {}, label = { Text("📅 ${weekdayName(dow)} ${JalaliCalendar.formatDateTime(trigger)}") })
                repeatLabel(parsed).takeIf { it.isNotEmpty() }?.let { SuggestionChip(onClick = {}, label = { Text("🔁 $it") }) }
                if (parsed.category != "عمومی") SuggestionChip(onClick = {}, label = { Text("📂 ${parsed.category}") })
                parsed.financialType?.let { SuggestionChip(onClick = {}, label = { Text("💳 ${typeLabel(it)}") }) }
                parsed.amount?.let { SuggestionChip(onClick = {}, label = { Text("💰 ${fa(it.grouped())} تومان") }) }
                parsed.counterparty?.let { SuggestionChip(onClick = {}, label = { Text("👤 $it") }) }
                parsed.bankName?.let { SuggestionChip(onClick = {}, label = { Text("🏦 بانک ${it}") }) }
                parsed.mileageTargetKm?.let { SuggestionChip(onClick = {}, label = { Text("🛣 ${fa(it.grouped())} کیلومتر") }) }
                if (parsed.installmentCount > 1) {
                    val total = parsed.amount?.let { " (جمع ${fa((it * parsed.installmentCount).grouped())})" } ?: ""
                    SuggestionChip(onClick = {}, label = { Text("🔢 ${fa(parsed.installmentCount.toString())} قسط ماهانه$total") })
                }
                parsed.birthYear?.let { SuggestionChip(onClick = {}, label = { Text("🎂 متولد ${fa(it.toString())}") }) }
            }

            if (parsed.timeInferred) {
                TextButton(onClick = { shifted = !shifted }) {
                    Text(if (shifted) "ساعت رو برگردون به حالت اول" else "ساعت رو ۱۲ ساعت جابه‌جا کن (صبح ↔ عصر)")
                }
            }

            parsed.warnings.forEach {
                Text("ℹ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }

            if (closedWarning != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("⚠ $closedWarning", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val before = IranHolidays.previousBankDay(trigger)
                            val after = IranHolidays.nextBankDay(trigger)
                            TextButton(onClick = { triggerOverride = before; shifted = false }) { Text("روز کاری قبل (${JalaliCalendar.formatDate(before)})") }
                            TextButton(onClick = { triggerOverride = after; shifted = false }) { Text("روز کاری بعد (${JalaliCalendar.formatDate(after)})") }
                        }
                    }
                }
            } else if (dayStatus.isOffDay && !moneyType) {
                Text(
                    "ℹ این روز " + (if (dayStatus.kind == IranHolidays.Kind.HOLIDAY) "تعطیل رسمیه (${dayStatus.name})" else "جمعه‌ست"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("حیاتی ❗", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "اگه جواب ندی، تا ۴ بار با صدای بلندتر تکرار می‌شه",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = critical, onCheckedChange = { criticalOverride = it })
            }

            Button(
                onClick = { onSave(parsed.copy(triggerAtMillis = trigger, critical = critical)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (parsed.installmentCount > 1) "ذخیره‌ی ${fa(parsed.installmentCount.toString())} قسط" else "ذخیره‌ی یادآوری") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerCard(
    answer: AskAnswer,
    reminders: List<Reminder>,
    onOpenReminder: (Reminder) -> Unit,
    onPickAsk: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(answer.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            answer.lines.forEachIndexed { index, line ->
                val id = answer.reminderIds.getOrNull(index)?.takeIf { !line.startsWith("⚠") }
                val target = id?.let { rid -> reminders.firstOrNull { it.id == rid } }
                Text(
                    "• $line",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = if (target != null) Modifier.fillMaxWidth().clickable { onOpenReminder(target) } else Modifier.fillMaxWidth()
                )
            }
            if (answer.reminderIds.isNotEmpty()) {
                Text(
                    "روی هر مورد بزنی بازش می‌کنم.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (!answer.understood) {
                Text("این‌جوری بپرس:", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AskEngine.EXAMPLES.forEach { ex -> AssistChip(onClick = { onPickAsk(ex) }, label = { Text(ex) }) }
                }
            }
        }
    }
}
