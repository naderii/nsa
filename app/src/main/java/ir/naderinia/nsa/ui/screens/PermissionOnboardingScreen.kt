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
import androidx.compose.material3.ExperimentalMaterial3Api

data class PermissionUiState(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: Boolean,
    val onRequest: () -> Unit
)

/**
 * Shown once before the main list, whenever an essential permission is
 * still missing. Every item is requested explicitly and individually
 * instead of firing scattered system dialogs at random moments — the
 * person sees exactly what's being asked for and why before granting it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionOnboardingScreen(
    permissions: List<PermissionUiState>,
    onContinue: () -> Unit
) {
    val allGranted = permissions.all { it.isGranted }

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

            Button(
                onClick = onContinue,
                enabled = allGranted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (allGranted) "شروع کن" else "همه رو بده تا فعال بشه دکمه")
            }

            if (!allGranted) {
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("فعلاً رد شو (بعضی یادآوری‌ها ممکنه دیر برسن)")
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
                Text(permission.title, style = MaterialTheme.typography.titleMedium)
                Text(permission.description, style = MaterialTheme.typography.bodySmall)
            }
            if (permission.isGranted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "داده‌شده", tint = Color(0xFF2E7D32))
            } else {
                TextButton(onClick = permission.onRequest) { Text("اجازه بده") }
            }
        }
    }
}
