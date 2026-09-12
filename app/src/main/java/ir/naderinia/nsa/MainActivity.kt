package ir.naderinia.nsa

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.naderinia.nsa.ui.ReminderViewModel
import ir.naderinia.nsa.ui.screens.AddReminderScreen
import ir.naderinia.nsa.ui.screens.PermissionUiState
import ir.naderinia.nsa.ui.screens.PermissionOnboardingScreen
import ir.naderinia.nsa.ui.screens.ReminderListScreen
import ir.naderinia.nsa.ui.theme.NsaTheme
import ir.naderinia.nsa.util.PermissionStatus

class MainActivity : ComponentActivity() {

    private val viewModel: ReminderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NsaTheme {
                var notifGranted by remember { mutableStateOf(PermissionStatus.hasNotificationPermission(this)) }
                var exactAlarmGranted by remember { mutableStateOf(PermissionStatus.canScheduleExactAlarms(this)) }
                var batteryExempt by remember { mutableStateOf(PermissionStatus.isIgnoringBatteryOptimizations(this)) }

                // Battery/exact-alarm grants happen in system Settings screens, not
                // through registerForActivityResult callbacks, so we re-check every
                // time this Activity comes back to the foreground.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            notifGranted = PermissionStatus.hasNotificationPermission(this@MainActivity)
                            exactAlarmGranted = PermissionStatus.canScheduleExactAlarms(this@MainActivity)
                            batteryExempt = PermissionStatus.isIgnoringBatteryOptimizations(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val requestNotificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> notifGranted = granted }

                val permissionItems = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(
                            PermissionUiState(
                                title = "نمایش نوتیفیکیشن",
                                description = "بدون این، یادآوری اصلاً نشون داده نمی‌شه",
                                icon = Icons.Default.Notifications,
                                isGranted = notifGranted,
                                onRequest = { requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            )
                        )
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(
                            PermissionUiState(
                                title = "زمان‌بندی دقیق",
                                description = "تا یادآوری دقیقاً سر همون ساعتی که تعیین کردی برسه",
                                icon = Icons.Default.Alarm,
                                isGranted = exactAlarmGranted,
                                onRequest = {
                                    startActivity(
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:$packageName")
                                        }
                                    )
                                }
                            )
                        )
                    }
                    add(
                        PermissionUiState(
                            title = "معافیت از بهینه‌سازی باتری",
                            description = "تا گوشی اپ رو در پس‌زمینه نکشه و یادآوری گم نشه",
                            icon = Icons.Default.BatteryAlert,
                            isGranted = batteryExempt,
                            onRequest = { requestBatteryOptimizationExemption() }
                        )
                    )
                }

                val navController = rememberNavController()
                val reminders by viewModel.reminders.collectAsState()
                val knownCategories by viewModel.knownCategories.collectAsState()

                val startDestination = if (permissionItems.all { it.isGranted }) "list" else "onboarding"

                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable("onboarding") {
                        PermissionOnboardingScreen(
                            permissions = permissionItems,
                            onContinue = {
                                navController.navigate("list") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("list") {
                        ReminderListScreen(
                            reminders = reminders,
                            onAddClick = { navController.navigate("add") },
                            onToggleDone = { viewModel.toggleDone(it) },
                            onDelete = { viewModel.deleteReminder(it) },
                            onRecordPayment = { reminder, amount -> viewModel.recordPayment(reminder, amount) }
                        )
                    }
                    composable("add") {
                        AddReminderScreen(
                            knownCategories = knownCategories,
                            onSave = { title, note, category, color, trigger, repeat, amount, counterparty, phone ->
                                viewModel.addReminder(
                                    title, note, category, color, trigger, repeat, amount, counterparty, phone
                                )
                                navController.popBackStack()
                            },
                            onCancel = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    /**
     * Many Android OEMs (Xiaomi, Samsung, Huawei) aggressively kill background
     * apps and drop scheduled alarms unless the app is exempted from battery
     * optimization. This is the #1 cause of "my reminder never fired" bug
     * reports on real devices, so it's requested explicitly from onboarding.
     */
    private fun requestBatteryOptimizationExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
