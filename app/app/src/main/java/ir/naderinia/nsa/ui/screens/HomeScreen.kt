package ir.naderinia.nsa.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.ui.DashboardStats
import ir.naderinia.nsa.ui.FinancialSummary

private enum class HomeTab(val title: String) {
    REMINDERS("یادآوری‌های من"),
    FINANCIAL("مدیریت مالی"),
    DASHBOARD("داشبورد روزانه")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    reminders: List<Reminder>,
    financialReminders: List<Reminder>,
    financialSummary: FinancialSummary,
    dashboardStats: DashboardStats,
    onAddClick: () -> Unit,
    onRecordPayment: (Reminder, Long) -> Unit,
    onScanClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onSoundClick: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenOverdue: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(HomeTab.REMINDERS) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedTab.title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onScanClick) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "اسکن رسید")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "بیشتر")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("امنیت") },
                                leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onSecurityClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("آهنگ هشدار") },
                                leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onSoundClick()
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.REMINDERS,
                    onClick = { selectedTab = HomeTab.REMINDERS },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    label = { Text("یادآوری‌ها") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.FINANCIAL,
                    onClick = { selectedTab = HomeTab.FINANCIAL },
                    icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                    label = { Text("مالی") }
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.DASHBOARD,
                    onClick = { selectedTab = HomeTab.DASHBOARD },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                    label = { Text("داشبورد") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == HomeTab.REMINDERS) {
                ExtendedFloatingActionButton(
                    onClick = onAddClick,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("یادآوری جدید") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                HomeTab.REMINDERS -> RemindersOverview(
                    reminders = reminders,
                    onOpenToday = onOpenToday,
                    onOpenOverdue = onOpenOverdue,
                    onOpenCategory = onOpenCategory
                )
                HomeTab.FINANCIAL -> FinancialScreen(
                    reminders = financialReminders,
                    summary = financialSummary,
                    onRecordPayment = onRecordPayment
                )
                HomeTab.DASHBOARD -> DashboardScreen(stats = dashboardStats)
            }
        }
    }
}
