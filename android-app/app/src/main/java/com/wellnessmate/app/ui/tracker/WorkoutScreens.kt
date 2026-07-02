package com.wellnessmate.app.ui.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wellnessmate.app.data.TrackerEntryRequest
import com.wellnessmate.app.data.TrackerEntryResponse
import com.wellnessmate.app.ui.HealthProfileViewModel
import com.wellnessmate.app.ui.TrackerViewModel
import com.wellnessmate.app.ui.components.WellnessIconButton
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

// ── Workout type definitions with MET values ──────────────────────────

private data class WorkoutDef(
    val key: String,
    val label: String,
    val emoji: String,
    val met: Double,
    val category: String = "Other",
)

private val legacyWorkoutTypes = listOf(
    WorkoutDef("RUNNING",    "Running",     "🏃",  8.0),
    WorkoutDef("WALKING",    "Walking",     "🚶",  3.5),
    WorkoutDef("CYCLING",    "Cycling",     "🚴",  7.0),
    WorkoutDef("SWIMMING",   "Swimming",    "🏊",  8.0),
    WorkoutDef("STRENGTH",   "Strength",    "🏋️",  5.0),
    WorkoutDef("YOGA",       "Yoga",        "🧘",  3.0),
    WorkoutDef("HIIT",       "HIIT",        "⚡", 10.0),
    WorkoutDef("DANCE",      "Dance",       "💃",  5.5),
    WorkoutDef("BALL",       "Ball Games",  "⚽",  7.0),
    WorkoutDef("OTHER",      "Other",       "🏅",  5.0),
)

private val workoutCategories = listOf(
    "Running", "Walking", "Cycling", "Ball Sports", "Cardio Machines",
    "Gym & Strength", "Water Sports", "Dance & Aerobics", "Flexibility",
)

private val workoutTypes = listOf(
    WorkoutDef("RUNNING", "Easy Running", "🏃", 7.0, "Running"),
    WorkoutDef("JOGGING", "Jogging", "🏃", 6.0, "Running"),
    WorkoutDef("TEMPO_RUN", "Tempo Running", "🏃", 9.0, "Running"),
    WorkoutDef("TREADMILL_RUN", "Treadmill Running", "🏃", 8.0, "Running"),
    WorkoutDef("WALKING", "Slow Walking", "🚶", 2.8, "Walking"),
    WorkoutDef("BRISK_WALK", "Brisk Walking", "🚶", 4.3, "Walking"),
    WorkoutDef("DOG_WALK", "Dog Walking", "🐕", 3.0, "Walking"),
    WorkoutDef("HIKING", "Hiking", "🥾", 6.0, "Walking"),
    WorkoutDef("MOUNTAIN_CLIMB", "Mountain Climbing", "⛰", 7.5, "Walking"),
    WorkoutDef("CYCLING", "Leisure Cycling", "🚲", 5.0, "Cycling"),
    WorkoutDef("ROAD_CYCLING", "Road Cycling", "🚲", 8.0, "Cycling"),
    WorkoutDef("INDOOR_CYCLING", "Indoor Cycling", "🚲", 7.5, "Cycling"),
    WorkoutDef("BASKETBALL", "Basketball", "🏀", 8.0, "Ball Sports"),
    WorkoutDef("FOOTBALL", "Football", "⚽", 8.0, "Ball Sports"),
    WorkoutDef("BADMINTON", "Badminton", "🏸", 5.5, "Ball Sports"),
    WorkoutDef("TENNIS", "Tennis", "🎾", 7.0, "Ball Sports"),
    WorkoutDef("ELLIPTICAL", "Elliptical", "🏋", 5.0, "Cardio Machines"),
    WorkoutDef("ROWING_MACHINE", "Rowing Machine", "🚣", 7.0, "Cardio Machines"),
    WorkoutDef("STAIR_CLIMBER", "Stair Climber", "🪜", 8.0, "Cardio Machines"),
    WorkoutDef("TREADMILL_WALK", "Treadmill Walking", "🚶", 4.0, "Cardio Machines"),
    WorkoutDef("STRENGTH", "Strength Training", "🏋", 5.0, "Gym & Strength"),
    WorkoutDef("WEIGHT_LIFTING", "Weight Lifting", "🏋", 6.0, "Gym & Strength"),
    WorkoutDef("CIRCUIT_TRAINING", "Circuit Training", "💪", 8.0, "Gym & Strength"),
    WorkoutDef("BODYWEIGHT", "Bodyweight Training", "💪", 5.0, "Gym & Strength"),
    WorkoutDef("SWIMMING", "Swimming", "🏊", 8.0, "Water Sports"),
    WorkoutDef("WATER_AEROBICS", "Water Aerobics", "🏊", 5.5, "Water Sports"),
    WorkoutDef("KAYAKING", "Kayaking", "🚣", 5.0, "Water Sports"),
    WorkoutDef("DANCE", "Dance", "💃", 5.5, "Dance & Aerobics"),
    WorkoutDef("AEROBICS", "Aerobics", "🤸", 7.0, "Dance & Aerobics"),
    WorkoutDef("ZUMBA", "Zumba", "💃", 7.5, "Dance & Aerobics"),
    WorkoutDef("YOGA", "Yoga", "🧘", 3.0, "Flexibility"),
    WorkoutDef("PILATES", "Pilates", "🧘", 3.5, "Flexibility"),
    WorkoutDef("STRETCHING", "Stretching", "🤸", 2.3, "Flexibility"),
    WorkoutDef("MOBILITY", "Mobility Training", "🤸", 2.5, "Flexibility"),
)

private fun defForKey(key: String?): WorkoutDef =
    workoutTypes.firstOrNull { it.key == key } ?: workoutTypes.last()

private fun defForLabel(label: String?): WorkoutDef =
    workoutTypes.firstOrNull { it.key == label || it.label.equals(label, ignoreCase = true) }
        ?: workoutTypes.last()

fun estimateWorkoutCalories(weightKg: Double, durationMin: Double, detail: String?): Double {
    val met = workoutTypes.firstOrNull { it.key == detail || it.label.equals(detail, ignoreCase = true) }?.met
        ?: workoutTypes.last().met
    return met * weightKg * (durationMin / 60.0)
}

// ── Main workout tracker screen ───────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTrackerScreen(
    viewModel: TrackerViewModel,
    healthProfileViewModel: HealthProfileViewModel,
    selectedDate: LocalDate,
    onAddWorkout: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val profileState by healthProfileViewModel.state.collectAsState()
    val weightKg = profileState.profile?.currentWeightKg ?: 70.0

    var showInputSheet by rememberSaveable { mutableStateOf(false) }
    var editingEntry by rememberSaveable { mutableStateOf<TrackerEntryResponse?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    val entries = state.entries.filter { it.type == "WORKOUT" }
    val selectedEntries = entries.filter { entryDate(it) == selectedDate }

    // ── This week's summary ──
    val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekEnd = weekStart.plusDays(6)
    val weekEntries = entries.filter {
        val d = entryDate(it); d >= weekStart && d <= weekEnd
    }
    val weekDays = weekEntries.map { entryDate(it) }.distinct().size
    val weekTotalDuration = weekEntries.sumOf { it.amount }
    val weekTotalCalories = weekEntries.sumOf { estimateWorkoutCalories(weightKg, it.amount, it.detail) }
    val weekDailyAvgCal = weekTotalCalories / 7.0  // averaged over all 7 days

    // ── 7‑day chart data ──
    val chart = (6 downTo 0).map { offset ->
        val date = selectedDate.minusDays(offset.toLong())
        val dayEntries = entries.filter { entryDate(it) == date }
        val duration = dayEntries.sumOf { it.amount }
        val calories = dayEntries.sumOf { estimateWorkoutCalories(weightKg, it.amount, it.detail) }
        Triple(date, duration, calories)
    }

    // ── Workout type distribution for the current week ──
    val typeDistribution = weekEntries
        .groupBy { it.detail?.let { defForLabel(it).label } ?: "Other" }
        .map { (type, typeEntries) ->
            TypeSummary(
                type = type,
                emoji = defForLabel(type).emoji,
                totalMin = typeEntries.sumOf { it.amount },
                totalKcal = typeEntries.sumOf { estimateWorkoutCalories(weightKg, it.amount, it.detail) },
            )
        }
        .sortedByDescending { it.totalMin }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Workout Tracker", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onBack) { Text("Back") }
            }
            ErrorBanner(state.error, viewModel::clearError)
        }

        // ── Weekly summary card ──
        item {
            WorkoutWeekSummaryCard(
                weekStart = weekStart,
                weekEntries = weekEntries,
                weightKg = weightKg,
                workoutDays = weekDays,
                dailyAverageCalories = weekDailyAvgCal,
                totalDuration = weekTotalDuration,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F8FF)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("AI Adviser · Weekly review", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Personalized comments on your workout balance and progress will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        // ── Workout type distribution (horizontal bars) ──
        if (typeDistribution.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("This Week's Workouts", style = MaterialTheme.typography.titleMedium)
                        SpacerH(10)
                        WorkoutDistributionChart(typeDistribution, Modifier.fillMaxWidth())
                    }
                }
            }
        }

        // ── Calories 7‑day bar chart ──
        item {
            SevenDayBarChart(
                values = chart.map { it.first to it.third },
                unit = "kcal",
                showMissingPlaceholders = true,
            )
        }

        // ── Duration 7‑day bar chart ──
        item {
            SevenDayBarChart(
                values = chart.map { it.first to it.second },
                unit = "min",
                showMissingPlaceholders = true,
            )
        }

        item {
            Text(selectedDate.toString(), style = MaterialTheme.typography.titleLarge)
        }

        // ── Entry list for selected date ──
        if (state.loading) item { LoadingState() }
        else if (selectedEntries.isEmpty()) {
            item { Text("No workout recorded for this day.", modifier = Modifier.padding(vertical = 16.dp)) }
        } else items(selectedEntries, key = { it.id }) { entry ->
            WorkoutDayRow(
                entry = entry,
                weightKg = weightKg,
                onEdit = { editingEntry = entry; showInputSheet = true },
                onDelete = { deleteId = entry.id },
            )
            HorizontalDivider()
        }

        // Add / record button
        item {
            Button(
                onClick = onAddWorkout,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                Text(if (selectedEntries.isNotEmpty()) "Add more workout" else "Record workout")
            }
        }
    }

    // ── Workout input bottom sheet ──
    if (showInputSheet) {
        WorkoutInputSheet(
            date = selectedDate,
            existingEntry = editingEntry,
            weightKg = weightKg,
            onSave = { type, duration ->
                viewModel.save(
                    id = editingEntry?.id,
                    request = TrackerEntryRequest(
                        type = "WORKOUT",
                        recordedAt = editingEntry?.recordedAt ?: recordedAt(selectedDate),
                        amount = duration,
                        detail = type,
                        notes = null,
                    ),
                    onSaved = { showInputSheet = false; editingEntry = null },
                )
            },
            onDismiss = { showInputSheet = false; editingEntry = null },
        )
    }

    // ── Delete confirmation ──
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete workout?") },
            text = { Text("This workout record will be deleted.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(id); deleteId = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
        )
    }
}

// ── Workout day row ───────────────────────────────────────────────────

@Composable
private fun WorkoutWeekSummaryCard(
    weekStart: LocalDate,
    weekEntries: List<TrackerEntryResponse>,
    weightKg: Double,
    workoutDays: Int,
    dailyAverageCalories: Double,
    totalDuration: Double,
) {
    val weekEnd = weekStart.plusDays(6)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF2FBF7)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "${weekStart.monthValue}/${weekStart.dayOfMonth} – ${weekEnd.monthValue}/${weekEnd.dayOfMonth}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryTile("Goal", "$workoutDays / 7 days", Modifier.weight(1f))
                SummaryTile("Daily avg", "${dailyAverageCalories.roundToInt()} kcal", Modifier.weight(1f))
                SummaryTile("Duration", "${totalDuration.roundToInt()} min", Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                (0L..6L).forEach { offset ->
                    val date = weekStart.plusDays(offset)
                    val dayEntries = weekEntries.filter { entryDate(it) == date }
                    val calories = dayEntries.sumOf {
                        estimateWorkoutCalories(weightKg, it.amount, it.detail)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(date.dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall)
                        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall)
                        if (dayEntries.isNotEmpty()) {
                            Text("${calories.roundToInt()}k", style = MaterialTheme.typography.labelSmall, color = Color(0xFFD88A00))
                            Text("${dayEntries.sumOf { it.amount }.roundToInt()}m", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6A62A8))
                            Text(defForLabel(dayEntries.first().detail).label.take(3), style = MaterialTheme.typography.labelSmall, color = Color(0xFF168A61))
                        } else {
                            Text("—", color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.8f)).padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WorkoutDayRow(
    entry: TrackerEntryResponse,
    weightKg: Double,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val def = defForLabel(entry.detail)
    val calories = estimateWorkoutCalories(weightKg, entry.amount, entry.detail)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(def.emoji, style = MaterialTheme.typography.titleLarge)
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(def.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatAmount(entry.amount)} min · ${calories.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(formatTime(entry.recordedAt), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TextButton(onClick = onEdit) { Text("Edit") }
        TextButton(onClick = onDelete) { Text("Delete") }
    }
}

// ── Workout type distribution chart (horizontal bars) ──────────────────

@Composable
private fun WorkoutDistributionChart(
    data: List<TypeSummary>,  // already sorted by totalMin desc
    modifier: Modifier = Modifier,
) {
    val maxKcal = data.maxOfOrNull { it.totalKcal }?.coerceAtLeast(1.0) ?: 1.0
    val maxMin = data.maxOfOrNull { it.totalMin }?.coerceAtLeast(1.0) ?: 1.0

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.forEach { item ->
            val barFraction = ((item.totalMin / maxMin).coerceIn(0.05, 1.0)).toFloat()
            val barColor = calorieGradientColor(item.totalKcal / maxKcal)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Label
                Text(
                    "${item.emoji} ${item.type}",
                    modifier = Modifier.width(88.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(barColor.copy(alpha = 0.15f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barFraction)
                            .height(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(barColor),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            "${item.totalKcal.roundToInt()} kcal",
                            modifier = Modifier.padding(end = 6.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
                // Duration on the right
                Text(
                    "${item.totalMin.roundToInt()} min",
                    modifier = Modifier.width(52.dp).padding(start = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

// ── Calorie gradient: green → yellow → red ────────────────────────────

private fun calorieGradientColor(ratio: Double): Color {
    val t = ratio.coerceIn(0.0, 1.0)
    // Interpolate: #4CAF50 (green) → #FF9800 (orange at 0.5) → #F44336 (red)
    val rgb: Triple<Int, Int, Int> = if (t <= 0.5) {
        val s = t / 0.5
        Triple(
            (76 + (255 - 76) * s).toInt(),
            (175 + (152 - 175) * s).toInt(),
            (80 + (0 - 80) * s).toInt(),
        )
    } else {
        val s = (t - 0.5) / 0.5
        Triple(
            (255 + (244 - 255) * s).toInt(),
            (152 + (67 - 152) * s).toInt(),
            (0 + (54 - 0) * s).toInt(),
        )
    }
    return Color((0xFF shl 24) or (rgb.first shl 16) or (rgb.second shl 8) or rgb.third)
}

// ── Workout input bottom sheet ────────────────────────────────────────

private data class WorkoutDraft(val typeKey: String, val durationMinutes: Double)

@Composable
fun WorkoutSelectionScreen(
    viewModel: TrackerViewModel,
    healthProfileViewModel: HealthProfileViewModel,
    date: LocalDate,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val profileState by healthProfileViewModel.state.collectAsState()
    val weightKg = profileState.profile?.currentWeightKg ?: 70.0
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(workoutCategories.first()) }
    var selectedTypeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showSelected by rememberSaveable { mutableStateOf(false) }
    var drafts by remember { mutableStateOf(emptyList<WorkoutDraft>()) }
    val visibleWorkouts = workoutTypes.filter {
        it.category == selectedCategory && (query.isBlank() || it.label.contains(query, ignoreCase = true))
    }
    val totalCalories = drafts.sumOf {
        estimateWorkoutCalories(weightKg, it.durationMinutes, it.typeKey)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Add workout", style = MaterialTheme.typography.titleLarge)
                Text(date.toString(), style = MaterialTheme.typography.bodySmall)
            }
            WellnessIconButton("×", "Close", onBack)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search workout") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        Row(modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.weight(0.36f).padding(end = 4.dp)) {
                items(workoutCategories, key = { it }) { category ->
                    TextButton(
                        onClick = { selectedCategory = category },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            category,
                            style = if (category == selectedCategory) MaterialTheme.typography.titleSmall
                                else MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            LazyColumn(modifier = Modifier.weight(0.64f).padding(start = 4.dp)) {
                items(visibleWorkouts, key = { it.key }) { workout ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable { selectedTypeKey = workout.key },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(workout.emoji, style = MaterialTheme.typography.headlineSmall)
                            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(workout.label, style = MaterialTheme.typography.titleSmall)
                                Text("MET ${formatAmount(workout.met)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("+")
                        }
                    }
                }
                if (visibleWorkouts.isEmpty()) item {
                    Text("No matching workouts", modifier = Modifier.padding(16.dp))
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    onClick = { if (drafts.isNotEmpty()) showSelected = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Workout · ${drafts.size} selected", style = MaterialTheme.typography.titleMedium)
                        Text("${totalCalories.roundToInt()} kcal estimated")
                    }
                }
                Button(
                    onClick = {
                        viewModel.saveAll(
                            drafts.map { draft ->
                                TrackerEntryRequest(
                                    "WORKOUT", recordedAt(date), draft.durationMinutes,
                                    draft.typeKey, null,
                                )
                            },
                            onSaved = onDone,
                        )
                    },
                    enabled = drafts.isNotEmpty() && !state.saving,
                ) { Text(if (state.saving) "Saving…" else "Done") }
            }
        }
    }

    selectedTypeKey?.let { typeKey ->
        WorkoutInputSheet(
            date = date,
            existingEntry = null,
            initialTypeKey = typeKey,
            weightKg = weightKg,
            onSave = { selectedType, duration ->
                drafts = drafts + WorkoutDraft(selectedType, duration)
                selectedTypeKey = null
            },
            onDismiss = { selectedTypeKey = null },
        )
    }

    if (showSelected) {
        WorkoutSelectedBottomSheet(
            drafts = drafts,
            weightKg = weightKg,
            onRemove = { index -> drafts = drafts.toMutableList().also { it.removeAt(index) } },
            onDismiss = { showSelected = false },
        )
    }
}

@Composable
private fun LegacyWorkoutSelectionScreen(
    viewModel: TrackerViewModel,
    healthProfileViewModel: HealthProfileViewModel,
    date: LocalDate,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val profileState by healthProfileViewModel.state.collectAsState()
    val weightKg = profileState.profile?.currentWeightKg ?: 70.0
    var selectedTypeKey by rememberSaveable { mutableStateOf<String?>(null) }
    var drafts by remember { mutableStateOf(emptyList<WorkoutDraft>()) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Add workout", style = MaterialTheme.typography.headlineMedium)
                    Text(date.toString(), style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onBack) { Text("Back") }
            }
            Text("Choose an activity", style = MaterialTheme.typography.titleMedium)
        }
        items(workoutTypes, key = { it.key }) { workout ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .clickable { selectedTypeKey = workout.key },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(workout.emoji, style = MaterialTheme.typography.headlineSmall)
                        Text(workout.label, modifier = Modifier.padding(start = 12.dp))
                    }
                    Text("+")
                }
            }
        }
        if (drafts.isNotEmpty()) {
            item {
                Text(
                    "Selected workouts (${drafts.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            items(drafts.indices.toList(), key = { it }) { index ->
                val draft = drafts[index]
                val workout = defForKey(draft.typeKey)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${workout.emoji} ${workout.label}", modifier = Modifier.weight(1f))
                    Text("${formatAmount(draft.durationMinutes)} min")
                    TextButton(onClick = { drafts = drafts.toMutableList().also { it.removeAt(index) } }) {
                        Text("Remove")
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.saveAll(
                        drafts.map { draft ->
                            TrackerEntryRequest(
                                type = "WORKOUT",
                                recordedAt = recordedAt(date),
                                amount = draft.durationMinutes,
                                detail = draft.typeKey,
                                notes = null,
                            )
                        },
                        onSaved = onDone,
                    )
                },
                enabled = drafts.isNotEmpty() && !state.saving,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                Text(if (state.saving) "Saving…" else "Add selected workouts")
            }
            ErrorBanner(state.error, viewModel::clearError)
        }
    }

    selectedTypeKey?.let { typeKey ->
        WorkoutInputSheet(
            date = date,
            existingEntry = null,
            initialTypeKey = typeKey,
            weightKg = weightKg,
            onSave = { selectedType, duration ->
                drafts = drafts + WorkoutDraft(selectedType, duration)
                selectedTypeKey = null
            },
            onDismiss = { selectedTypeKey = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutSelectedBottomSheet(
    drafts: List<WorkoutDraft>,
    weightKg: Double,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Selected workouts (${drafts.size})", style = MaterialTheme.typography.titleLarge)
            drafts.forEachIndexed { index, draft ->
                val workout = defForKey(draft.typeKey)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(workout.emoji, style = MaterialTheme.typography.titleLarge)
                    Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(workout.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${formatAmount(draft.durationMinutes)} min · ${estimateWorkoutCalories(weightKg, draft.durationMinutes, draft.typeKey).roundToInt()} kcal",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    TextButton(onClick = { onRemove(index) }) { Text("Remove") }
                }
                HorizontalDivider()
            }
            WellnessIconButton(
                symbol = "×",
                contentDescription = "Close",
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutInputSheet(
    date: LocalDate,
    existingEntry: TrackerEntryResponse?,
    initialTypeKey: String? = null,
    weightKg: Double,
    onSave: (String, Double) -> Unit,  // (workoutTypeKey, durationMinutes)
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val existingDef = existingEntry?.detail?.let { defForLabel(it) }
    val selectedType = existingDef?.key ?: initialTypeKey ?: workoutTypes.first().key
    val initialMinutes = existingEntry?.amount?.let { formatAmount(it) } ?: ""
    var minutesText by rememberSaveable { mutableStateOf(initialMinutes) }
    var replaceOnNextInput by rememberSaveable { mutableStateOf(existingEntry == null) }
    val minutes = minutesText.toDoubleOrNull()
    val isValid = minutes != null && minutes in 1.0..1440.0
    val selectedDef = defForKey(selectedType)
    val estimatedCal = if (minutes != null) estimateWorkoutCalories(weightKg, minutes, selectedDef.label) else 0.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${date.monthValue}/${date.dayOfMonth}  Workout", style = MaterialTheme.typography.titleLarge)
            WellnessIconButton("×", "Close", onDismiss)
        }
        HorizontalDivider()

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Workout type grid ──
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(selectedDef.emoji, fontSize = 28.sp)
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(selectedDef.label, style = MaterialTheme.typography.titleMedium)
                        Text(selectedDef.category, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // ── Duration display ──
            Text(
                minutesText.ifBlank { "0" },
                color = Color(0xFF00B978),
                style = MaterialTheme.typography.displayMedium,
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.3f),
                color = Color(0xFF00B978),
            )
            Text("min", color = Color(0xFF00B978), modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))

            // Estimated calories
            Text(
                "≈ ${estimatedCal.roundToInt()} kcal",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Validation
            if (minutes != null && minutes > 1440) {
                Text("Duration exceeds 1440 min limit.", color = MaterialTheme.colorScheme.error)
            }
            if (minutes != null && minutes < 1) {
                Text("Duration must be at least 1 minute.", color = MaterialTheme.colorScheme.error)
            }

            WorkoutKeypad(
                value = minutesText,
                canSave = isValid,
                replaceOnNextInput = replaceOnNextInput,
                onValueChange = { minutesText = it },
                onInputStarted = { replaceOnNextInput = false },
                onSave = { minutes?.let { onSave(selectedDef.key, it) } },
            )
        }
    }
}

// ── Workout-specific keypad (integer minutes, no decimal) ─────────────

@Composable
private fun WorkoutKeypad(
    value: String,
    canSave: Boolean,
    replaceOnNextInput: Boolean,
    onValueChange: (String) -> Unit,
    onInputStarted: () -> Unit,
    onSave: () -> Unit,
) {
    fun append(character: String) {
        if (!replaceOnNextInput && value.length >= 4) return
        val next = when {
            replaceOnNextInput -> character
            value == "0" -> character
            else -> value + character
        }
        onInputStarted()
        onValueChange(next)
    }

    fun adjust(delta: Int) {
        val next = ((value.toIntOrNull() ?: 0) + delta).coerceAtLeast(0)
        onInputStarted()
        onValueChange(next.toString())
    }

    val rows = listOf(
        listOf("1", "2", "3", "⌫"),
        listOf("4", "5", "6", "+5"),
        listOf("7", "8", "9", "−5"),
        listOf("Reset", "0", "Save"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEach { key ->
                    if (key == "Save") {
                        Button(
                            onClick = onSave,
                            enabled = canSave,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B978)),
                            modifier = Modifier.weight(1f).height(58.dp),
                        ) { Text("Save") }
                    } else {
                        OutlinedButton(
                            onClick = {
                                when (key) {
                                    "⌫" -> {
                                        onInputStarted()
                                        onValueChange(value.dropLast(1).ifBlank { "0" })
                                    }
                                    "+5" -> adjust(5)
                                    "−5" -> adjust(-5)
                                    "Reset" -> {
                                        onInputStarted()
                                        onValueChange("0")
                                    }
                                    else -> append(key)
                                }
                            },
                            modifier = Modifier.weight(1f).height(58.dp),
                        ) { Text(key) }
                    }
                }
            }
        }
    }
}

// ── Small helpers ─────────────────────────────────────────────────────

@Composable
private fun SpacerH(dp: Int) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(dp.dp))
}

// TypeSummary is local to the distribution chart; redeclared here for the parameter type
private data class TypeSummary(val type: String, val emoji: String, val totalMin: Double, val totalKcal: Double)
