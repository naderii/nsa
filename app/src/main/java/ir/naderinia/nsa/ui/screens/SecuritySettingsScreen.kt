package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.util.SecurityPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    biometricAvailable: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var lockEnabled by remember { mutableStateOf(SecurityPrefs.isLockEnabled(context)) }
    var biometricEnabled by remember { mutableStateOf(SecurityPrefs.isBiometricEnabled(context)) }
    var showSetPinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("امنیت") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("قفل اپ با رمز", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "باز کردن اپ نیاز به رمز ۴ رقمی داشته باشه",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = lockEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showSetPinDialog = true
                        } else {
                            SecurityPrefs.disableLock(context)
                            lockEnabled = false
                            biometricEnabled = false
                        }
                    }
                )
            }

            if (lockEnabled && biometricAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ورود با اثر انگشت", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "علاوه بر رمز، اثر انگشت هم قبول بشه",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = {
                            biometricEnabled = it
                            SecurityPrefs.setBiometricEnabled(context, it)
                        }
                    )
                }
            }

            if (lockEnabled) {
                OutlinedButton(onClick = { showSetPinDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("تغییر رمز")
                }
            }

            Text(
                "توجه: رمز فقط به‌صورت هش‌شده ذخیره می‌شه، ولی این یه قفل ساده‌ست نه رمزنگاری کامل دیتابیس. برای امنیت مالی جدی، در آینده رمزنگاری کامل دیتابیس هم قابل اضافه‌کردنه.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showSetPinDialog) {
        SetPinDialog(
            onDismiss = {
                showSetPinDialog = false
                if (!SecurityPrefs.hasPin(context)) lockEnabled = false
            },
            onConfirm = { pin ->
                SecurityPrefs.setPin(context, pin)
                lockEnabled = true
                showSetPinDialog = false
            }
        )
    }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تنظیم رمز ۴ رقمی") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("رمز جدید") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("تکرار رمز") },
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pin.length != 4 -> error = "رمز باید ۴ رقم باشه"
                    pin != confirmPin -> error = "رمزها یکسان نیستن"
                    else -> onConfirm(pin)
                }
            }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
