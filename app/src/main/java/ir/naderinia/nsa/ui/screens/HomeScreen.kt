package ir.naderinia.nsa.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.naderinia.nsa.data.CheckStatus
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.PaymentLog
import ir.naderinia.nsa.data.Reminder
import ir.naderinia.nsa.ui.DashboardStats
import ir.naderinia.nsa.ui.FinancialSummary
import ir.naderinia.nsa.ui.theme.LocalClassicStyle
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.util.ThemePrefs
import kotlinx.coroutines.launch

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
    paymentLogs: List<PaymentLog>,
    dashboardStats: DashboardStats,
    /** (category, financialType): what the new reminder should be pre-set to, based on the current tab. */
    onAddClick: (String?, FinancialType?) -> Unit,
    onRecordPayment: (Reminder, Long) -> Unit,
    onCheckStatusChange: (Reminder, CheckStatus?) -> Unit,
    onSearchClick: () -> Unit,
    onReportClick: () -> Unit,
    themeMode: Int,
    onThemeModeChange: (Int) -> Unit,
    onClassicStyleChange: (Boolean) -> Unit,
    onScanClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onSoundClick: () -> Unit,
    onAboutClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenOverdue: () -> Unit,
    onOpenActive: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    val classic = LocalClassicStyle.current
    val tabs = HomeTab.values()
    // The pager is the single source of truth for the current tab: swiping the content and
    // tapping the bottom bar both end up here, and the page survives rotation/process death.
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val selectedTab = tabs[pagerState.currentPage.coerceIn(0, tabs.size - 1)]
    fun goToTab(tab: HomeTab) {
        scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
    }
    val overdueBadge = remember(reminders) {
        val now = System.currentTimeMillis()
        reminders.count { !it.isDone && it.triggerAtMillis < now }
    }
    var menuExpanded by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var financialFilter by rememberSaveable { mutableStateOf<FinancialType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                        label = "homeTitle"
                    ) { tab ->
                        if (classic) {
                            Text(tab.title)
                        } else {
                            Text(
                                tab.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = if (classic) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "جست‌وجو"
                        )
                    }

                    IconButton(onClick = onScanClick) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "اسکن رسید"
                        )
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "بیشتر"
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("امنیت") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onSecurityClick()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("ظاهر برنامه") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Palette,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    showThemeDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("آهنگ هشدار") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onSoundClick()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("تهیه‌ی پشتیبان") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Backup,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onBackupClick()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("بازیابی از پشتیبان") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRestoreClick()
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("درباره‌ی اپ") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onAboutClick()
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
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        goToTab(HomeTab.REMINDERS)
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (!classic && overdueBadge > 0) {
                                    Badge {
                                        Text(
                                            JalaliCalendar.toPersianDigits(
                                                overdueBadge.coerceAtMost(99).toString()
                                            )
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = null
                            )
                        }
                    },
                    label = { Text("یادآوری‌ها") }
                )

                NavigationBarItem(
                    selected = selectedTab == HomeTab.FINANCIAL,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        goToTab(HomeTab.FINANCIAL)
                    },
                    icon = {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = null
                        )
                    },
                    label = { Text("مالی") }
                )

                NavigationBarItem(
                    selected = selectedTab == HomeTab.DASHBOARD,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        goToTab(HomeTab.DASHBOARD)
                    },
                    icon = {
                        Icon(
                            Icons.Default.Dashboard,
                            contentDescription = null
                        )
                    },
                    label = { Text("داشبورد") }
                )
            }
        },

        floatingActionButton = {
            ExtendedFloatingActionButton(
                containerColor = if (classic) FloatingActionButtonDefaults.containerColor
                else MaterialTheme.colorScheme.primary,
                contentColor = if (classic) contentColorFor(FloatingActionButtonDefaults.containerColor)
                else MaterialTheme.colorScheme.onPrimary,
                shape = if (classic) FloatingActionButtonDefaults.extendedFabShape
                else MaterialTheme.shapes.large,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (selectedTab == HomeTab.FINANCIAL) {
                        onAddClick("مالی", financialFilter)
                    } else {
                        onAddClick(null, null)
                    }
                },
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = {
                    Text("یادآوری جدید")
                }
            )
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { it }
            ) { page ->
                when (tabs[page]) {

                    HomeTab.REMINDERS -> RemindersOverview(
                        reminders = reminders,
                        onOpenToday = onOpenToday,
                        onOpenOverdue = onOpenOverdue,
                        onOpenActive = onOpenActive,
                        onOpenCategory = onOpenCategory
                    )

                    HomeTab.FINANCIAL -> FinancialScreen(
                        reminders = financialReminders,
                        summary = financialSummary,
                        selectedFilter = financialFilter,
                        onFilterChange = { financialFilter = it },
                        paymentLogs = paymentLogs,
                        onCheckStatusChange = onCheckStatusChange,
                        onReportClick = onReportClick,
                        onRecordPayment = onRecordPayment
                    )

                    HomeTab.DASHBOARD -> DashboardScreen(
                        stats = dashboardStats,
                        financialSummary = financialSummary,
                        onOpenToday = onOpenToday,
                        onOpenOverdue = onOpenOverdue,
                        onOpenActive = onOpenActive,
                        onOpenFinancial = { goToTab(HomeTab.FINANCIAL) }
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("ظاهر برنامه") },
            text = {
                Column {
                    Text(
                        "رنگ‌بندی",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    listOf(
                        ThemePrefs.SYSTEM to "مطابق تنظیم گوشی",
                        ThemePrefs.LIGHT to "روشن",
                        ThemePrefs.DARK to "تیره"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeModeChange(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    onThemeModeChange(mode)
                                    showThemeDialog = false
                                }
                            )
                            Text(label)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        "سبک ظاهر",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    listOf(
                        false to "مدرن",
                        true to "کلاسیک (ظاهر نسخه‌های قبلی)"
                    ).forEach { (isClassic, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onClassicStyleChange(isClassic)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = classic == isClassic,
                                onClick = {
                                    onClassicStyleChange(isClassic)
                                    showThemeDialog = false
                                }
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("بستن") }
            }
        )
    }
}
