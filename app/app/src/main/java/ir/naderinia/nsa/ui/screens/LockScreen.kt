package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LockScreen(
    error: String?,
    showBiometricButton: Boolean,
    onPinEntered: (String) -> Unit,
    onBiometricClick: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("رمز عبور را وارد کن", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    val filled = index < pin.length
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                    )
                }
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(32.dp))

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { key ->
                            when {
                                key == "⌫" -> IconButton(onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) }) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "پاک کردن")
                                }
                                key.isEmpty() -> Spacer(modifier = Modifier.size(56.dp))
                                else -> OutlinedButton(
                                    onClick = {
                                        if (pin.length < 4) {
                                            pin += key
                                            if (pin.length == 4) {
                                                val entered = pin
                                                pin = ""
                                                onPinEntered(entered)
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(56.dp),
                                    shape = CircleShape
                                ) {
                                    Text(key, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                }
            }

            if (showBiometricButton) {
                Spacer(modifier = Modifier.height(24.dp))
                IconButton(onClick = onBiometricClick) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = "ورود با اثر انگشت",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
