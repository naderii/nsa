package ir.naderinia.nsa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Asked once after a crash: send the technical report by email, or discard it. */
@Composable
fun CrashReportDialog(
    report: String,
    onSend: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برنامه دفعه‌ی قبل ناگهانی بسته شد") },
        text = {
            Column {
                Text(
                    "می‌خوای گزارش خطا رو برای سازنده بفرستی تا درستش کنه؟ فقط اطلاعات فنی (نسخه‌ی برنامه، مدل گوشی و متن خطا) فرستاده می‌شه، نه یادآوری‌ها یا اطلاعات شخصی تو. ایمیل خودت باز می‌شه و تا دکمه‌ی ارسال رو نزنی چیزی نمی‌ره.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .heightIn(max = 140.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        report,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            textDirection = TextDirection.Ltr
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onSend) { Text("ارسال با ایمیل") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("نه، ممنون") } }
    )
}
