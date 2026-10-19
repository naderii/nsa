package ir.naderinia.nsa.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import ir.naderinia.nsa.util.rememberLocalBitmap
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.ui.components.JalaliDateTimeField
import ir.naderinia.nsa.ui.components.JalaliDateTimePickerDialog
import ir.naderinia.nsa.ui.components.WeekDaySelector
import ir.naderinia.nsa.ui.theme.CategoryColors
import ir.naderinia.nsa.util.BabyCareGuide
import ir.naderinia.nsa.util.CarPrefs
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.util.IranHolidays
import ir.naderinia.nsa.data.isCritical
import ir.naderinia.nsa.util.PregnancyGuide
import androidx.compose.runtime.mutableStateListOf
import ir.naderinia.nsa.data.ChecklistItem
import ir.naderinia.nsa.data.FIXED_SECTION_CATEGORIES
import ir.naderinia.nsa.data.parseChecklist
import ir.naderinia.nsa.data.serializeChecklist
import ir.naderinia.nsa.util.grouped
import ir.naderinia.nsa.util.resolveContact
import java.io.File
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private enum class ReminderTemplate(val label: String, val fixedCategory: String?) {
    GENERAL("عمومی", null),
    VEHICLE_SERVICE("سرویس وسیله نقلیه", "وسیله نقلیه"),
    MEETING("جلسه", "جلسات"),
    PROPERTY("ساختمان", "ساختمان"),
    FINANCIAL("مالی", "مالی"),
    BIRTHDAY("تولد و سالگرد", "تولد و سالگرد"),
    MEDICATION("دارو و ویتامین", "دارو"),
    PREGNANCY("بارداری", "بارداری"),
    BABY_CARE("نوزاد و کودک", "نوزاد و کودک"),
    RELIGIOUS("مذهبی", "مذهبی"),
    SCHOOL("مدرسه", "مدرسه")
}

private val VEHICLE_SERVICE_BUILTIN_ITEMS = listOf("تعویض روغن", "تعویض لاستیک", "باتری", "بیمه", "معاینه فنی", "سرویس دوره‌ای")
private val PROPERTY_BUILTIN_ITEMS = listOf("سرویس کولر", "سرویس پکیج", "شارژ ساختمان", "تعمیرات", "بیمه ساختمان")
private val GENERAL_BUILTIN_CATEGORIES = listOf("قرار ملاقات", "ورزش", "لیست خرید", "کارهای خانه", "عادت روزانه", "کار اداری", "تماس تلفنی", "یادداشت")
private val BILL_BUILTIN_ITEMS = listOf("آب", "برق", "گاز", "اینترنت", "تلفن ثابت", "تلفن همراه", "تلویزیون/ماهواره", "شهرداری")
private val RELIGIOUS_BUILTIN_ITEMS = listOf(
    "دعای کمیل (شب جمعه)", "دعای ندبه (صبح جمعه)", "زیارت عاشورا",
    "دعای توسل", "نماز شب", "دعای صباح", "قرائت قرآن"
)
private val SCHOOL_BUILTIN_ITEMS = listOf(
    "جلسه اولیا و مربیان", "امتحان", "تحویل تکلیف", "ثبت‌نام", "خرید لوازم‌التحریر", "کلاس فوق‌برنامه"
)

private val IRAN_BANKS = listOf(
    "ملی", "ملت", "صادرات", "سپه", "تجارت", "رفاه", "کشاورزی", "مسکن",
    "پاسارگاد", "پارسیان", "سامان", "اقتصاد نوین", "کارآفرین", "سینا", "سرمایه",
    "شهر", "دی", "آینده", "پست‌بانک", "توسعه صادرات", "توسعه تعاون", "صنعت و معدن",
    "گردشگری", "ایران زمین", "قوامین", "خاورمیانه",
    "قرض‌الحسنه مهر ایران", "قرض‌الحسنه رسالت"
)

private fun templateForCategory(category: String?): ReminderTemplate =
    ReminderTemplate.entries.firstOrNull { it.fixedCategory != null && it.fixedCategory == category }
        ?: ReminderTemplate.GENERAL

/** Trim + unify Arabic ي/ك with Persian ی/ک so «لیست خرید» typed two ways stays one category. */
private fun normalizeCategory(text: String): String =
    text.replace('ي', 'ی').replace('ك', 'ک').trim()

private fun formatToman(text: String): String? =
    text.toLongOrNull()?.takeIf { it > 0 }?.let { NumberFormat.getInstance(Locale.US).format(it) + " تومان" }

private fun inferTemplate(reminder: Reminder?, initialCategory: String? = null): ReminderTemplate {
    if (reminder == null) return templateForCategory(initialCategory)
    return when {
        reminder.mileageTargetKm != null -> ReminderTemplate.VEHICLE_SERVICE
        reminder.location != null -> ReminderTemplate.MEETING
        reminder.financialType != null -> ReminderTemplate.FINANCIAL
        reminder.category == "ساختمان" -> ReminderTemplate.PROPERTY
        reminder.category == "تولد و سالگرد" -> ReminderTemplate.BIRTHDAY
        reminder.category == "دارو" -> ReminderTemplate.MEDICATION
        reminder.category == "بارداری" -> ReminderTemplate.PREGNANCY
        reminder.category == "نوزاد و کودک" -> ReminderTemplate.BABY_CARE
        reminder.category == "مذهبی" -> ReminderTemplate.RELIGIOUS
        reminder.category == "مدرسه" -> ReminderTemplate.SCHOOL
        else -> templateForCategory(reminder.category)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderScreen(
    knownCategories: List<String>,
    knownItemsByCategory: Map<String, List<String>> = emptyMap(),
    editingReminder: Reminder? = null,
    initialCategory: String? = null,
    initialFinancialType: FinancialType? = null,
    onSave: (
        title: String,
        note: String,
        category: String,
        categoryColor: Long,
        triggerAtMillis: Long,
        repeatInterval: RepeatInterval,
        amount: Long?,
        counterparty: String?,
        counterpartyPhone: String?,
        financialType: FinancialType?,
        attachmentUri: String?,
        mileageTargetKm: Long?,
        location: String?,
        bankName: String?,
        repeatDaysOfWeek: String?,
        birthYear: Int?,
        checklist: String?,
        critical: Boolean
    ) -> Unit,
    onSaveInstallments: (
        title: String,
        note: String,
        category: String,
        categoryColor: Long,
        firstTriggerMillis: Long,
        count: Int,
        amountEach: Long?,
        counterparty: String?,
        counterpartyPhone: String?,
        attachmentUri: String?,
        bankName: String?
    ) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var template by remember(editingReminder) { mutableStateOf(inferTemplate(editingReminder, initialCategory)) }
    var critical by remember(editingReminder) { mutableStateOf(editingReminder?.isCritical() ?: false) }
    var title by remember(editingReminder) { mutableStateOf(editingReminder?.title ?: "") }
    var note by remember(editingReminder) { mutableStateOf(editingReminder?.note ?: "") }
    var category by remember(editingReminder) { mutableStateOf(editingReminder?.category ?: templateForCategory(initialCategory).fixedCategory ?: "عمومی") }
    var categoryColor by remember(editingReminder) { mutableStateOf(editingReminder?.categoryColor ?: CategoryColors.first()) }
    var repeatInterval by remember(editingReminder) {
        mutableStateOf(
            editingReminder?.repeatInterval ?: when (templateForCategory(initialCategory)) {
                ReminderTemplate.BIRTHDAY -> RepeatInterval.YEARLY
                ReminderTemplate.MEDICATION -> RepeatInterval.DAILY
                else -> RepeatInterval.NONE
            }
        )
    }
    var financialType by remember(editingReminder) { mutableStateOf(editingReminder?.financialType ?: initialFinancialType ?: FinancialType.DEBT) }
    var amountText by remember(editingReminder) { mutableStateOf(editingReminder?.amount?.toString() ?: "") }
    var counterparty by remember(editingReminder) { mutableStateOf(editingReminder?.counterparty ?: "") }
    var counterpartyPhone by remember(editingReminder) { mutableStateOf(editingReminder?.counterpartyPhone) }
    var bankName by remember(editingReminder) { mutableStateOf(editingReminder?.bankName ?: "") }
    var installmentCountText by remember(editingReminder) { mutableStateOf("") }
    var birthYearText by remember(editingReminder) { mutableStateOf(editingReminder?.birthYear?.toString() ?: "") }
    val checklistItems = remember(editingReminder) {
        mutableStateListOf<ChecklistItem>().apply { addAll(parseChecklist(editingReminder?.checklist)) }
    }
    var newChecklistItem by remember(editingReminder) { mutableStateOf("") }
    var selectedWeekDays by remember(editingReminder) {
        mutableStateOf(
            editingReminder?.repeatDaysOfWeek?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
        )
    }
    var locationText by remember(editingReminder) { mutableStateOf(editingReminder?.location ?: "") }
    var mileageCurrentText by remember(editingReminder) {
        mutableStateOf(CarPrefs.getCurrentKm(context).takeIf { it > 0 }?.toString() ?: "")
    }
    var mileageTargetText by remember(editingReminder) { mutableStateOf(editingReminder?.mileageTargetKm?.toString() ?: "") }
    var pregnancyMonth by remember(editingReminder) { mutableStateOf(1) }
    var babyAgeMonth by remember(editingReminder) { mutableStateOf(0) }
    var pregnancyMenuExpanded by remember { mutableStateOf(false) }
    var babyMenuExpanded by remember { mutableStateOf(false) }
    var attachmentUri by remember(editingReminder) {
        mutableStateOf(editingReminder?.attachmentUri?.let { Uri.parse(it) })
    }

    val attachmentScope = androidx.compose.runtime.rememberCoroutineScope()
    var previousAttachmentUri by remember { mutableStateOf<Uri?>(null) }

    val takeAttachmentPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (!success) {
            // Camera cancelled: don't leave an empty .jpg behind in our storage.
            attachmentUri?.lastPathSegment?.let { File(File(context.filesDir, "attachments"), it).delete() }
            attachmentUri = previousAttachmentUri
        }
    }

    val pickAttachmentFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { picked ->
            // Copy the file into the app's own storage. A content:// link to another app's file
            // dies when that file moves/is deleted and can't be put into a backup.
            attachmentScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val ext = android.webkit.MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(context.contentResolver.getType(picked)) ?: "bin"
                val dir = File(context.filesDir, "attachments").apply { mkdirs() }
                val target = File(dir, "attachment_${System.currentTimeMillis()}.$ext")
                val copied = runCatching {
                    context.contentResolver.openInputStream(picked)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    } != null
                }.getOrDefault(false)
                if (copied) {
                    attachmentUri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", target
                    )
                } else {
                    target.delete()
                }
            }
        }
    }

    fun captureAttachmentPhoto() {
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val file = File(dir, "attachment_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        previousAttachmentUri = attachmentUri
        attachmentUri = uri
        takeAttachmentPhotoLauncher.launch(uri)
    }

    val pickContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            resolveContact(context, uri)?.let { picked ->
                counterparty = picked.displayName
                counterpartyPhone = picked.phoneNumber
            }
        }
    }

    fun pickFromContacts() {
        // Picking a specific phone-number entry via the system Contacts app's
        // own picker UI needs no READ_CONTACTS permission — only reading the
        // full contacts list yourself would require it.
        val intent = android.content.Intent(
            android.content.Intent.ACTION_PICK,
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        )
        pickContactLauncher.launch(intent)
    }

    val calendar = remember(editingReminder) {
        Calendar.getInstance().apply {
            if (editingReminder != null) {
                timeInMillis = editingReminder.triggerAtMillis
            } else {
                // New reminder: default to one hour from now (a default of "right now" is already
                // in the past when saved).
                add(Calendar.HOUR_OF_DAY, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
    }
    var triggerMillis by remember(editingReminder) { mutableStateOf(calendar.timeInMillis) }
    var showJalaliDatePicker by remember { mutableStateOf(false) }

    var repeatMenuExpanded by remember { mutableStateOf(false) }
    var financialTypeMenuExpanded by remember { mutableStateOf(false) }
    var templateMenuExpanded by remember { mutableStateOf(false) }

    // Moves the date to "today + offset" but keeps the time of day that's already picked.
    fun setDayFromToday(offsetDays: Int) {
        val picked = Calendar.getInstance().apply { timeInMillis = triggerMillis }
        triggerMillis = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, offsetDays)
            set(Calendar.HOUR_OF_DAY, picked.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, picked.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val installmentCount = installmentCountText.toIntOrNull() ?: 1
    val isInstallmentBatch = template == ReminderTemplate.FINANCIAL &&
        financialType == FinancialType.INSTALLMENT &&
        editingReminder == null &&
        installmentCount > 1

    Scaffold(topBar = { TopAppBar(title = { Text(if (editingReminder != null) "ویرایش یادآوری" else "یادآوری جدید") }) }) { padding ->
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

            ExposedDropdownMenuBox(
                expanded = templateMenuExpanded,
                onExpandedChange = { templateMenuExpanded = it }
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = template.label,
                    onValueChange = {},
                    label = { Text("نوع") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = templateMenuExpanded,
                    onDismissRequest = { templateMenuExpanded = false }
                ) {
                    ReminderTemplate.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                template = option
                                option.fixedCategory?.let { category = it }
                                // Back to «عمومی» from a fixed section: drop that section's category,
                                // otherwise the reminder silently stays inside it.
                                if (option == ReminderTemplate.GENERAL && category in FIXED_SECTION_CATEGORIES) {
                                    category = "عمومی"
                                }
                                when (option) {
                                    ReminderTemplate.BIRTHDAY -> repeatInterval = RepeatInterval.YEARLY
                                    ReminderTemplate.MEDICATION -> repeatInterval = RepeatInterval.DAILY
                                    else -> {}
                                }
                                templateMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // ---- Template-specific fields ----
            when (template) {
                ReminderTemplate.GENERAL -> {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("دسته‌بندی") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    val generalSuggestions = (GENERAL_BUILTIN_CATEGORIES + knownCategories)
                        .filter { it !in FIXED_SECTION_CATEGORIES && it != "قبض" }
                        .distinct()
                    if (generalSuggestions.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(generalSuggestions) { existingCategory ->
                                AssistChip(
                                    onClick = { category = existingCategory },
                                    label = { Text(existingCategory) }
                                )
                            }
                        }
                    }
                }

                ReminderTemplate.BIRTHDAY -> {
                    Text(
                        "تکرار روی «سالانه» تنظیم شد — هر سال همین روز بهت یادآوری می‌شه.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = birthYearText,
                        onValueChange = { birthYearText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("سال تولد (اختیاری، مثلاً ۱۳۷۰)") },
                        supportingText = { Text("اگه بنویسی، سن و شمارش معکوس تولد توی لیست نشون داده می‌شه") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ReminderTemplate.MEDICATION -> {
                    Text(
                        "تکرار روی «روزانه» تنظیم شد — دوز یا نکات خاص رو توی توضیحات بنویس.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ReminderTemplate.PREGNANCY -> {
                    ExposedDropdownMenuBox(
                        expanded = pregnancyMenuExpanded,
                        onExpandedChange = { pregnancyMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = "ماه $pregnancyMonth بارداری",
                            onValueChange = {},
                            label = { Text("ماه بارداری") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = pregnancyMenuExpanded, onDismissRequest = { pregnancyMenuExpanded = false }) {
                            (1..9).forEach { m ->
                                DropdownMenuItem(text = { Text("ماه $m") }, onClick = { pregnancyMonth = m; pregnancyMenuExpanded = false })
                            }
                        }
                    }
                    val suggestions = PregnancyGuide.milestonesByMonth[pregnancyMonth].orEmpty()
                    if (suggestions.isNotEmpty()) {
                        Text("پیشنهاد این ماه", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(suggestions) { item ->
                                AssistChip(onClick = { title = item }, label = { Text(item) })
                            }
                        }
                    }
                    Text(PregnancyGuide.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                ReminderTemplate.BABY_CARE -> {
                    ExposedDropdownMenuBox(
                        expanded = babyMenuExpanded,
                        onExpandedChange = { babyMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = if (babyAgeMonth == 0) "بدو تولد" else "$babyAgeMonth ماهگی",
                            onValueChange = {},
                            label = { Text("سن نوزاد") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = babyMenuExpanded, onDismissRequest = { babyMenuExpanded = false }) {
                            listOf(0, 2, 4, 6, 9, 12, 15, 18, 24).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(if (m == 0) "بدو تولد" else "$m ماهگی") },
                                    onClick = { babyAgeMonth = m; babyMenuExpanded = false }
                                )
                            }
                        }
                    }
                    val suggestions = BabyCareGuide.milestonesByMonth[babyAgeMonth].orEmpty()
                    if (suggestions.isNotEmpty()) {
                        Text("پیشنهاد این سن", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(suggestions) { item ->
                                AssistChip(onClick = { title = item }, label = { Text(item) })
                            }
                        }
                    }
                    Text(BabyCareGuide.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                ReminderTemplate.RELIGIOUS -> {
                    Text("پیشنهاد", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RELIGIOUS_BUILTIN_ITEMS) { item ->
                            AssistChip(onClick = { title = item }, label = { Text(item) })
                        }
                    }
                    Text(
                        "این فهرست کامل نیست — هر ورد یا عمل دیگه‌ای رو خودت توی عنوان بنویس و با «تکرار هفتگی» روی روز دلخواه تنظیمش کن.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ReminderTemplate.VEHICLE_SERVICE -> {
                    val suggestions = (VEHICLE_SERVICE_BUILTIN_ITEMS + knownItemsByCategory["وسیله نقلیه"].orEmpty()).distinct()
                    Text("آیتم سرویس", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions) { item ->
                            AssistChip(onClick = { title = item }, label = { Text(item) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = mileageCurrentText,
                            onValueChange = { mileageCurrentText = it.filter { c -> c.isDigit() } },
                            label = { Text("کیلومتر فعلی") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = mileageTargetText,
                            onValueChange = { mileageTargetText = it.filter { c -> c.isDigit() } },
                            label = { Text("کیلومتر بعدی (هدف)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        label = { Text("مبلغ (تومان، اختیاری)") },
                        supportingText = { formatToman(amountText)?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ReminderTemplate.MEETING -> {
                    OutlinedTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        label = { Text("محل برگزاری") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ReminderTemplate.SCHOOL -> {
                    val suggestions = (SCHOOL_BUILTIN_ITEMS + knownItemsByCategory["مدرسه"].orEmpty()).distinct()
                    Text("آیتم", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions) { item ->
                            AssistChip(onClick = { title = item }, label = { Text(item) })
                        }
                    }
                    OutlinedTextField(
                        value = locationText,
                        onValueChange = { locationText = it },
                        label = { Text("محل (اختیاری — مثلاً نام مدرسه یا کلاس)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ReminderTemplate.PROPERTY -> {
                    val suggestions = (PROPERTY_BUILTIN_ITEMS + knownItemsByCategory["ساختمان"].orEmpty()).distinct()
                    Text("آیتم", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(suggestions) { item ->
                            AssistChip(onClick = { title = item }, label = { Text(item) })
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        label = { Text("مبلغ (تومان، اختیاری)") },
                        supportingText = { formatToman(amountText)?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                ReminderTemplate.FINANCIAL -> {
                    ExposedDropdownMenuBox(
                        expanded = financialTypeMenuExpanded,
                        onExpandedChange = { financialTypeMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            readOnly = true,
                            value = financialType.persianLabel(),
                            onValueChange = {},
                            label = { Text("نوع مالی") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = financialTypeMenuExpanded,
                            onDismissRequest = { financialTypeMenuExpanded = false }
                        ) {
                            FinancialType.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.persianLabel()) },
                                    onClick = {
                                        if (option != FinancialType.BILL && financialType == FinancialType.BILL &&
                                            editingReminder == null && repeatInterval == RepeatInterval.MONTHLY
                                        ) {
                                            repeatInterval = RepeatInterval.NONE
                                        }
                                        financialType = option
                                        // A bill is almost always monthly — start there (still changeable below).
                                        if (option == FinancialType.BILL && editingReminder == null &&
                                            repeatInterval == RepeatInterval.NONE
                                        ) {
                                            repeatInterval = RepeatInterval.MONTHLY
                                        }
                                        financialTypeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    if (financialType == FinancialType.BILL && repeatInterval != RepeatInterval.NONE) {
                        Text(
                            "قبض تکراری: تا وقتی پرداخت نکنی باز می‌مونه، و بعد از پرداخت خودکار به دوره‌ی بعد می‌ره (همون مبلغ؛ هر وقت قبض جدید اومد مبلغ رو ویرایش کن).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        label = {
                            Text(if (financialType == FinancialType.INSTALLMENT) "مبلغ هر قسط (تومان)" else "مبلغ (تومان)")
                        },
                        supportingText = { formatToman(amountText)?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (financialType == FinancialType.INSTALLMENT && editingReminder == null) {
                        OutlinedTextField(
                            value = installmentCountText,
                            onValueChange = { installmentCountText = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("تعداد اقساط (اختیاری — مثلاً ۱۲)") },
                            supportingText = {
                                val each = amountText.toLongOrNull()
                                when {
                                    installmentCount > 60 -> Text("حداکثر ۶۰ قسط")
                                    isInstallmentBatch && each != null && each > 0 ->
                                        Text("جمع کل: ${(each * installmentCount).grouped()} تومان — $installmentCount یادآوری ماهانه از تاریخ پایین ساخته می‌شه")
                                    isInstallmentBatch ->
                                        Text("$installmentCount یادآوری ماهانه از تاریخ پایین ساخته می‌شه")
                                    else -> Text("خالی بذاری فقط یه قسط ثبت می‌شه")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (financialType == FinancialType.BILL) {
                        Text("نوع قبض", style = MaterialTheme.typography.titleSmall)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(BILL_BUILTIN_ITEMS) { item ->
                                AssistChip(onClick = { title = item }, label = { Text(item) })
                            }
                        }
                    }

                    if (financialType == FinancialType.CHECK || financialType == FinancialType.INSTALLMENT) {
                        OutlinedTextField(
                            value = bankName,
                            onValueChange = { bankName = it },
                            label = { Text("بانک (از لیست انتخاب کن یا بنویس)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(IRAN_BANKS) { bank ->
                                FilterChip(
                                    selected = bankName == bank,
                                    onClick = { bankName = if (bankName == bank) "" else bank },
                                    label = { Text(bank) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = counterparty,
                        onValueChange = {
                            counterparty = it
                            counterpartyPhone = null
                        },
                        label = { Text("طرف حساب") },
                        trailingIcon = {
                            IconButton(onClick = { pickFromContacts() }) {
                                Icon(Icons.Default.Contacts, contentDescription = "انتخاب از مخاطبین")
                            }
                        },
                        supportingText = {
                            if (counterpartyPhone != null) Text("از مخاطبین: $counterpartyPhone")
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("توضیحات (اختیاری)") },
                modifier = Modifier.fillMaxWidth()
            )

            JalaliDateTimeField(
                millis = triggerMillis,
                onClick = { showJalaliDatePicker = true }
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("امروز" to 0, "فردا" to 1, "۳ روز بعد" to 3, "هفته بعد" to 7)) { (label, offset) ->
                    AssistChip(onClick = { setDayFromToday(offset) }, label = { Text(label) })
                }
            }

            // Cheque / installment / bill due on a holiday, Friday or Thursday: banks are closed.
            if (template == ReminderTemplate.FINANCIAL) {
                val closedWarning = remember(triggerMillis) { IranHolidays.dueWarning(triggerMillis) }
                if (closedWarning != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "⚠ $closedWarning",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val before = remember(triggerMillis) { IranHolidays.previousBankDay(triggerMillis) }
                                val after = remember(triggerMillis) { IranHolidays.nextBankDay(triggerMillis) }
                                TextButton(onClick = { triggerMillis = before }) {
                                    Text("روز کاری قبل (${JalaliCalendar.formatDate(before)})")
                                }
                                TextButton(onClick = { triggerMillis = after }) {
                                    Text("روز کاری بعد (${JalaliCalendar.formatDate(after)})")
                                }
                            }
                        }
                    }
                }
            }

            if (showJalaliDatePicker) {
                JalaliDateTimePickerDialog(
                    initialMillis = triggerMillis,
                    onDismiss = { showJalaliDatePicker = false },
                    onConfirm = { millis ->
                        triggerMillis = millis
                        showJalaliDatePicker = false
                    }
                )
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
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
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

            if (repeatInterval == RepeatInterval.WEEKLY) {
                Text("روزهای تکرار (اختیاری، می‌تونی چندتا انتخاب کنی)", style = MaterialTheme.typography.titleSmall)
                WeekDaySelector(
                    selected = selectedWeekDays,
                    onSelectedChange = { selectedWeekDays = it }
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("حیاتی ❗", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "اگه به هشدار جواب ندی، تا ۴ بار با صدای بلندتر تکرار می‌شه",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = critical, onCheckedChange = { critical = it })
            }

            Text("چک‌لیست (اختیاری)", style = MaterialTheme.typography.titleSmall)
            checklistItems.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = item.done,
                        onCheckedChange = { checked -> checklistItems[index] = item.copy(done = checked) }
                    )
                    Text(item.text, modifier = Modifier.weight(1f))
                    TextButton(onClick = { checklistItems.removeAt(index) }) { Text("حذف") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newChecklistItem,
                    onValueChange = { newChecklistItem = it },
                    label = { Text("آیتم جدید") },
                    singleLine = true,
                    // Enter on the keyboard adds the item and keeps the field ready for the next one.
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            val text = newChecklistItem.trim()
                            if (text.isNotEmpty()) {
                                checklistItems.add(ChecklistItem(text, false))
                                newChecklistItem = ""
                            }
                        }
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val text = newChecklistItem.trim()
                        if (text.isNotEmpty()) {
                            checklistItems.add(ChecklistItem(text, false))
                            newChecklistItem = ""
                        }
                    }
                ) { Text("افزودن") }
            }

            Text("پیوست (اختیاری) — عکس یا سند", style = MaterialTheme.typography.titleSmall)
            if (attachmentUri == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { captureAttachmentPhoto() }) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("گرفتن عکس")
                    }
                    OutlinedButton(onClick = {
                        pickAttachmentFileLauncher.launch(arrayOf("image/*", "application/pdf"))
                    }) {
                        Text("انتخاب فایل")
                    }
                }
            } else {
                val uri = attachmentUri!!
                val isImage = context.contentResolver.getType(uri)?.startsWith("image/") == true ||
                    uri.toString().endsWith(".jpg", ignoreCase = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isImage) {
                        val thumbnail by rememberLocalBitmap(uri, 256)
                        val loaded = thumbnail
                        if (loaded != null) {
                            Image(
                                bitmap = loaded,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Spacer(modifier = Modifier.size(64.dp))
                        }
                    } else {
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(48.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("پیوست اضافه شد", modifier = Modifier.weight(1f))
                    IconButton(onClick = { attachmentUri = null }) {
                        Icon(Icons.Default.Close, contentDescription = "حذف پیوست")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (template == ReminderTemplate.VEHICLE_SERVICE) {
                            mileageCurrentText.toLongOrNull()?.let { CarPrefs.setCurrentKm(context, it) }
                        }
                        if (isInstallmentBatch) onSaveInstallments(
                            title.trim(),
                            note,
                            normalizeCategory(category).ifBlank { "عمومی" },
                            categoryColor,
                            triggerMillis,
                            installmentCount,
                            amountText.toLongOrNull(),
                            counterparty.ifBlank { null },
                            counterpartyPhone,
                            attachmentUri?.toString(),
                            bankName.ifBlank { null }
                        ) else onSave(
                            title.trim(),
                            note,
                            normalizeCategory(category).ifBlank { "عمومی" },
                            categoryColor,
                            triggerMillis,
                            repeatInterval,
                            if (template == ReminderTemplate.FINANCIAL || template == ReminderTemplate.VEHICLE_SERVICE || template == ReminderTemplate.PROPERTY) amountText.toLongOrNull() else null,
                            if (template == ReminderTemplate.FINANCIAL) counterparty.ifBlank { null } else null,
                            if (template == ReminderTemplate.FINANCIAL) counterpartyPhone else null,
                            if (template == ReminderTemplate.FINANCIAL) financialType else null,
                            attachmentUri?.toString(),
                            if (template == ReminderTemplate.VEHICLE_SERVICE) mileageTargetText.toLongOrNull() else null,
                            if (template == ReminderTemplate.MEETING) locationText.ifBlank { null } else null,
                            if (template == ReminderTemplate.FINANCIAL &&
                                (financialType == FinancialType.CHECK || financialType == FinancialType.INSTALLMENT)
                            ) bankName.ifBlank { null } else null,
                            if (repeatInterval == RepeatInterval.WEEKLY && selectedWeekDays.isNotEmpty()) {
                                selectedWeekDays.joinToString(",")
                            } else null,
                            if (template == ReminderTemplate.BIRTHDAY) {
                                birthYearText.toIntOrNull()?.takeIf { it in 1200..1500 }
                            } else null,
                            serializeChecklist(
                                checklistItems.toList() +
                                    (newChecklistItem.trim().takeIf { it.isNotEmpty() }
                                        ?.let { listOf(ChecklistItem(it, false)) } ?: emptyList())
                            ),
                            critical
                        )
                    },
                    enabled = title.isNotBlank() && installmentCount <= 60,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            editingReminder != null -> "به‌روزرسانی"
                            isInstallmentBatch -> "ذخیره‌ی $installmentCount قسط"
                            else -> "ذخیره"
                        }
                    )
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

private fun FinancialType.persianLabel(): String = when (this) {
    FinancialType.DEBT -> "بدهی من"
    FinancialType.CREDIT -> "طلب من"
    FinancialType.CHECK -> "چک"
    FinancialType.INSTALLMENT -> "قسط یا وام"
    FinancialType.BILL -> "قبض"
}
