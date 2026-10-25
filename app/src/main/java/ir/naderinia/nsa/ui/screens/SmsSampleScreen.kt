package ir.naderinia.nsa.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.ui.theme.NsaShapes
import ir.naderinia.nsa.util.BankKey
import ir.naderinia.nsa.util.BankSmsParser
import ir.naderinia.nsa.util.SampleContacts
import ir.naderinia.nsa.util.SmsSampleMasker
import ir.naderinia.nsa.util.SmsSampleReport

/**
 * "Add your bank": the person pastes one bank SMS, the app hides the numbers and the name, shows exactly
 * what would be sent (editable), and only when the person taps a button does it hand the text to their own
 * mail / Bale / Telegram / share sheet. The app itself has no internet permission and sends nothing.
 */
@Composable
fun SmsSampleScreen(
    appVersion: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // The real SMS lives only here, only until it has been masked; it is never saved or restored.
    var original by remember { mutableStateOf("") }
    var sender by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var prepared by rememberSaveable { mutableStateOf(false) }
    var thanks by rememberSaveable { mutableStateOf(false) }

    val supported = BankKey.values()
        .filter { it != BankKey.GENERIC }
        .joinToString("، ") { it.persianName }

    fun report(): String =
        SmsSampleReport.buildFromSummary(preview, sender, summary, appVersion)

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun prepare() {
        summary = SmsSampleReport.recognitionSummary(BankSmsParser.parse(original))
        preview = SmsSampleMasker.mask(original)
        original = "" // the real text is gone from memory state from here on
        prepared = true
        thanks = false
    }

    fun sendEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SampleContacts.EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, SmsSampleReport.subject())
            putExtra(Intent.EXTRA_TEXT, report())
        }
        runCatching { context.startActivity(intent) }
            .onSuccess { thanks = true }
            .onFailure {
                copyToClipboard(context, report())
                toast("برنامه‌ی ایمیل پیدا نشد؛ متن کپی شد. می‌تونی دستی بفرستی.")
            }
    }

    fun openChat(url: String, appName: String) {
        // Chat apps cannot be pre-filled for a specific person, so the text is copied for pasting.
        copyToClipboard(context, report())
        toast("متن کپی شد. توی چت $appName بچسبون و بفرست.")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onSuccess { thanks = true }
    }

    fun shareAny() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, SmsSampleReport.subject())
            putExtra(Intent.EXTRA_TEXT, report())
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "ارسال نمونه"))
        }.onSuccess { thanks = true }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { SmsSampleTopBar(onBack = onBack) }
        ) { paddingValues ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {

                // ---------- why ----------
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = NsaShapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "بانک خودت رو به نسا اضافه کن",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "هر بانک قالب پیامک خودش رو داره. با فرستادن فقط یه نمونه، پشتیبانی بانک تو اضافه می‌شه و برای همه‌ی کاربرها هم کار می‌کنه. اعداد و اسم قبل از ارسال خودکار مخفی می‌شن و خودت همه‌چیز رو می‌بینی.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "بانک‌های پشتیبانی‌شده تا الان: $supported",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Right
                        )
                    }
                }

                // ---------- step 1 ----------
                StepCard(title = "۱) پیامک بانکی رو بچسبون") {
                    OutlinedTextField(
                        value = original,
                        onValueChange = { original = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("متن پیامک (واریز یا برداشت)") },
                        minLines = 4
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sender,
                        onValueChange = { sender = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("فرستنده‌ی پیامک (اختیاری)") },
                        placeholder = { Text("مثلاً 700717 یا POSTBANK") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { prepare() },
                        enabled = original.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مخفی‌کردن اعداد و پیش‌نمایش")
                    }
                }

                // ---------- step 2 + 3 ----------
                if (prepared) {
                    StepCard(title = "۲) این چیزیه که ارسال می‌شه") {
                        Text(
                            text = "اعداد با عددهای تصادفی عوض شدن ولی تاریخ و ساعت مونده تا قالب رو بشناسیم. اگه اسم یا چیز دیگه‌ای دیدی که نباید باشه، همین‌جا پاکش کن.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Right
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = preview,
                            onValueChange = { preview = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "تشخیص نسا: $summary",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Right
                        )
                    }

                    StepCard(title = "۳) بفرست — هر راهی که راحت‌تره") {
                        Button(
                            onClick = { sendEmail() },
                            enabled = preview.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.Email, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ایمیل")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { openChat(SampleContacts.BALE_URL, "بله") },
                                enabled = preview.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("بله")
                            }
                            OutlinedButton(
                                onClick = { openChat(SampleContacts.TELEGRAM_URL, "تلگرام") },
                                enabled = preview.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تلگرام")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { shareAny() },
                                enabled = preview.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("پیامک و بقیه")
                            }
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, report())
                                    toast("متن کپی شد")
                                },
                                enabled = preview.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("کپی")
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "ایمیل: ${SampleContacts.EMAIL}\nبله: ${SampleContacts.BALE_ID}   •   تلگرام: ${SampleContacts.TELEGRAM_ID}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Right
                        )
                    }
                }

                if (thanks) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = NsaShapes.medium,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "ممنون که کمک کردی! هر نمونه یه بانک جدید رو برای همه‌ی کاربرهای نسا ممکن می‌کنه. اگه پیامک‌های دیگه‌ی بانکت هم قالب متفاوتی دارن (خرید، کارت‌به‌کارت، قسط...) اونا رو هم بفرست.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                // ---------- promise ----------
                Text(
                    text = "نسا اینترنت نداره و خودش هیچ‌چیز نمی‌فرسته. متن فقط وقتی از گوشی بیرون می‌ره که خودت یکی از دکمه‌های ارسال رو بزنی.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("nsa bank sample", text))
    }
}

@Composable
private fun StepCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = NsaShapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Right
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SmsSampleTopBar(onBack: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "بازگشت"
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "پشتیبانی از بانک تو",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
