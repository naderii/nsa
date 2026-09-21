package ir.naderinia.nsa.ui.screens

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.ui.components.JalaliDateTimePickerDialog
import ir.naderinia.nsa.ui.theme.CategoryColors
import ir.naderinia.nsa.util.BabyCareGuide
import ir.naderinia.nsa.util.CarPrefs
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.util.PregnancyGuide
import ir.naderinia.nsa.util.resolveContact
import java.io.File
import java.util.Calendar

private enum class ReminderTemplate(val label: String, val fixedCategory: String?) {
    GENERAL("عمومی", null),
    CAR_SERVICE("سرویس خودرو", "ماشین"),
    MEETING("جلسه", "جلسات"),
    PROPERTY("ساختمان", "ساختمان"),
    FINANCIAL("مالی", "مالی"),
    BIRTHDAY("تولد و سالگرد", "تولد و سالگرد"),
    MEDICATION("دارو و ویتامین", "دارو"),
    PREGNANCY("بارداری", "بارداری"),
    BABY_CARE("نوزاد و کودک", "نوزاد و کودک"),
    RELIGIOUS("مذهبی", "مذهبی")
}

private val CAR_SERVICE_BUILTIN_ITEMS = listOf("تعویض روغن", "تعویض لاستیک", "باتری", "بیمه", "معاینه فنی", "سرویس دوره‌ای")
private val PROPERTY_BUILTIN_ITEMS = listOf("سرویس کولر", "سرویس پکیج", "شارژ ساختمان", "تعمیرات", "بیمه ساختمان")
private val GENERAL_BUILTIN_CATEGORIES = listOf("قرار ملاقات", "ورزش", "لیست خرید", "کارهای خانه", "عادت روزانه", "کار اداری", "تماس تلفنی", "یادداشت")
private val BILL_BUILTIN_ITEMS = listOf("آب", "برق", "گاز", "اینترنت", "تلفن ثابت", "تلفن همراه", "تلویزیون/ماهواره", "شهرداری")
private val RELIGIOUS_BUILTIN_ITEMS = listOf(
    "دعای کمیل (شب جمعه)", "دعای ندبه (صبح جمعه)", "زیارت عاشورا",
    "دعای توسل", "نماز شب", "دعای صباح", "قرائت قرآن"
)
private val PERSIAN_WEEK_DAYS = listOf(
    Calendar.SATURDAY to "شنبه",
    Calendar.SUNDAY to "یکشنبه",
    Calendar.MONDAY to "دوشنبه",
    Calendar.TUESDAY to "سه‌شنبه",
    Calendar.WEDNESDAY to "چهارشنبه",
    Calendar.THURSDAY to "پنجشنبه",
    Calendar.FRIDAY to "جمعه"
)

private fun inferTemplate(reminder: Reminder?): ReminderTemplate {
    if (reminder == null) return ReminderTemplate.GENERAL
    return when {
        reminder.mileageTargetKm != null -> ReminderTemplate.CAR_SERVICE
        reminder.location != null -> ReminderTemplate.MEETING
        reminder.financialType != null -> ReminderTemplate.FINANCIAL
        reminder.category == "ساختمان" -> ReminderTemplate.PROPERTY
        reminder.category == "تولد و سالگرد" -> ReminderTemplate.BIRTHDAY
        reminder.category == "دارو" -> ReminderTemplate.MEDICATION
        reminder.category == "بارداری" -> ReminderTemplate.PREGNANCY
        reminder.category == "نوزاد و کودک" -> ReminderTemplate.BABY_CARE
        reminder.category == "مذهبی" -> ReminderTemplate.RELIGIOUS
        else -> ReminderTemplate.GENERAL
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderScreen(
    knownCategories: List<String>,
    knownItemsByCategory: Map<String, List<String>> = emptyMap(),
    editingReminder: Reminder? = null,
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
        repeatDaysOfWeek: String?
    ) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    var template by remember(editingReminder) { mutableStateOf(inferTemplate(editingReminder)) }
    var title by remember(editingReminder) { mutableStateOf(editingReminder?.title ?: "") }
    var note by remember(editingReminder) { mutableStateOf(editingReminder?.note ?: "") }
    var category by remember(editingReminder) { mutableStateOf(editingReminder?.category ?: "عمومی") }
    var categoryColor by remember(editingReminder) { mutableStateOf(editingReminder?.categoryColor ?: CategoryColors.first()) }
    var repeatInterval by remember(editingReminder) { mutableStateOf(editingReminder?.repeatInterval ?: RepeatInterval.NONE) }
    var financialType by remember(editingReminder) { mutableStateOf(editingReminder?.financialType ?: FinancialType.DEBT) }
    var amountText by remember(editingReminder) { mutableStateOf(editingReminder?.amount?.toString() ?: "") }
    var counterparty by remember(editingReminder) { mutableStateOf(editingReminder?.counterparty ?: "") }
    var counterpartyPhone by remember(editingReminder) { mutableStateOf(editingReminder?.counterpartyPhone) }
    var bankName by remember(editingReminder) { mutableStateOf(editingReminder?.bankName ?: "") }
    var selectedWeekDays by remember(editingReminder) {
        mutableStateOf(
            editingReminder?.repeatDaysOfWeek?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toSet() ?: emptySet()
        )
    }
    var locationText by remember(editingReminder) { mutableStateOf(editingReminder?.location ?: "") }
    var mileageCurrentText by remember(editingReminder) {
        mutableStateOf(editingReminder?.let { CarPrefs.getCurrentKm(context).toString() } ?: CarPrefs.getCurrentKm(context).toString())
    }
    var mileageTargetText by remember(editingReminder) { mutableStateOf(editingReminder?.mileageTargetKm?.toString() ?: "") }
    var pregnancyMonth by remember(editingReminder) { mutableStateOf(1) }
    var babyAgeMonth by remember(editingReminder) { mutableStateOf(0) }
    var pregnancyMenuExpanded by remember { mutableStateOf(false) }
    var babyMenuExpanded by remember { mutableStateOf(false) }
    var attachmentUri by remember(editingReminder) {
        mutableStateOf(editingReminder?.attachmentUri?.let { Uri.parse(it) })
    }

    val takeAttachmentPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (!success) attachmentUri = null }

    val pickAttachmentFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            attachmentUri = it
        }
    }

    fun captureAttachmentPhoto() {
        val dir = File(context.filesDir, "attachments").apply { mkdirs() }
        val file = File(dir, "attachment_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        attachmentUri = uri
        takeAttachmentPhotoLauncher.launch(uri)
    }

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
    ) { granted -> if (granted) pickContactLauncher.launch(null) }

    fun pickFromContacts() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) pickContactLauncher.launch(null) else requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
    }

    val calendar = remember(editingReminder) {
        Calendar.getInstance().apply { editingReminder?.let { timeInMillis = it.triggerAtMillis } }
    }
    var triggerMillis by remember(editingReminder) { mutableStateOf(calendar.timeInMillis) }
    var showJalaliDatePicker by remember { mutableStateOf(false) }

    var repeatMenuExpanded by remember { mutableStateOf(false) }
    var financialTypeMenuExpanded by remember { mutableStateOf(false) }
    var templateMenuExpanded by remember { mutableStateOf(false) }

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
                    modifier = Modifier.fillMaxWidth().menuAnchor()
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
                    val generalSuggestions = (GENERAL_BUILTIN_CATEGORIES + knownCategories).distinct()
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
                            modifier = Modifier.fillMaxWidth().menuAnchor()
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
                            modifier = Modifier.fillMaxWidth().menuAnchor()
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

                ReminderTemplate.CAR_SERVICE -> {
                    val suggestions = (CAR_SERVICE_BUILTIN_ITEMS + knownItemsByCategory["ماشین"].orEmpty()).distinct()
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
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = financialTypeMenuExpanded,
                            onDismissRequest = { financialTypeMenuExpanded = false }
                        ) {
                            FinancialType.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.persianLabel()) },
                                    onClick = {
                                        financialType = option
                                        financialTypeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                        label = { Text("مبلغ (تومان)") },
                        modifier = Modifier.fillMaxWidth()
                    )

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
                            label = { Text("بانک") },
                            modifier = Modifier.fillMaxWidth()
                        )
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

            Button(onClick = { showJalaliDatePicker = true }) {
                Text("انتخاب تاریخ و ساعت: ${JalaliCalendar.formatDateTime(triggerMillis)}")
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

            if (repeatInterval == RepeatInterval.WEEKLY) {
                Text("روزهای تکرار (اختیاری، می‌تونی چندتا انتخاب کنی)", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PERSIAN_WEEK_DAYS) { (dayValue, label) ->
                        FilterChip(
                            selected = dayValue in selectedWeekDays,
                            onClick = {
                                selectedWeekDays = if (dayValue in selectedWeekDays) {
                                    selectedWeekDays - dayValue
                                } else {
                                    selectedWeekDays + dayValue
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }
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
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            contentScale = ContentScale.Crop
                        )
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
                        if (template == ReminderTemplate.CAR_SERVICE) {
                            mileageCurrentText.toLongOrNull()?.let { CarPrefs.setCurrentKm(context, it) }
                        }
                        onSave(
                            title,
                            note,
                            category.ifBlank { "عمومی" },
                            categoryColor,
                            triggerMillis,
                            repeatInterval,
                            if (template == ReminderTemplate.FINANCIAL || template == ReminderTemplate.CAR_SERVICE || template == ReminderTemplate.PROPERTY) amountText.toLongOrNull() else null,
                            if (template == ReminderTemplate.FINANCIAL) counterparty.ifBlank { null } else null,
                            if (template == ReminderTemplate.FINANCIAL) counterpartyPhone else null,
                            if (template == ReminderTemplate.FINANCIAL) financialType else null,
                            attachmentUri?.toString(),
                            if (template == ReminderTemplate.CAR_SERVICE) mileageTargetText.toLongOrNull() else null,
                            if (template == ReminderTemplate.MEETING) locationText.ifBlank { null } else null,
                            if (template == ReminderTemplate.FINANCIAL &&
                                (financialType == FinancialType.CHECK || financialType == FinancialType.INSTALLMENT)
                            ) bankName.ifBlank { null } else null,
                            if (repeatInterval == RepeatInterval.WEEKLY && selectedWeekDays.isNotEmpty()) {
                                selectedWeekDays.joinToString(",")
                            } else null
                        )
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (editingReminder != null) "به‌روزرسانی" else "ذخیره")
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
