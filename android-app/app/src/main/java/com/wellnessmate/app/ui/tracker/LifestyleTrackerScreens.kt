package com.wellnessmate.app.ui.tracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.wellnessmate.app.data.TrackerEntryRequest
import com.wellnessmate.app.data.TrackerEntryResponse
import com.wellnessmate.app.reminder.ReminderScheduler
import com.wellnessmate.app.reminder.ReminderSettings
import com.wellnessmate.app.ui.TrackerViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun StepsTrackerScreen(
    viewModel: TrackerViewModel,
    selectedDate: LocalDate,
    onAdd: (String) -> Unit,
    onEdit: (TrackerEntryResponse) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var period by rememberSaveable { mutableStateOf(0) }
    var goal by rememberSaveable { mutableStateOf(loadIntSetting(context, "steps_goal", 5000)) }
    var showGoalEditor by rememberSaveable { mutableStateOf(false) }
    val entries = state.entries.filter { it.type == "STEPS" }
    val visibleEntries = when (period) {
        1 -> entries.filter { entryDate(it) in selectedDate.minusDays(6)..selectedDate }
        2 -> entries.filter { YearMonth.from(entryDate(it)) == YearMonth.from(selectedDate) }
        else -> entries.filter { entryDate(it) == selectedDate }
    }
    val steps = visibleEntries.sumOf { it.amount }.roundToInt()
    val progress = (steps.toFloat() / goal).coerceIn(0f, 1f)
    val distanceKm = steps * 0.00075
    val calories = (steps * 0.04).roundToInt()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F6FA)).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            LifestyleHeader("Steps", selectedDate, onBack)
            ErrorBanner(state.error, viewModel::clearError)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SegmentedTabs(
                        labels = listOf("Day", "Week", "Month"),
                        selected = period,
                        onSelected = { next ->
                            period = next
                            if (next == 2) viewModel.loadMonth("STEPS", YearMonth.from(selectedDate))
                            else viewModel.loadDate(selectedDate)
                        },
                    )
                    Text(
                        when (period) {
                            1 -> "Last 7 days"
                            2 -> "${selectedDate.year}/${selectedDate.monthValue}"
                            else -> "${selectedDate.monthValue}/${selectedDate.dayOfMonth}"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 22.dp),
                    )
                    CircularMetric(
                        progress = progress,
                        label = "Steps",
                        value = steps.toString(),
                        caption = "Goal $goal",
                        color = Color(0xFF18C6A3),
                        modifier = Modifier.padding(vertical = 22.dp),
                    )
                    HorizontalDivider(color = Color(0xFFECEEF4))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        MetricColumn("%.1f".format(distanceKm), "Distance (km)")
                        Box(Modifier.height(54.dp).background(Color(0xFFECEEF4)).padding(horizontal = 0.5.dp))
                        MetricColumn(calories.toString(), "Calories (kcal)")
                    }
                }
            }
        }
        item {
            SettingsRow("Daily goal", "$goal steps", onClick = { showGoalEditor = true })
            SettingsRow("Data source", "User input")
        }
        if (state.loading) {
            item { LoadingState() }
        } else {
            items(visibleEntries, key = { it.id }) { entry ->
                EntrySummaryCard(entry, onEdit = { onEdit(entry) }, onDelete = { viewModel.delete(entry.id) })
            }
        }
        item {
            Button(onClick = { onAdd("STEPS") }, modifier = Modifier.fillMaxWidth()) {
                Text("Add steps")
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showGoalEditor) {
        NumberSettingSheet(
            title = "Step goal",
            label = "Daily step goal",
            initialValue = goal.toString(),
            suffix = "steps",
            range = 1..100000,
            onDismiss = { showGoalEditor = false },
            onSave = { value ->
                goal = value
                saveIntSetting(context, "steps_goal", value)
                showGoalEditor = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaterTrackerScreen(
    viewModel: TrackerViewModel,
    selectedDate: LocalDate,
    onEdit: (TrackerEntryResponse) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val waterDrink = DrinkAction("Water", 400)
    val entries = state.entries.filter { it.type == "WATER" && entryDate(it) == selectedDate }
    val latestEntry = entries.maxByOrNull { it.recordedAt }
    val total = entries.sumOf { it.amount }.roundToInt()
    val goal = 2400
    val remaining = (goal - total).coerceAtLeast(0)
    val progress = (total.toFloat() / goal).coerceIn(0f, 1f)
    val waterHistory = (6 downTo 0).map { selectedDate.minusDays(it.toLong()) }.map { date ->
        date to state.entries.filter { it.type == "WATER" && entryDate(it) == date }.sumOf { it.amount }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            LifestyleHeader("Hydration", selectedDate, onBack)
            ErrorBanner(state.error, viewModel::clearError)
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Water goal  ${goal}ml", color = Color(0xFF8E93A3))
                    Text("Left  ${remaining}ml")
                }
            }
        }
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                WaterGlass(progress = progress, amount = total)
                Text("Progress: ${(progress * 100).roundToInt()}%", modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { latestEntry?.let { viewModel.delete(it.id) } },
                        enabled = latestEntry != null && !state.saving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("- Undo last")
                    }
                    Button(
                        onClick = {
                            viewModel.save(
                                id = null,
                                request = TrackerEntryRequest(
                                    type = "WATER",
                                    recordedAt = recordedAt(selectedDate),
                                    amount = 100.0,
                                    detail = "Quick add",
                                    notes = null,
                                ),
                                onSaved = {},
                            )
                        },
                        enabled = !state.saving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("+ 100ml")
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Quick drinks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        item {
            DrinkActionCard(
                drink = waterDrink,
                modifier = Modifier.fillMaxWidth(),
                onDrink = {
                    viewModel.save(
                        id = null,
                        request = TrackerEntryRequest(
                            type = "WATER",
                            recordedAt = recordedAt(selectedDate),
                            amount = waterDrink.amountMl.toDouble(),
                            detail = waterDrink.name,
                            notes = null,
                        ),
                        onSaved = {},
                    )
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Recent hydration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${total}ml today", color = Color(0xFF4FC3E8))
            }
        }
        item {
            if (state.loading) {
                LoadingState()
            } else {
                WaterHistoryChart(history = waterHistory, goal = goal)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

}

@Composable
fun SleepTrackerScreen(
    viewModel: TrackerViewModel,
    selectedDate: LocalDate,
    onAdd: (String) -> Unit,
    onEdit: (TrackerEntryResponse) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var sleepGoalMinutes by rememberSaveable { mutableStateOf(loadIntSetting(context, "sleep_goal_minutes", 480)) }
    var showSleepGoalEditor by rememberSaveable { mutableStateOf(false) }
    val entries = state.entries.filter { it.type == "SLEEP" && entryDate(it) == selectedDate }
    val hours = entries.sumOf { it.amount }
    val goal = sleepGoalMinutes / 60.0
    val progress = (hours / goal).toFloat().coerceIn(0f, 1f)
    val week = (6 downTo 0).map { selectedDate.minusDays(it.toLong()) }.map { date ->
        date to state.entries.filter { it.type == "SLEEP" && entryDate(it) == date }.sumOf { it.amount }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F6FA))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                LifestyleHeader("Sleep", selectedDate, onBack)
                ErrorBanner(state.error, viewModel::clearError)
            }
            item {
                Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(formatSleep(hours), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                    Text(" of ${formatSleep(goal)}", modifier = Modifier.padding(start = 6.dp, bottom = 7.dp))
                                }
                                Text("Sleep goal", color = Color(0xFF7D8494))
                            }
                            TextButton(onClick = { showSleepGoalEditor = true }) {
                                Text("Edit")
                            }
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                            color = Color(0xFF5DB130),
                        )
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Sleep reminder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Set bedtime and wake-up alarms from this tracker.", color = Color(0xFF7D8494), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        ReminderControl("Sleep time", ReminderScheduler.SLEEP_KEY, context)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ReminderControl("Wake up time", ReminderScheduler.WAKE_KEY, context)
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text("Sleep analysis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Last 7 days", color = Color(0xFF5DB130), modifier = Modifier.padding(top = 4.dp))
                        Spacer(Modifier.height(22.dp))
                        Text("Goal: ${formatSleep(goal)}")
                        HorizontalDivider(Modifier.padding(top = 6.dp, bottom = 12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            week.forEach { (date, value) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        Modifier.size(width = 14.dp, height = (24 + value * 8).dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (value >= goal) Color(0xFF5DB130) else Color(0xFFD9DDE8)),
                                    )
                                    Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        Text("Tip: Keep bedtime consistent and avoid caffeine late in the day.", modifier = Modifier.padding(top = 18.dp))
                    }
                }
            }
            if (state.loading) {
                item { LoadingState() }
            } else if (entries.isEmpty()) {
                item {
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No sleep tracked yet", style = MaterialTheme.typography.titleMedium)
                            Text("Tap + to add last night's sleep.", color = Color(0xFF7D8494))
                        }
                    }
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    EntrySummaryCard(entry, onEdit = { onEdit(entry) }, onDelete = { viewModel.delete(entry.id) })
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
        FloatingActionButton(
            onClick = { onAdd("SLEEP") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = Color(0xFF5DB130),
        ) { Text("+", style = MaterialTheme.typography.headlineMedium, color = Color.White) }
    }

    if (showSleepGoalEditor) {
        SleepGoalSheet(
            currentMinutes = sleepGoalMinutes,
            onDismiss = { showSleepGoalEditor = false },
            onSave = { minutes ->
                sleepGoalMinutes = minutes
                saveIntSetting(context, "sleep_goal_minutes", minutes)
                showSleepGoalEditor = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderControl(label: String, key: String, context: Context) {
    var settings by remember(key) { mutableStateOf(ReminderScheduler.settings(context, key)) }
    var showEditor by rememberSaveable(key) { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val next = settings.copy(enabled = true)
            settings = next
            ReminderScheduler.save(context, key, next)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).clickable { showEditor = true }) {
            Text(label)
            Text("%02d:%02d".format(settings.hour, settings.minute), color = Color(0xFF7D8494))
        }
        Switch(
            checked = settings.enabled,
            onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val next = settings.copy(enabled = enabled)
                    settings = next
                    ReminderScheduler.save(context, key, next)
                }
            },
        )
    }
    if (showEditor) {
        var hour by rememberSaveable(key) { mutableStateOf(settings.hour.toString()) }
        var minute by rememberSaveable(key) { mutableStateOf(settings.minute.toString().padStart(2, '0')) }
        var message by rememberSaveable(key) { mutableStateOf<String?>(null) }
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showEditor = false }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Edit $label", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hour") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minute") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        val h = hour.toIntOrNull()
                        val m = minute.toIntOrNull()
                        if (h !in 0..23 || m !in 0..59) {
                            message = "Enter a valid time."
                        } else {
                            val next = settings.copy(hour = h!!, minute = m!!, enabled = true)
                            settings = next
                            ReminderScheduler.save(context, key, next)
                            showEditor = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save alarm") }
                TextButton(onClick = { showEditor = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Cancel")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun LifestyleHeader(title: String, date: LocalDate, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(painterResource(com.wellnessmate.app.R.drawable.ic_back), "Back", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        Text("${date.monthValue}/${date.dayOfMonth}", color = Color(0xFF8E93A3))
    }
}

@Composable
private fun SegmentedTabs(labels: List<String>, selected: Int, onSelected: (Int) -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF1F3F8)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .background(if (index == selected) Color(0xFFFFA31A) else Color.Transparent)
                    .clickable { onSelected(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (index == selected) Color.White else Color(0xFF737988), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CircularMetric(
    progress: Float,
    label: String,
    value: String,
    caption: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(188.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            drawArc(Color(0xFFF0F2F8), -90f, 360f, false, style = stroke)
            drawArc(color, -90f, progress * 360f, false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = Color(0xFF737988))
            Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(caption, color = Color(0xFF9AA0AE))
        }
    }
}

@Composable
private fun WaterGlass(progress: Float, amount: Int) {
    Box(modifier = Modifier.size(width = 230.dp, height = 300.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize()) {
            val glass = Color(0x3329B6F6)
            val water = Color(0x9929B6F6)
            val border = Color(0x5529B6F6)
            val width = size.width * 0.72f
            val left = (size.width - width) / 2
            val top = size.height * 0.06f
            val bottom = size.height * 0.92f
            val waterTop = bottom - (bottom - top) * progress
            drawRoundRect(glass, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, bottom - top), cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()))
            drawRoundRect(water, topLeft = Offset(left + 8.dp.toPx(), waterTop), size = androidx.compose.ui.geometry.Size(width - 16.dp.toPx(), bottom - waterTop - 8.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()))
            drawRoundRect(border, topLeft = Offset(left, top), size = androidx.compose.ui.geometry.Size(width, bottom - top), cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()), style = Stroke(6.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.align(Alignment.Center)) {
            Text("${amount}ml", color = Color(0xFF29B6F6), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFF9AA0AE))
    }
}

@Composable
private fun SettingsRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(if (onClick != null) "$value  Edit" else value, color = Color(0xFF9AA0AE))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberSettingSheet(
    title: String,
    label: String,
    initialValue: String,
    suffix: String,
    range: IntRange,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var valueText by rememberSaveable(title) { mutableStateOf(initialValue) }
    var message by rememberSaveable(title) { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = valueText,
                onValueChange = { valueText = it.filter(Char::isDigit).take(6) },
                label = { Text(label) },
                suffix = { Text(suffix) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val value = valueText.toIntOrNull()
                    if (value == null || value !in range) {
                        message = "Enter a value from ${range.first} to ${range.last}."
                    } else {
                        onSave(value)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancel")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepGoalSheet(
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var hoursText by rememberSaveable { mutableStateOf((currentMinutes / 60).toString()) }
    var minutesText by rememberSaveable { mutableStateOf((currentMinutes % 60).toString().padStart(2, '0')) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sleep goal", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = hoursText,
                    onValueChange = { hoursText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Hours") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val hours = hoursText.toIntOrNull()
                    val minutes = minutesText.toIntOrNull()
                    val total = if (hours != null && minutes != null) hours * 60 + minutes else null
                    if (total == null || minutes !in 0..59 || total !in 60..1200) {
                        message = "Set a sleep goal from 1h 0m to 20h 0m."
                    } else {
                        onSave(total)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save goal") }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Cancel")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DrinkActionCard(
    drink: DrinkAction,
    modifier: Modifier,
    onDrink: () -> Unit,
) {
    Card(
        modifier = modifier.clickable { onDrink() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FC)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFE4F6FF)), contentAlignment = Alignment.Center) {
                    Text(drink.name.take(1).uppercase(), color = Color(0xFF29B6F6), fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        drink.name,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${drink.amountMl}ml", color = Color(0xFF7D8494))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tap card to add ${drink.amountMl}ml",
                    color = Color(0xFF29B6F6),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Text("+${drink.amountMl}ml", color = Color(0xFF29B6F6), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WaterHistoryChart(history: List<Pair<LocalDate, Double>>, goal: Int) {
    val maxValue = (history.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(goal.toDouble())
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFF)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Last 7 days", fontWeight = FontWeight.Bold)
                Text("Goal ${goal}ml", color = Color(0xFF8E93A3))
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(170.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                history.forEach { (date, amount) ->
                    val ratio = (amount / maxValue).toFloat().coerceIn(0f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("${amount.roundToInt()}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7D8494))
                        Box(
                            modifier = Modifier
                                .height((18 + ratio * 112).dp)
                                .size(width = 24.dp, height = (18 + ratio * 112).dp)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                .background(if (amount >= goal) Color(0xFF29B6F6) else Color(0xFFB9E9FC)),
                        )
                        Text("${date.monthValue}/${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { ((history.lastOrNull()?.second ?: 0.0) / goal).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF29B6F6),
            )
        }
    }
}

@Composable
private fun EntrySummaryCard(
    entry: TrackerEntryResponse,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.detail ?: entry.type.lowercase().replaceFirstChar(Char::uppercase), fontWeight = FontWeight.Medium)
                Text(formatTime(entry.recordedAt), color = Color(0xFF8E93A3))
            }
            Text("${formatAmount(entry.amount)} ${entry.unit}")
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

private data class DrinkAction(val name: String, val amountMl: Int)

private fun loadIntSetting(context: Context, key: String, defaultValue: Int): Int {
    return context.getSharedPreferences("lifestyle_tracker_settings", Context.MODE_PRIVATE)
        .getInt(key, defaultValue)
}

private fun saveIntSetting(context: Context, key: String, value: Int) {
    context.getSharedPreferences("lifestyle_tracker_settings", Context.MODE_PRIVATE).edit()
        .putInt(key, value)
        .apply()
}

private fun formatSleep(hours: Double): String {
    val whole = hours.toInt()
    val minutes = ((hours - whole) * 60).roundToInt()
    return "${whole}h ${minutes}m"
}
