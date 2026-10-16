package ir.naderinia.nsa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.util.VoiceParseResult
import ir.naderinia.nsa.util.VoiceReminder

/**
 * تأیید یادآوری‌ای که با صدا ساخته شده. متن کامل (عنوان + زمان + دسته) در یک جمله‌ی
 * پشت‌سرهم هم هست تا TalkBack همه‌چیز رو یک‌جا بخونه.
 */
@Composable
fun VoiceReminderDialog(
    result: VoiceParseResult,
    onConfirm: (VoiceReminder) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    when (result) {
        is VoiceParseResult.Ok -> {
            val r = result.reminder
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("ثبت این یادآوری؟") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(r.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(r.whenText, style = MaterialTheme.typography.bodyLarge)
                        if (r.category != "عمومی") {
                            Text("دسته: ${r.category}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = { Button(onClick = { onConfirm(r) }) { Text("ثبت یادآوری") } },
                dismissButton = {
                    Row {
                        TextButton(onClick = onRetry) { Text("دوباره بگو") }
                        TextButton(onClick = onDismiss) { Text("لغو") }
                    }
                }
            )
        }

        is VoiceParseResult.Problem -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("متوجه نشدم") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(result.message, style = MaterialTheme.typography.bodyLarge)
                        if (result.heard.isNotBlank()) {
                            Text("شنیدم: «${result.heard}»", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                },
                confirmButton = { Button(onClick = onRetry) { Text("دوباره بگو") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } }
            )
        }
    }
}

/** خلاصه‌ی روز: متن نوشته شده + خوانده شدن (با TalkBack یا گوینده‌ی خود برنامه). */
@Composable
fun BriefingDialog(
    text: String,
    canRepeat: Boolean,
    onRepeat: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خلاصه‌ی امروز") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("بستن") } },
        dismissButton = {
            if (canRepeat) TextButton(onClick = onRepeat) { Text("دوباره بخون") }
        }
    )
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun AccessibilitySettingsDialog(
    voiceMode: Boolean,
    onVoiceModeChange: (Boolean) -> Unit,
    speakNotifications: Boolean,
    onSpeakNotificationsChange: (Boolean) -> Unit,
    onTestVoice: () -> Unit,
    onOpenVoiceSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دسترس‌پذیری و صدا") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SwitchRow(
                    title = "حالت صوتی",
                    description = "برنامه نتیجه‌ی کارها رو با صدا می‌گه (مثلاً «یادآوری ذخیره شد»). " +
                        "وقتی TalkBack روشنه نیازی نیست و خود TalkBack می‌خونه.",
                    checked = voiceMode,
                    onChange = onVoiceModeChange
                )
                SwitchRow(
                    title = "خواندن یادآوری با صدا",
                    description = "وقتی وقت یادآوری می‌شه، متنش با صدای بلند خونده می‌شه؛ " +
                        "حتی وقتی صفحه خاموشه.",
                    checked = speakNotifications,
                    onChange = onSpeakNotificationsChange
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onTestVoice, modifier = Modifier.fillMaxWidth()) {
                    Text("امتحان صدا")
                }
                TextButton(onClick = onOpenVoiceSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("تنظیمات صدای گوشی (متن به گفتار)")
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("تأیید") } }
    )
}
