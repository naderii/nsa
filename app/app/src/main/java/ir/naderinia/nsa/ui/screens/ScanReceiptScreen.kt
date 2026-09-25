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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import ir.naderinia.nsa.util.OcrHelper
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReceiptScreen(
    onSave: (title: String, amount: Long, counterparty: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<Long>>(emptyList()) }
    var selectedAmount by remember { mutableStateOf<Long?>(null) }
    var title by remember { mutableStateOf("") }
    var counterparty by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun runOcr(uri: Uri) {
        isProcessing = true
        errorText = null
        scope.launch {
            try {
                val text = OcrHelper.recognizeText(context, uri)
                candidates = OcrHelper.extractNumberCandidates(text)
                if (candidates.isEmpty()) {
                    errorText = "عددی توی عکس پیدا نشد. مبلغ رو دستی وارد کن."
                }
            } catch (e: Exception) {
                errorText = "خطا در پردازش عکس: ${e.message}"
            } finally {
                isProcessing = false
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) imageUri?.let { runOcr(it) } }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
            runOcr(uri)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("اسکن رسید/قبض") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "این کاملاً روی خود گوشی و رایگان انجام می‌شه، بدون اینترنت. فقط اعداد (مبلغ) به‌درستی تشخیص داده می‌شن؛ عنوان رو خودت بنویس.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    val dir = File(context.cacheDir, "images").apply { mkdirs() }
                    val file = File(dir, "receipt_${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    imageUri = uri
                    takePictureLauncher.launch(uri)
                }) { Text("گرفتن عکس") }

                OutlinedButton(onClick = { pickImageLauncher.launch("image/*") }) {
                    Text("انتخاب از گالری")
                }
            }

            imageUri?.let { uri ->
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentScale = ContentScale.Crop
                )
            }

            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            errorText?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (candidates.isNotEmpty()) {
                Text("مبلغ رو انتخاب کن:", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(candidates) { amount ->
                        FilterChip(
                            selected = selectedAmount == amount,
                            onClick = { selectedAmount = amount },
                            label = { Text("$amount") }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان (مثلاً قبض برق)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = counterparty,
                onValueChange = { counterparty = it },
                label = { Text("طرف حساب (اختیاری)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = selectedAmount?.toString() ?: "",
                onValueChange = { selectedAmount = it.filter { c -> c.isDigit() }.toLongOrNull() },
                label = { Text("مبلغ (تومان) — یا دستی وارد کن") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val amount = selectedAmount
                        if (title.isNotBlank() && amount != null) onSave(title, amount, counterparty)
                    },
                    enabled = title.isNotBlank() && selectedAmount != null,
                    modifier = Modifier.weight(1f)
                ) { Text("ذخیره به‌عنوان یادآوری مالی") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("انصراف") }
            }
        }
    }
}
