package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class PermissionUiState(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    /** Required ones gate "everything will work"; optional ones are only recommended. */
    val isRequired: Boolean = true,
    /** Text of the card button — e.g. becomes "باز کردن تنظیمات" after a permanent denial. */
    val actionLabel: String = "اجازه بده",
    val onRequest: () -> Unit
)

/**
 * Shown before the main list whenever a required permission is missing (and once
 * for optional ones). One big button always performs the *next* missing step, so
 * the person can just keep tapping it instead of hunting for the right row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingScreen(
    permissions: List<PermissionUiState>,
    onContinue: () -> Unit
) {
    val nextPending = permissions.firstOrNull { !it.isGranted }
    val requiredMissing = permissions.any { it.isRequired && !it.isGranted }

    Scaffold(topBar = { TopAppBar(title = { Text("راه‌اندازی اولیه") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "برای این‌که یادآوری‌ها همیشه سر وقت بهت برسن، این چند دسترسی لازمه:",
                style = MaterialTheme.typography.bodyLarge
            )

            permissions.forEach { permission ->
                PermissionCard(permission)
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (nextPending != null) {
                Button(
                    onClick = nextPending.onRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("مرحله بعد: ${nextPending.title}")
                }
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (requiredMissing) "فعلاً رد شو (ممکنه یادآوری‌ها نرسن یا دیر برسن)"
                        else "ادامه بدون این مورد (فقط پیشنهادی بود)"
                    )
                }
            } else {
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("شروع کن")
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(permission: PermissionUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                permission.icon,
                contentDescription = null,
                tint = if (permission.isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (permission.isRequired) permission.title else "${permission.title} (پیشنهادی)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(permission.description, style = MaterialTheme.typography.bodySmall)
            }
            if (permission.isGranted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "داده‌شده", tint = Color(0xFF2E7D32))
            } else {
                TextButton(onClick = permission.onRequest) { Text(permission.actionLabel) }
            }
        }
    }
}
