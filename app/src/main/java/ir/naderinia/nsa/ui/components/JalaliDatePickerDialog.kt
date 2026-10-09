package ir.naderinia.nsa.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.naderinia.nsa.util.JalaliCalendar
import ir.naderinia.nsa.util.JalaliDate
import java.util.Calendar
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// ───────────────────────── helpers ─────────────────────────

private val WeekdayNames = arrayOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
private val WeekdayShort = arrayOf("ش", "ی", "د", "س", "چ", "پ", "ج")

/** 0 = شنبه … 6 = جمعه */
private fun weekdayIndex(jy: Int, jm: Int, jd: Int): Int {
    val cal = Calendar.getInstance().apply {
        timeInMillis = JalaliCalendar.jalaliToMillis(jy, jm, jd, 12, 0)
    }
    // Calendar: SUNDAY=1 … SATURDAY=7  →  %7 gives Saturday=0 … Friday=6
    return cal.get(Calendar.DAY_OF_WEEK) % 7
}

private fun fa(n: Int): String = JalaliCalendar.toPersianDigits(n.toString())
private fun fa2(n: Int): String = JalaliCalendar.toPersianDigits("%02d".format(n))

private fun longDate(y: Int, m: Int, d: Int): String =
    "${WeekdayNames[weekdayIndex(y, m, d)]}، ${fa(d)} ${JalaliCalendar.monthNames[m - 1]} ${fa(y)}"

internal fun longJalaliDate(millis: Long): String {
    val j = JalaliCalendar.millisToJalali(millis)
    return longDate(j.year, j.month, j.day)
}

internal fun jalaliTimeText(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "${fa2(cal.get(Calendar.HOUR_OF_DAY))}:${fa2(cal.get(Calendar.MINUTE))}"
}

// ───────────────────────── dialog ─────────────────────────

/**
 * Unified Jalali date + time picker.
 *  - Date tab: real month grid (Saturday-first, Friday highlighted), month arrows,
 *    tap the month title to pick a year.
 *  - Time tab: two snapping wheels (hour / minute) + quick presets.
 * Public API is unchanged, so no other screen needs to be touched.
 */
@Composable
fun JalaliDateTimePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (millis: Long) -> Unit
) {
    val initialJalali = remember(initialMillis) { JalaliCalendar.millisToJalali(initialMillis) }
    val initialCal = remember(initialMillis) { Calendar.getInstance().apply { timeInMillis = initialMillis } }
    val today = remember { JalaliCalendar.millisToJalali(System.currentTimeMillis()) }

    var year by remember { mutableIntStateOf(initialJalali.year) }
    var month by remember { mutableIntStateOf(initialJalali.month) }
    var day by remember { mutableIntStateOf(initialJalali.day) }
    var hour by remember { mutableIntStateOf(initialCal.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableIntStateOf(initialCal.get(Calendar.MINUTE)) }
    var tab by remember { mutableIntStateOf(0) }
    var showYears by remember { mutableStateOf(false) }

    val years = remember(today.year, initialJalali.year) {
        val from = minOf(today.year - 1, initialJalali.year)
        val to = maxOf(today.year + 30, initialJalali.year)
        (from..to).toList()
    }

    fun shiftMonth(delta: Int) {
        var m = month + delta
        var y = year
        if (m > 12) { m = 1; y++ } else if (m < 1) { m = 12; y-- }
        year = y
        month = m
        day = day.coerceAtMost(JalaliCalendar.daysInJalaliMonth(y, m))
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {

                    // Header: the two chips double as tabs
                    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                "انتخاب تاریخ و ساعت",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                HeaderChip(
                                    icon = Icons.Default.CalendarMonth,
                                    text = longDate(year, month, day),
                                    selected = tab == 0,
                                    onClick = { tab = 0 },
                                    modifier = Modifier.weight(1.7f)
                                )
                                HeaderChip(
                                    icon = Icons.Default.Schedule,
                                    text = "${fa2(hour)}:${fa2(minute)}",
                                    selected = tab == 1,
                                    onClick = { tab = 1 },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Body (fixed height so the dialog doesn't jump between tabs / months)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(318.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (tab == 0) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(onClick = { if (showYears) year-- else shiftMonth(-1) }) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "قبلی")
                                    }
                                    TextButton(
                                        onClick = { showYears = !showYears },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (showYears) fa(year)
                                            else "${JalaliCalendar.monthNames[month - 1]} ${fa(year)}",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    IconButton(onClick = { if (showYears) year++ else shiftMonth(1) }) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "بعدی")
                                    }
                                }
                                if (showYears) {
                                    YearGrid(years, year) { picked ->
                                        year = picked
                                        day = day.coerceAtMost(JalaliCalendar.daysInJalaliMonth(picked, month))
                                        showYears = false
                                    }
                                } else {
                                    MonthGrid(year, month, day, today) { day = it }
                                }
                            }
                        } else {
                            TimeContent(
                                hour = hour,
                                minute = minute,
                                onHour = { hour = it },
                                onMinute = { minute = it },
                                onPreset = { h, m -> hour = h; minute = m }
                            )
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            year = today.year; month = today.month; day = today.day
                            showYears = false
                            tab = 0
                        }) { Text("امروز") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("انصراف") }
                        TextButton(onClick = {
                            onConfirm(JalaliCalendar.jalaliToMillis(year, month, day, hour, minute))
                        }) { Text("تأیید") }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(
    icon: ImageVector,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else onContainer,
        border = if (selected) null else BorderStroke(1.dp, onContainer.copy(alpha = 0.3f))
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
}

// ───────────────────────── date tab ─────────────────────────

@Composable
private fun MonthGrid(
    year: Int,
    month: Int,
    selectedDay: Int,
    today: JalaliDate,
    onDayClick: (Int) -> Unit
) {
    val maxDay = JalaliCalendar.daysInJalaliMonth(year, month)
    val offset = weekdayIndex(year, month, 1)
    val primary = MaterialTheme.colorScheme.primary
    val error = MaterialTheme.colorScheme.error

    Column {
        Row {
            WeekdayShort.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (i == 6) error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        // Always 6 rows → constant height
        repeat(6) { r ->
            Row {
                repeat(7) { c ->
                    val d = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).height(38.dp), contentAlignment = Alignment.Center) {
                        if (d in 1..maxDay) {
                            val selected = d == selectedDay
                            val isToday = year == today.year && month == today.month && d == today.day
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .then(if (selected) Modifier.background(primary) else Modifier)
                                    .then(
                                        if (isToday && !selected) Modifier.border(1.dp, primary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { onDayClick(d) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    fa(d),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        selected -> MaterialTheme.colorScheme.onPrimary
                                        c == 6 -> error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearGrid(years: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    val startIndex = (years.indexOf(selected).coerceAtLeast(0) / 3) * 3
    val state = rememberLazyGridState(initialFirstVisibleItemIndex = startIndex)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = state,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(years.size) { i ->
            val y = years[i]
            FilterChip(
                selected = y == selected,
                onClick = { onPick(y) },
                label = {
                    Text(fa(y), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            )
        }
    }
}

// ───────────────────────── time tab ─────────────────────────

private val TimePresets = listOf(
    Triple("صبح", 8, 0),
    Triple("ظهر", 12, 0),
    Triple("عصر", 17, 0),
    Triple("شب", 21, 0)
)

@Composable
private fun TimeContent(
    hour: Int,
    minute: Int,
    onHour: (Int) -> Unit,
    onMinute: (Int) -> Unit,
    onPreset: (Int, Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Clock digits read left→right (HH:MM), so force LTR just for the wheels.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberWheel(count = 24, value = hour, onValueChange = onHour, modifier = Modifier.width(96.dp))
                Text(
                    ":",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                NumberWheel(count = 60, value = minute, onValueChange = onMinute, modifier = Modifier.width(96.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TimePresets.size) { i ->
                val (label, h, m) = TimePresets[i]
                FilterChip(
                    selected = hour == h && minute == m,
                    onClick = { onPreset(h, m) },
                    label = { Text("$label ${fa2(h)}:${fa2(m)}") }
                )
            }
        }
    }
}

/** Vertical snapping wheel: scroll (or tap) to choose a value in 0 until [count]. */
@Composable
private fun NumberWheel(
    count: Int,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 48.dp
    val itemPx = with(LocalDensity.current) { itemHeight.roundToPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value.coerceIn(0, count - 1))
    val fling = rememberSnapFlingBehavior(state)
    val scope = rememberCoroutineScope()
    val currentOnChange by rememberUpdatedState(onValueChange)

    val selected by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0)
                .coerceIn(0, count - 1)
        }
    }

    // Report the value once scrolling settles.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collect { currentOnChange(selected) }
    }
    // Follow external changes (e.g. quick presets).
    LaunchedEffect(value) {
        if (!state.isScrollInProgress && value != selected) state.animateScrollToItem(value)
    }

    Box(modifier.height(itemHeight * 3), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize()
        ) {
            items(count) { i ->
                val isSel = i == selected
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable { scope.launch { state.animateScrollToItem(i) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        fa2(i),
                        style = if (isSel) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.titleLarge,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}

// ───────────────────────── reusable pieces for the form ─────────────────────────

/** Tappable card that shows the chosen date + time (replaces the plain Button). */
@Composable
fun JalaliDateTimeField(millis: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "تاریخ و ساعت",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(longJalaliDate(millis), style = MaterialTheme.typography.titleMedium)
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    jalaliTimeText(millis),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private val WeekDayValues = listOf(
    Calendar.SATURDAY, Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
    Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY
)

/**
 * Multi-select weekday circles (شنبه … جمعه) for weekly repeat.
 * Values are java.util.Calendar.DAY_OF_WEEK constants — same as before,
 * so the stored `repeatDaysOfWeek` format doesn't change.
 */
@Composable
fun WeekDaySelector(
    selected: Set<Int>,
    onSelectedChange: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WeekDayValues.forEachIndexed { i, value ->
                val isSel = value in selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .semantics { contentDescription = WeekdayNames[i] }
                        .toggleable(
                            value = isSel,
                            role = Role.Checkbox,
                            onValueChange = {
                                onSelectedChange(if (isSel) selected - value else selected + value)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        WeekdayShort[i],
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSel) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (selected.isNotEmpty()) {
            Text(
                "هر هفته: " + WeekDayValues.withIndex()
                    .filter { it.value in selected }
                    .joinToString("، ") { WeekdayNames[it.index] },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
