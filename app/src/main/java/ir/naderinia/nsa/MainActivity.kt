package ir.naderinia.nsa

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.naderinia.nsa.ui.ReminderViewModel
import ir.naderinia.nsa.ui.screens.AddReminderScreen
import ir.naderinia.nsa.ui.screens.ReminderListScreen
import ir.naderinia.nsa.ui.theme.NsaTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ReminderViewModel by viewModels()

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result intentionally ignored: app still works, just silent if denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        maybeRequestBatteryOptimizationExemption()

        setContent {
            NsaTheme {
                val navController = rememberNavController()
                val reminders by viewModel.reminders.collectAsState()
                val knownCategories by viewModel.knownCategories.collectAsState()

                NavHost(navController = navController, startDestination = "list") {
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
     * optimization. We ask once, politely — this is the #1 cause of "my
     * reminder never fired" bug reports on real devices.
     */
    private fun maybeRequestBatteryOptimizationExemption() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
