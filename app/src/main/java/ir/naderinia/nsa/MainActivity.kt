package ir.naderinia.nsa

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.core.app.ActivityCompat
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.naderinia.nsa.data.FinancialType
import ir.naderinia.nsa.data.RepeatInterval
import ir.naderinia.nsa.data.sectionKey
import ir.naderinia.nsa.notification.NotificationHelper
import ir.naderinia.nsa.ui.ReminderUiEvent
import ir.naderinia.nsa.ui.ReminderViewModel
import ir.naderinia.nsa.ui.screens.AboutScreen
import ir.naderinia.nsa.ui.screens.AddReminderScreen
import ir.naderinia.nsa.ui.screens.CategoryDetailScreen
import ir.naderinia.nsa.ui.screens.HomeScreen
import ir.naderinia.nsa.ui.screens.LockScreen
import ir.naderinia.nsa.ui.screens.PermissionOnboardingScreen
import ir.naderinia.nsa.ui.screens.PermissionUiState
import ir.naderinia.nsa.ui.screens.FinanceReportScreen
import ir.naderinia.nsa.notification.NotificationScheduler
import ir.naderinia.nsa.ui.screens.ScanReceiptScreen
import ir.naderinia.nsa.ui.screens.SearchScreen
import ir.naderinia.nsa.ui.screens.SecuritySettingsScreen
import ir.naderinia.nsa.ui.components.CrashReportDialog
import ir.naderinia.nsa.ui.screens.WhatsNewScreen
import ir.naderinia.nsa.ui.theme.NsaTheme
import ir.naderinia.nsa.util.Changelog
import ir.naderinia.nsa.util.ChangelogPrefs
import ir.naderinia.nsa.util.CrashReporter
import ir.naderinia.nsa.util.OnboardingPrefs
import ir.naderinia.nsa.util.PermissionStatus
import ir.naderinia.nsa.util.SecurityPrefs
import ir.naderinia.nsa.util.ThemePrefs
import java.util.Calendar

private sealed class ReminderDetailFilter {
    object Active : ReminderDetailFilter()
    object Today : ReminderDetailFilter()
    object Overdue : ReminderDetailFilter()
    data class Category(val name: String) : ReminderDetailFilter()
}

/**
 * Extends FragmentActivity (not plain ComponentActivity) because
 * androidx.biometric.BiometricPrompt requires a FragmentActivity host.
 * FragmentActivity is itself a ComponentActivity subclass, so Compose's
 * setContent still works exactly the same.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: ReminderViewModel by viewModels()

    companion object {
        const val EXTRA_NAVIGATE_TO_ADD = "navigate_to_add"

        /** Re-prompt for PIN/biometric if the app sat in the background this long. */
        private const val LOCK_TIMEOUT_MILLIS = 30_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // AlarmManager forgets alarms after a force-stop or a restore on another phone — and
        // nothing else would re-arm them. Opening the app does (2 min grace: an alarm that is
        // being delivered right now is left to its own receiver).
        lifecycleScope.launch(Dispatchers.IO) {
            NotificationScheduler.rescheduleAll(applicationContext, 120_000L)
        }

        setContent {

            var themeMode by remember {
                mutableStateOf(ThemePrefs.getMode(this@MainActivity))
            }

            var classicStyle by remember {
                mutableStateOf(ThemePrefs.isClassic(this@MainActivity))
            }

            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()

            NsaTheme(
                classicStyle = classicStyle,
                darkTheme = when (themeMode) {
                    ThemePrefs.LIGHT -> false
                    ThemePrefs.DARK -> true
                    else -> systemDark
                }
            ) {

                val snackbarHostState = remember {
                    SnackbarHostState()
                }

                // Deleted reminders waiting for a possible "واگرد". Deleting 10 reminders must show
                // ONE snackbar ("۱۰ یادآوری حذف شد") whose undo restores all of them — not ten
                // queued snackbars. Any new event replaces the current snackbar instead of
                // waiting behind it, so a "saved" message never sits in a queue.
                val pendingDeleted = remember { mutableStateListOf<ir.naderinia.nsa.data.Reminder>() }
                var snackbarJob by remember { mutableStateOf<Job?>(null) }

                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        snackbarJob?.cancel()
                        snackbarHostState.currentSnackbarData?.dismiss()

                        if (event !is ReminderUiEvent.ReminderDeleted) pendingDeleted.clear()

                        snackbarJob = launch {
                            when (event) {
                                ReminderUiEvent.ReminderSaved ->
                                    snackbarHostState.showSnackbar(
                                        message = "یادآوری ذخیره شد ✓",
                                        duration = SnackbarDuration.Short
                                    )

                                ReminderUiEvent.ReminderUpdated ->
                                    snackbarHostState.showSnackbar(
                                        message = "یادآوری به‌روزرسانی شد ✓",
                                        duration = SnackbarDuration.Short
                                    )

                                is ReminderUiEvent.RepeatingRoundDone ->
                                    snackbarHostState.showSnackbar(
                                        message = "«${event.title}» ثبت شد؛ دفعه‌ی بعد دوباره یادآوری می‌شه ✓",
                                        duration = SnackbarDuration.Short
                                    )

                                is ReminderUiEvent.ReminderDeleted -> {
                                    pendingDeleted.add(event.reminder)
                                    val count = pendingDeleted.size
                                    val message = if (count == 1) {
                                        "یادآوری حذف شد"
                                    } else {
                                        ir.naderinia.nsa.util.JalaliCalendar
                                            .toPersianDigits("$count یادآوری حذف شد")
                                    }
                                    val result = snackbarHostState.showSnackbar(
                                        message = message,
                                        actionLabel = "واگرد",
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Short
                                    )
                                    // Only reached when this snackbar ends by itself / by the user;
                                    // a newer delete cancels this job and takes over the list.
                                    if (result == SnackbarResult.ActionPerformed) {
                                        pendingDeleted.toList().forEach { viewModel.restoreReminder(it) }
                                    }
                                    pendingDeleted.clear()
                                }
                            }
                        }
                    }
                }

                // ---- Permission onboarding state ----
                var notifGranted by remember {
                    mutableStateOf(
                        PermissionStatus.hasNotificationPermission(this)
                    )
                }

                // true after Android stops showing the notification dialog (denied twice):
                // from then on the only way is the app's system notification settings.
                var notifBlocked by rememberSaveable { mutableStateOf(false) }

                var exactAlarmGranted by remember {
                    mutableStateOf(
                        PermissionStatus.canScheduleExactAlarms(this)
                    )
                }

                var batteryExempt by remember {
                    mutableStateOf(
                        PermissionStatus.isIgnoringBatteryOptimizations(this)
                    )
                }

                // ---- App-lock state ----
                var isUnlocked by remember {
                    mutableStateOf(
                        !SecurityPrefs.isLockEnabled(this)
                    )
                }

                var lockError by remember {
                    mutableStateOf<String?>(null)
                }

                var backgroundedAtMillis by remember {
                    mutableStateOf(0L)
                }

                val biometricAvailable = remember {
                    BiometricManager
                        .from(this)
                        .canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_WEAK
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                }

                // Battery/exact-alarm grants happen in system Settings screens,
                // not through registerForActivityResult callbacks, so we re-check
                // every time this Activity comes back to the foreground. Also:
                // re-lock the app if it sat in the background longer than
                // LOCK_TIMEOUT_MILLIS (a quick trip through the camera/ringtone/
                // contacts pickers also triggers ON_STOP, but shouldn't force a
                // re-unlock — only actually switching away for a while should),
                // and keep FLAG_SECURE (blocks screenshots + Recents thumbnail)
                // in sync with whether app-lock is turned on, since that's the
                // clearest signal the person cares about this data being private.
                val lifecycleOwner = LocalLifecycleOwner.current

                DisposableEffect(lifecycleOwner) {
                    val observer =
                        LifecycleEventObserver { _, event ->

                            if (event == Lifecycle.Event.ON_RESUME) {

                                notifGranted =
                                    PermissionStatus.hasNotificationPermission(
                                        this@MainActivity
                                    )

                                if (notifGranted) notifBlocked = false

                                exactAlarmGranted =
                                    PermissionStatus.canScheduleExactAlarms(
                                        this@MainActivity
                                    )

                                batteryExempt =
                                    PermissionStatus.isIgnoringBatteryOptimizations(
                                        this@MainActivity
                                    )

                                // Some ROMs flip these flags a moment after returning from the
                                // system dialog — look once more so the screen never shows stale state.
                                Handler(Looper.getMainLooper()).postDelayed({
                                    notifGranted =
                                        PermissionStatus.hasNotificationPermission(this@MainActivity)
                                    exactAlarmGranted =
                                        PermissionStatus.canScheduleExactAlarms(this@MainActivity)
                                    batteryExempt =
                                        PermissionStatus.isIgnoringBatteryOptimizations(this@MainActivity)
                                }, 800L)

                                val lockEnabled = SecurityPrefs.isLockEnabled(this@MainActivity)

                                if (lockEnabled) {
                                    window.setFlags(
                                        WindowManager.LayoutParams.FLAG_SECURE,
                                        WindowManager.LayoutParams.FLAG_SECURE
                                    )
                                } else {
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                }

                                if (lockEnabled &&
                                    backgroundedAtMillis != 0L &&
                                    System.currentTimeMillis() - backgroundedAtMillis > LOCK_TIMEOUT_MILLIS
                                ) {
                                    isUnlocked = false
                                }
                                backgroundedAtMillis = 0L
                            } else if (event == Lifecycle.Event.ON_STOP) {
                                backgroundedAtMillis = System.currentTimeMillis()
                            }
                        }

                    lifecycleOwner.lifecycle.addObserver(observer)

                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val requestNotificationPermission =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        notifGranted = granted
                        // No rationale + not granted right after asking = permanently denied;
                        // further launch() calls would silently do nothing.
                        if (!granted &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        ) {
                            notifBlocked = true
                        }
                    }

                val ringtonePickerLauncher =
                    rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { result ->

                        // Backing out of the picker must not reset the chosen sound.
                        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult

                        val uri =
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                                result.data?.getParcelableExtra(
                                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                                    Uri::class.java
                                )

                            } else {

                                @Suppress("DEPRECATION")
                                result.data?.getParcelableExtra<Uri>(
                                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                                )
                            }

                        NotificationHelper.setCustomSound(
                            this,
                            uri
                        )
                    }

                fun openRingtonePicker() {

                    val intent =
                        Intent(
                            RingtoneManager.ACTION_RINGTONE_PICKER
                        ).apply {

                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TYPE,
                                RingtoneManager.TYPE_NOTIFICATION
                            )

                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_TITLE,
                                "انتخاب آهنگ هشدار"
                            )

                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
                                true
                            )

                            // "Silent" would come back as null and silently turn into the default sound.
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                                false
                            )

                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                NotificationHelper.currentSoundUri(
                                    this@MainActivity
                                )
                            )
                        }

                    ringtonePickerLauncher.launch(intent)
                }

                val scope = rememberCoroutineScope()

                val createBackupLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/zip")
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            try {
                                ir.naderinia.nsa.util.BackupManager.exportToUri(this@MainActivity, uri)
                                snackbarHostState.showSnackbar("فایل پشتیبان ذخیره شد ✓")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("تهیه‌ی پشتیبان با خطا مواجه شد")
                            }
                        }
                    }
                }

                fun startBackup() {
                    val fileName = "nsa-backup-${System.currentTimeMillis()}.zip"
                    createBackupLauncher.launch(fileName)
                }

                val restoreBackupLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            try {
                                val count = ir.naderinia.nsa.util.BackupManager.importFromUri(this@MainActivity, uri)
                                snackbarHostState.showSnackbar(
                                    "${ir.naderinia.nsa.util.JalaliCalendar.toPersianDigits(count.toString())} یادآوری بازیابی شد ✓"
                                )
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("فایل پشتیبان قابل‌خواندن نبود")
                            }
                        }
                    }
                }

                fun startRestore() {
                    restoreBackupLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream"))
                }

                val permissionItems = buildList {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                        add(
                            PermissionUiState(
                                title = "نمایش نوتیفیکیشن",
                                description = "بدون این، یادآوری اصلاً نشون داده نمی‌شه",
                                icon = Icons.Default.Notifications,
                                isGranted = notifGranted,
                                actionLabel = if (notifBlocked) "باز کردن تنظیمات" else "اجازه بده",
                                onRequest = {
                                    if (notifBlocked) {
                                        openAppNotificationSettings()
                                    } else {
                                        requestNotificationPermission.launch(
                                            Manifest.permission.POST_NOTIFICATIONS
                                        )
                                    }
                                }
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
                                    startActivitySafely(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                                        ).apply {
                                            data =
                                                Uri.parse(
                                                    "package:$packageName"
                                                )
                                        }
                                    )
                                }
                            )
                        )
                    }

                    add(
                        PermissionUiState(
                            title = "معافیت از بهینه‌سازی باتری",
                            description = "تا گوشی اپ رو در پس‌زمینه نکشه و یادآوری گم نشه (روی بعضی گوشی‌ها مثل شیائومی و هواوی خیلی مهمه)",
                            icon = Icons.Default.BatteryAlert,
                            isGranted = batteryExempt,
                            isRequired = false,
                            onRequest = {
                                requestBatteryOptimizationExemption()
                            }
                        )
                    )
                }

                val navController = rememberNavController()

                // A snackbar sits over the bottom buttons: never let it cover «ذخیره» on the form.
                val backStackEntry by navController.currentBackStackEntryAsState()
                LaunchedEffect(backStackEntry?.destination?.route) {
                    if (backStackEntry?.destination?.route == "add") {
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                }

                val reminders by viewModel.reminders.collectAsState()

                val knownCategories by
                    viewModel.knownCategories.collectAsState()

                val knownItemsByCategory by
                    viewModel.knownItemsByCategory.collectAsState()

                val financialReminders by
                    viewModel.financialReminders.collectAsState()

                val financialSummary by
                    viewModel.financialSummary.collectAsState()

                val paymentLogs by
                    viewModel.paymentLogs.collectAsState()

                val medicationLogs by
                    viewModel.medicationLogs.collectAsState()

                val dashboardStats by
                    viewModel.dashboardStats.collectAsState()

                var editingReminder by remember {
                    mutableStateOf<
                        ir.naderinia.nsa.data.Reminder?
                    >(null)
                }

                var detailFilter by remember {
                    mutableStateOf<ReminderDetailFilter?>(null)
                }

                // What a *new* reminder should be pre-set to, depending on which
                // section the "یادآوری جدید" button was tapped in.
                var addPresetCategory by remember {
                    mutableStateOf<String?>(null)
                }

                var addPresetFinancialType by remember {
                    mutableStateOf<FinancialType?>(null)
                }

                // Opened from the home-screen widget's "quick add" button.
                LaunchedEffect(isUnlocked) {

                    if (
                        isUnlocked &&
                        intent?.getBooleanExtra(
                            EXTRA_NAVIGATE_TO_ADD,
                            false
                        ) == true
                    ) {

                        editingReminder = null
                        addPresetCategory = null
                        addPresetFinancialType = null

                        navController.navigate("add")

                        intent.removeExtra(
                            EXTRA_NAVIGATE_TO_ADD
                        )
                    }
                }

                if (!isUnlocked) {

                    LockScreen(
                        error = lockError,

                        showBiometricButton =
                            biometricAvailable &&
                                SecurityPrefs.isBiometricEnabled(
                                    this
                                ),

                        onPinEntered = { pin: CharArray ->

                            val waitMillis = SecurityPrefs.lockoutRemainingMillis(this)

                            if (waitMillis > 0) {
                                pin.fill('\u0000')
                                lockError = "چند بار رمز اشتباه زده شد؛ " +
                                    ir.naderinia.nsa.util.JalaliCalendar.toPersianDigits(
                                        "${(waitMillis + 999) / 1000} ثانیه صبر کن"
                                    )
                            } else if (
                                SecurityPrefs.verifyPin(
                                    this,
                                    pin
                                )
                            ) {

                                SecurityPrefs.resetFailedAttempts(this)
                                lockError = null
                                isUnlocked = true

                            } else {

                                val wait = SecurityPrefs.registerFailedAttempt(this)
                                lockError = if (wait > 0) {
                                    ir.naderinia.nsa.util.JalaliCalendar.toPersianDigits(
                                        "رمز اشتباهه؛ ${wait / 1000} ثانیه صبر کن"
                                    )
                                } else "رمز اشتباهه"
                            }
                        },

                        onBiometricClick = {

                            showBiometricPrompt(
                                onSuccess = {
                                    SecurityPrefs.resetFailedAttempts(this)
                                    lockError = null
                                    isUnlocked = true
                                }
                            )
                        }
                    )

                    return@NsaTheme
                }

                // Crash report from the previous run (if any) — shown after the lock screen.
                var pendingCrash by remember {
                    mutableStateOf(CrashReporter.readPending(this@MainActivity))
                }

                pendingCrash?.let { report ->
                    CrashReportDialog(
                        report = report,
                        onSend = {
                            CrashReporter.sendByEmail(this@MainActivity, report)
                            CrashReporter.clearPending(this@MainActivity)
                            pendingCrash = null
                        },
                        onDismiss = {
                            CrashReporter.clearPending(this@MainActivity)
                            pendingCrash = null
                        }
                    )
                }

                val pendingChangelog = remember {
                    Changelog.since(
                        ChangelogPrefs.getLastSeenVersion(
                            this
                        )
                    )
                }

                // Decided once per launch: granting/revoking something later must not
                // rebuild the nav graph under the person's feet.
                val startDestination = remember {
                    val requiredMissing =
                        permissionItems.any { it.isRequired && !it.isGranted }
                    val optionalMissing =
                        permissionItems.any { !it.isRequired && !it.isGranted }

                    when {
                        requiredMissing ||
                            (optionalMissing && !OnboardingPrefs.isSeen(this@MainActivity)) ->
                            "onboarding"

                        pendingChangelog.isNotEmpty() ->
                            "whatsnew"

                        else ->
                            "home"
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),

                    snackbarHost = {
                        SnackbarHost(
                            hostState = snackbarHostState
                        )
                    }
                ) {

                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.fillMaxSize(),
                        // Screens glide in from the "forward" side (Start/End follow the layout
                        // direction, so this is right-to-left in a Persian UI) with a soft fade.
                        enterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(320)
                            ) + fadeIn(tween(320))
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.Start,
                                animationSpec = tween(320),
                                targetOffset = { it / 4 }
                            ) + fadeOut(tween(220))
                        },
                        popEnterTransition = {
                            slideIntoContainer(
                                AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(320),
                                initialOffset = { it / 4 }
                            ) + fadeIn(tween(320))
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                AnimatedContentTransitionScope.SlideDirection.End,
                                animationSpec = tween(320)
                            ) + fadeOut(tween(220))
                        }
                    ) {

                        composable("onboarding") {

                            PermissionOnboardingScreen(
                                permissions = permissionItems,

                                onContinue = {

                                    OnboardingPrefs.setSeen(this@MainActivity)

                                    val next =
                                        if (
                                            pendingChangelog.isNotEmpty()
                                        ) {
                                            "whatsnew"
                                        } else {
                                            "home"
                                        }

                                    navController.navigate(next) {
                                        popUpTo("onboarding") {
                                            inclusive = true
                                        }
                                    }
                                }
                            )
                        }

                        composable("whatsnew") {

                            WhatsNewScreen(
                                entries = pendingChangelog,

                                onDismiss = {

                                    ChangelogPrefs.setLastSeenVersion(
                                        this@MainActivity,
                                        BuildConfig.VERSION_CODE
                                    )

                                    navController.navigate("home") {
                                        popUpTo("whatsnew") {
                                            inclusive = true
                                        }
                                    }
                                }
                            )
                        }

                        composable("about") {

                            AboutScreen(
                                versionName =
                                    BuildConfig.VERSION_NAME,

                                versionCode =
                                    BuildConfig.VERSION_CODE,

                                onShowChangelog = {
                                    navController.navigate("whatsnew")
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("home") {
                        
                            HomeScreen(
                                reminders = reminders,
                                financialReminders = financialReminders,
                                financialSummary = financialSummary,
                                paymentLogs = paymentLogs,
                                dashboardStats = dashboardStats,

                                onAddClick = { presetCategory, presetFinancialType ->
                                    editingReminder = null
                                    addPresetCategory = presetCategory
                                    addPresetFinancialType = presetFinancialType
                                    navController.navigate("add")
                                },

                                onRecordPayment = { reminder, amount ->
                                    viewModel.recordPayment(
                                        reminder,
                                        amount
                                    )
                                },

                                onCheckStatusChange = { reminder, status ->
                                    viewModel.setCheckStatus(
                                        reminder,
                                        status
                                    )
                                },

                                onSearchClick = {
                                    navController.navigate("search")
                                },

                                onReportClick = {
                                    navController.navigate("report")
                                },

                                themeMode = themeMode,

                                onThemeModeChange = { mode ->
                                    themeMode = mode
                                    ThemePrefs.setMode(this@MainActivity, mode)
                                },

                                onClassicStyleChange = { classic ->
                                    classicStyle = classic
                                    ThemePrefs.setClassic(this@MainActivity, classic)
                                },

                                onScanClick = {
                                    navController.navigate("scan")
                                },

                                onSecurityClick = {
                                    navController.navigate("security")
                                },

                                onSoundClick = {
                                    openRingtonePicker()
                                },

                                onBackupClick = {
                                    startBackup()
                                },

                                onRestoreClick = {
                                    startRestore()
                                },

                                onAboutClick = {
                                    navController.navigate("about")
                                },

                                onOpenActive = {
                                    detailFilter = ReminderDetailFilter.Active

                                    navController.navigate(
                                        "categoryDetail"
                                    )
                                },

                                onOpenToday = {
                                    detailFilter = ReminderDetailFilter.Today

                                    navController.navigate(
                                        "categoryDetail"
                                    )
                                },

                                onOpenOverdue = {
                                    detailFilter = ReminderDetailFilter.Overdue

                                    navController.navigate(
                                        "categoryDetail"
                                    )
                                },

                                onOpenCategory = { category ->

                                    detailFilter =
                                        ReminderDetailFilter.Category(category)

                                    navController.navigate(
                                        "categoryDetail"
                                    )
                                }
                            )
                        }

                        composable("categoryDetail") {

                            val filter = detailFilter

                            val (
                                detailTitle,
                                detailReminders
                            ) = when (filter) {
                            
                                is ReminderDetailFilter.Active ->
                                    "کارهای فعال" to
                                        reminders.filter {
                                            !it.isDone
                                        }
                            
                                is ReminderDetailFilter.Today ->
                                    "امروز" to
                                        dashboardStats.todayItems
                            
                                is ReminderDetailFilter.Overdue ->
                                    "عقب‌افتاده" to
                                        dashboardStats.overdueItems
                            
                                is ReminderDetailFilter.Category ->
                                    filter.name to
                                        reminders.filter {
                                            it.sectionKey() == filter.name
                                        }
                            
                                null ->
                                    "" to emptyList()
                            }

                            CategoryDetailScreen(
                                title = detailTitle,
                                reminders = detailReminders,

                                onToggleDone = {
                                    viewModel.toggleDone(it)
                                },

                                onDelete = {
                                    viewModel.deleteReminder(it)
                                },

                                onRecordPayment = { reminder, amount ->
                                    viewModel.recordPayment(
                                        reminder,
                                        amount
                                    )
                                },

                                onEdit = { reminder ->

                                    editingReminder = reminder
                                    addPresetCategory = null
                                    addPresetFinancialType = null

                                    navController.navigate(
                                        "add"
                                    )
                                },

                                onAddClick = {

                                    editingReminder = null

                                    // Inside a real section (e.g. وسیله نقلیه, جلسات) the new
                                    // reminder starts with that type already selected.
                                    // Today / overdue / active lists have no single type → عمومی.
                                    addPresetCategory =
                                        (filter as? ReminderDetailFilter.Category)?.name

                                    addPresetFinancialType = null

                                    navController.navigate("add")
                                },

                                medicationLogs = medicationLogs,

                                onMedicationTaken = {
                                    viewModel.markMedicationTaken(it)
                                },

                                onChecklistChange = { reminder, checklist ->
                                    viewModel.updateChecklist(reminder, checklist)
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("report") {

                            FinanceReportScreen(
                                financialReminders = financialReminders,
                                paymentLogs = paymentLogs,
                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("search") {

                            SearchScreen(
                                reminders = reminders,

                                medicationLogs = medicationLogs,

                                onMedicationTaken = {
                                    viewModel.markMedicationTaken(it)
                                },

                                onChecklistChange = { reminder, checklist ->
                                    viewModel.updateChecklist(reminder, checklist)
                                },

                                onToggleDone = {
                                    viewModel.toggleDone(it)
                                },

                                onDelete = {
                                    viewModel.deleteReminder(it)
                                },

                                onRecordPayment = { reminder, amount ->
                                    viewModel.recordPayment(
                                        reminder,
                                        amount
                                    )
                                },

                                onEdit = { reminder ->

                                    editingReminder = reminder
                                    addPresetCategory = null
                                    addPresetFinancialType = null

                                    navController.navigate("add")
                                },

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("security") {

                            SecuritySettingsScreen(
                                biometricAvailable =
                                    biometricAvailable,

                                onBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("scan") {

                            ScanReceiptScreen(

                                onSave = {
                                        title,
                                        amount,
                                        counterparty ->

                                    // Default the reminder to today at 9 AM since OCR
                                    // can't reliably read a Persian due date off the
                                    // receipt — the person can edit it later from the list.
                                    val trigger =
                                        Calendar.getInstance().apply {
                                            set(
                                                Calendar.HOUR_OF_DAY,
                                                9
                                            )
                                            set(
                                                Calendar.MINUTE,
                                                0
                                            )
                                            set(
                                                Calendar.SECOND,
                                                0
                                            )
                                            set(Calendar.MILLISECOND, 0)
                                            // 9 AM already passed today → tomorrow 9 AM, so the
                                            // new bill isn't born overdue.
                                            if (timeInMillis <= System.currentTimeMillis()) {
                                                add(Calendar.DAY_OF_YEAR, 1)
                                            }
                                        }.timeInMillis

                                    viewModel.addReminder(
                                        title = title,
                                        note = "ثبت‌شده از اسکن رسید",
                                        category = "قبض",
                                        categoryColor = 0xFFF59E0B,
                                        triggerAtMillis = trigger,
                                        repeatInterval =
                                            RepeatInterval.NONE,
                                        amount = amount,
                                        counterparty =
                                            counterparty.ifBlank {
                                                null
                                            },
                                        financialType =
                                            FinancialType.BILL
                                    )

                                    navController.popBackStack()
                                },

                                onCancel = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("add") {

                            AddReminderScreen(
                                knownCategories =
                                    knownCategories,

                                knownItemsByCategory =
                                    knownItemsByCategory,

                                editingReminder =
                                    editingReminder,

                                initialCategory =
                                    addPresetCategory,

                                initialFinancialType =
                                    addPresetFinancialType,

                                onSave = {
                                        title,
                                        note,
                                        category,
                                        color,
                                        trigger,
                                        repeat,
                                        amount,
                                        counterparty,
                                        phone,
                                        financialType,
                                        attachmentUri,
                                        mileageTargetKm,
                                        location,
                                        bankName,
                                        repeatDaysOfWeek,
                                        birthYear,
                                        checklist ->

                                    val current =
                                        editingReminder

                                    if (current != null) {

                                        viewModel.updateReminder(
                                            current.id,
                                            title,
                                            note,
                                            category,
                                            color,
                                            trigger,
                                            repeat,
                                            amount,
                                            counterparty,
                                            phone,
                                            financialType,
                                            attachmentUri,
                                            mileageTargetKm,
                                            location,
                                            bankName,
                                            repeatDaysOfWeek,
                                            birthYear,
                                            checklist
                                        )

                                    } else {

                                        viewModel.addReminder(
                                            title,
                                            note,
                                            category,
                                            color,
                                            trigger,
                                            repeat,
                                            amount,
                                            counterparty,
                                            phone,
                                            financialType,
                                            attachmentUri,
                                            mileageTargetKm,
                                            location,
                                            bankName,
                                            repeatDaysOfWeek,
                                            birthYear,
                                            checklist
                                        )
                                    }

                                    editingReminder = null
                                    addPresetCategory = null
                                    addPresetFinancialType = null

                                    navController.popBackStack()
                                },

                                onSaveInstallments = {
                                        title,
                                        note,
                                        category,
                                        color,
                                        firstTrigger,
                                        count,
                                        amountEach,
                                        counterparty,
                                        phone,
                                        attachmentUri,
                                        bankName ->

                                    viewModel.addInstallments(
                                        title,
                                        note,
                                        category,
                                        color,
                                        firstTrigger,
                                        count,
                                        amountEach,
                                        counterparty,
                                        phone,
                                        attachmentUri,
                                        bankName
                                    )

                                    editingReminder = null
                                    addPresetCategory = null
                                    addPresetFinancialType = null

                                    navController.popBackStack()
                                },

                                onCancel = {

                                    editingReminder = null
                                    addPresetCategory = null
                                    addPresetFinancialType = null

                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
    
        val callback = object : BiometricPrompt.AuthenticationCallback() {
        
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult
            ) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
    
            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence
            ) {
                super.onAuthenticationError(errorCode, errString)
    
                // لغو توسط کاربر یا Back — نیازی به نمایش خطا نیست
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    // خطاهای واقعی احراز هویت در اینجا قابل ثبت/نمایش هستند.
                }
            }
    
            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // سیستم خودش feedback مربوط به تلاش ناموفق را نمایش می‌دهد.
            }
        }
    
        val biometricPrompt = BiometricPrompt(this, executor, callback)
    
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود به یادآور من")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("استفاده از رمز")
            .build()
    
        biometricPrompt.authenticate(promptInfo)
    }

    /**
     * Many Android OEMs (Xiaomi, Samsung, Huawei) aggressively kill background
     * apps and drop scheduled alarms unless the app is exempted from battery
     * optimization. This is the #1 cause of "my reminder never fired" bug
     * reports on real devices, so it's requested explicitly from onboarding.
     */
    private fun requestBatteryOptimizationExemption() {

        val powerManager =
            getSystemService(POWER_SERVICE) as PowerManager

        if (
            !powerManager.isIgnoringBatteryOptimizations(
                packageName
            )
        ) {

            val intent =
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                ).apply {
                    data =
                        Uri.parse(
                            "package:$packageName"
                        )
                }

            // Some OEM ROMs don't ship that dialog — fall back to the general list.
            startActivitySafely(
                intent,
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            )
        }
    }

    private fun openAppNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        startActivitySafely(intent)
    }

    /**
     * Opens a system settings screen without ever crashing the app: if the
     * primary intent (or the optional fallback) isn't available on this device,
     * ends up on the app's own details page, which exists everywhere.
     */
    private fun startActivitySafely(primary: Intent, fallback: Intent? = null) {
        val attempts = listOfNotNull(
            primary,
            fallback,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
        for (intent in attempts) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // try the next one
            } catch (_: SecurityException) {
                // try the next one
            }
        }
    }
}