package com.wellnessmate.app.ui.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.wellnessmate.app.R
import com.wellnessmate.app.data.ProfileResponse
import com.wellnessmate.app.data.SessionUser
import com.wellnessmate.app.data.TrackerEntryRequest
import com.wellnessmate.app.data.TrackerEntryResponse
import com.wellnessmate.app.data.TrackerTypeResponse
import com.wellnessmate.app.ui.TrackerViewModel
import com.wellnessmate.app.ui.FoodViewModel
import com.wellnessmate.app.ui.CoachChatViewModel
import com.wellnessmate.app.ui.HealthProfileViewModel
import com.wellnessmate.app.ui.AiAdvisorViewModel
import com.wellnessmate.app.ui.advisor.AiAdvisorScreen
import com.wellnessmate.app.ui.chat.CoachChatScreen
import com.wellnessmate.app.ui.food.FoodCameraScreen
import com.wellnessmate.app.ui.food.FoodPhotoReviewScreen
import com.wellnessmate.app.ui.food.FoodSelectionScreen
import com.wellnessmate.app.ui.food.FoodTrackerScreen
import com.wellnessmate.app.ui.health.HealthProfileScreen
import com.wellnessmate.app.ui.health.HealthSummaryCard
import com.wellnessmate.app.ui.health.HeightPickerScreen
import com.wellnessmate.app.ui.user.ReminderScreen
import com.wellnessmate.app.ui.user.UserManagementScreen
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

private const val HOME = "home"
private const val COACH = "coach"
private const val ADVISOR = "advisor"
private const val USER_MANAGEMENT = "user-management"
private const val REMINDER = "reminder"
private const val HEALTH_PROFILE = "health-profile"
private const val HEIGHT_PICKER = "height-picker"
private const val TRACKER = "tracker/{type}"
private const val WEIGHT_TRENDS = "weight-trends"
private const val FORM = "form/{type}/{id}"
private const val FOOD = "food"
private const val FOOD_SELECT = "food-select/{date}/{meal}"
private const val FOOD_CAMERA = "food-camera/{date}/{meal}"
private const val WORKOUT_SELECT = "workout-select/{date}"
private const val FOOD_REVIEW = "food-review"

/** Main post-onboarding navigation for trackers and coach chat. @author TODO(team member) */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainTrackerNav(
    user: SessionUser,
    viewModel: TrackerViewModel,
    foodViewModel: FoodViewModel,
    coachChatViewModel: CoachChatViewModel,
    healthProfileViewModel: HealthProfileViewModel,
    aiAdvisorViewModel: AiAdvisorViewModel,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val selectedDate = LocalDate.parse(selectedDateText)

    LaunchedEffect(selectedDateText) {
        viewModel.loadDate(selectedDate)
        foodViewModel.loadDate(selectedDate)
    }

    Scaffold(
        topBar = {
            if (route != FOOD_SELECT && route != FOOD_CAMERA && route != WORKOUT_SELECT) TopAppBar(
                title = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp).clickable { navController.navigate(USER_MANAGEMENT) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text((user.displayName ?: user.username).take(1).uppercase())
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text("📅 ${selectedDate.monthValue}/${selectedDate.dayOfMonth}")
                    }
                },
            )
        },
        bottomBar = {
            if (user.role != "COACH" && (route == HOME || route == ADVISOR || route == COACH)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { navController.navigate(HOME) { launchSingleTop = true } }) {
                        Text("Home")
                    }
                    TextButton(onClick = { navController.navigate(ADVISOR) { launchSingleTop = true } }) {
                        Text("AI Advisor")
                    }
                    TextButton(onClick = { navController.navigate(COACH) { launchSingleTop = true } }) {
                        Text("Coach")
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController,
            startDestination = if (user.role == "COACH") COACH else HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(HOME) {
                LaunchedEffect(Unit) {
                    healthProfileViewModel.refresh()
                }
                HomeScreen(
                    user = user,
                    viewModel = viewModel,
                    foodViewModel = foodViewModel,
                    healthProfileViewModel = healthProfileViewModel,
                    onHealth = { navController.navigate(HEALTH_PROFILE) },
                    onTracker = { type ->
                        navController.navigate(if (type == "FOOD") FOOD else "tracker/$type")
                    },
                    onAddFood = { meal -> navController.navigate("food-select/$selectedDate/$meal") },
                    onTakeFoodPhoto = { navController.navigate("food-camera/$selectedDate/SNACK") },
                    selectedDate = selectedDate,
                )
            }
            composable(ADVISOR) { AiAdvisorScreen(aiAdvisorViewModel) }
            composable(COACH) {
                CoachChatScreen(user, coachChatViewModel)
            }
            composable(USER_MANAGEMENT) {
                UserManagementScreen(
                    user = user,
                    onProfile = { navController.navigate(HEALTH_PROFILE) },
                    onReminder = { navController.navigate(REMINDER) },
                    onLogout = onLogout,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(REMINDER) {
                ReminderScreen(onBack = { navController.popBackStack() })
            }
            composable(HEALTH_PROFILE) {
                LaunchedEffect(Unit) { healthProfileViewModel.refresh() }
                HealthProfileScreen(
                    viewModel = healthProfileViewModel,
                    onHeight = { navController.navigate(HEIGHT_PICKER) },
                    onWeight = { navController.navigate("tracker/WEIGHT") },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(HEIGHT_PICKER) {
                HeightPickerScreen(
                    viewModel = healthProfileViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = TRACKER,
                arguments = listOf(navArgument("type") { type = NavType.StringType }),
            ) { entry ->
                val trackerType = entry.arguments?.getString("type") ?: "WATER"
                if (trackerType == "WORKOUT") {
                    WorkoutTrackerScreen(
                        viewModel = viewModel,
                        healthProfileViewModel = healthProfileViewModel,
                        selectedDate = selectedDate,
                        onAddWorkout = { navController.navigate("workout-select/$selectedDate") },
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    TrackerDetailScreen(
                        type = trackerType,
                        viewModel = viewModel,
                        healthProfileViewModel = healthProfileViewModel,
                        selectedDate = selectedDate,
                        onEdit = { navController.navigate("form/${it.type}/${it.id}") },
                        onAdd = { navController.navigate("form/$it/-1") },
                        onBack = {
                            if (trackerType == "WEIGHT") healthProfileViewModel.refresh()
                            navController.popBackStack()
                        },
                        onWeightTrends = { navController.navigate(WEIGHT_TRENDS) },
                    )
                }
            }
            composable(WEIGHT_TRENDS) {
                WeightTrendsScreen(
                    viewModel = viewModel,
                    profile = healthProfileViewModel.state.value.profile,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(FOOD) {
                FoodTrackerScreen(
                    viewModel = foodViewModel,
                    selectedDate = selectedDate,
                    healthProfileViewModel = healthProfileViewModel,
                    onTakePhoto = { date, meal -> navController.navigate("food-camera/$date/$meal") },
                    onAddFood = { date, meal -> navController.navigate("food-select/$date/$meal") },
                    onReviewPhoto = { navController.navigate(FOOD_REVIEW) },
                    onBack = { navController.popBackStack() },
                    onTrackerChanged = { viewModel.loadDate(selectedDate) },
                )
            }
            composable(
                route = FOOD_SELECT,
                arguments = listOf(
                    navArgument("date") { type = NavType.StringType },
                    navArgument("meal") { type = NavType.StringType },
                ),
            ) { entry ->
                FoodSelectionScreen(
                    viewModel = foodViewModel,
                    initialDate = LocalDate.parse(entry.arguments?.getString("date")),
                    initialMealType = entry.arguments?.getString("meal") ?: "SNACK",
                    onBack = {
                        foodViewModel.loadDate(selectedDate)
                        navController.popBackStack()
                    },
                    onTrackerChanged = { viewModel.loadDate(selectedDate) },
                )
            }
            composable(
                route = FOOD_CAMERA,
                arguments = listOf(
                    navArgument("date") { type = NavType.StringType },
                    navArgument("meal") { type = NavType.StringType },
                ),
            ) { entry ->
                FoodCameraScreen(
                    viewModel = foodViewModel,
                    date = LocalDate.parse(entry.arguments?.getString("date")),
                    mealType = entry.arguments?.getString("meal") ?: "SNACK",
                    onComplete = {
                        navController.navigate(FOOD_REVIEW) {
                            popUpTo(FOOD_CAMERA) { inclusive = true }
                        }
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
            composable(FOOD_REVIEW) {
                FoodPhotoReviewScreen(
                    viewModel = foodViewModel,
                    onSaved = { navController.popBackStack(FOOD, inclusive = false) },
                    onDiscard = { navController.popBackStack(FOOD, inclusive = false) },
                    onTrackerChanged = { viewModel.loadDate(selectedDate) },
                )
            }
            composable(
                route = WORKOUT_SELECT,
                arguments = listOf(navArgument("date") { type = NavType.StringType }),
            ) { entry ->
                WorkoutSelectionScreen(
                    viewModel = viewModel,
                    healthProfileViewModel = healthProfileViewModel,
                    date = LocalDate.parse(entry.arguments?.getString("date")),
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = FORM,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.LongType },
                ),
            ) { entry ->
                val formType = entry.arguments?.getString("type") ?: "WATER"
                if (formType == "WORKOUT") {
                    WorkoutTrackerScreen(
                        viewModel = viewModel,
                        healthProfileViewModel = healthProfileViewModel,
                        selectedDate = selectedDate,
                        onAddWorkout = { navController.navigate("workout-select/$selectedDate") },
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    TrackerFormScreen(
                        type = formType,
                        id = entry.arguments?.getLong("id")?.takeIf { it >= 0 },
                        selectedDate = selectedDate,
                        viewModel = viewModel,
                        onDone = { navController.popBackStack() },
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val tomorrowUtcMillis = LocalDate.now().plusDays(1).atStartOfDay()
            .toInstant(ZoneOffset.UTC).toEpochMilli()
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis < tomorrowUtcMillis
                override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        val picked = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        selectedDateText = picked.coerceAtMost(LocalDate.now()).toString()
                    }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun HomeScreen(
    user: SessionUser,
    viewModel: TrackerViewModel,
    foodViewModel: FoodViewModel,
    healthProfileViewModel: HealthProfileViewModel,
    onHealth: () -> Unit,
    onTracker: (String) -> Unit,
    onAddFood: (String) -> Unit,
    onTakeFoodPhoto: () -> Unit,
    selectedDate: LocalDate,
) {
    val state by viewModel.state.collectAsState()
    val foodState by foodViewModel.state.collectAsState()
    val profileState by healthProfileViewModel.state.collectAsState()
    if (state.loading) return LoadingState()
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text(
                stringResource(R.string.hello_user, user.displayName ?: user.username),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text("Overview for $selectedDate", modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            HealthSummaryCard(healthProfileViewModel, onHealth)
            Text("Trackers", style = MaterialTheme.typography.titleLarge)
            ErrorBanner(state.error, viewModel::clearError)
        }
        items(state.types) { type ->
            if (type.type == "FOOD") {
                val foodEntries = foodState.entries.filter { entry ->
                    runCatching {
                        Instant.parse(entry.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    }.getOrNull() == selectedDate
                }
                FoodHomeCard(
                    calories = foodEntries.sumOf { it.totals.calories },
                    carbohydrates = foodEntries.sumOf { it.totals.carbohydrateGrams },
                    protein = foodEntries.sumOf { it.totals.proteinGrams },
                    fat = foodEntries.sumOf { it.totals.fatGrams },
                    onOpen = { onTracker("FOOD") },
                    onAddFood = onAddFood,
                    onTakePhoto = onTakeFoodPhoto,
                )
                return@items
            }
            if (type.type == "WEIGHT") {
                val weightEntries = state.entries.filter { it.type == "WEIGHT" }
                val latest = weightEntries.maxByOrNull { it.recordedAt }
                val trend = (6 downTo 0).map { offset ->
                    val date = selectedDate.minusDays(offset.toLong())
                    date to (weightEntries.filter { entryDate(it) == date }
                        .maxByOrNull { it.recordedAt }?.amount ?: 0.0)
                }
                WeightHomeCard(
                    weight = latest?.amount,
                    unit = type.unit,
                    updatedAt = latest?.recordedAt,
                    trend = trend,
                    onOpen = { onTracker("WEIGHT") },
                )
                return@items
            }
            if (type.type == "WORKOUT") {
                val weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weekEnd = weekStart.plusDays(6)
                val workoutEntries = state.entries.filter {
                    it.type == "WORKOUT" && entryDate(it) in weekStart..weekEnd
                }
                val weightKg = profileState.profile?.currentWeightKg ?: 70.0
                val caloriesByDay = (0L..6L).map { offset ->
                    val date = weekStart.plusDays(offset)
                    date to workoutEntries.filter { entryDate(it) == date }
                        .sumOf { estimateWorkoutCalories(weightKg, it.amount, it.detail) }
                }
                WorkoutHomeCard(
                    workoutDays = workoutEntries.map(::entryDate).distinct().size,
                    dailyAverageCalories = caloriesByDay.sumOf { it.second } / 7.0,
                    caloriesByDay = caloriesByDay,
                    onOpen = { onTracker("WORKOUT") },
                )
                return@items
            }
            val dayEntries = state.entries.filter { it.type == type.type && entryDate(it) == selectedDate }
            val value = if (type.type == "WEIGHT") dayEntries.maxByOrNull { it.recordedAt }?.amount
                else dayEntries.sumOf { it.amount }.takeIf { dayEntries.isNotEmpty() }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onTracker(type.type) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(typeLabel(type.type), style = MaterialTheme.typography.titleMedium)
                        Text(value?.let { "${formatAmount(it)} ${type.unit}" } ?: "No data")
                    }
                    Text("View")
                }
            }
        }
        item {
            TextButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.refresh))
            }
        }
    }
}

@Composable
private fun FoodHomeCard(
    calories: Double,
    carbohydrates: Double,
    protein: Double,
    fat: Double,
    onOpen: () -> Unit,
    onAddFood: (String) -> Unit,
    onTakePhoto: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Food", style = MaterialTheme.typography.titleMedium)
                    Text("${formatAmount(calories)} kcal")
                }
                TextButton(onClick = onOpen) { Text("View") }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Carbs ${formatAmount(carbohydrates)} g")
                Text("Protein ${formatAmount(protein)} g")
                Text("Fat ${formatAmount(fat)} g")
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(
                    Triple("🌅", "BF", "BREAKFAST"),
                    Triple("☀️", "LN", "LUNCH"),
                    Triple("🌙", "DN", "DINNER"),
                    Triple("🍎", "SN", "SNACK"),
                ).forEach { (icon, abbreviation, meal) ->
                    OutlinedButton(
                        onClick = { onAddFood(meal) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(icon)
                            Text(abbreviation, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Button(onClick = onTakePhoto, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("Recognize food from photo")
            }
        }
    }
}

@Composable
private fun WeightHomeCard(
    weight: Double?,
    unit: String,
    updatedAt: String?,
    trend: List<Pair<LocalDate, Double>>,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Weight", style = MaterialTheme.typography.titleMedium)
                    Text(weight?.let { "${formatAmount(it)} $unit" } ?: "No data")
                    updatedAt?.let {
                        Text(
                            "Updated ${formatTime(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text("View")
            }
            MiniWeightLineChart(trend, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun WorkoutHomeCard(
    workoutDays: Int,
    dailyAverageCalories: Double,
    caloriesByDay: List<Pair<LocalDate, Double>>,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Workout", style = MaterialTheme.typography.titleMedium)
                Text("View")
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                InfoChip("Days this week", "$workoutDays / 7")
                InfoChip("Daily avg", "${dailyAverageCalories.roundToInt()} kcal")
            }
            MiniSevenDayBarChart(caloriesByDay, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun TrackerDetailScreen(
    type: String,
    viewModel: TrackerViewModel,
    healthProfileViewModel: HealthProfileViewModel,
    selectedDate: LocalDate,
    onEdit: (TrackerEntryResponse) -> Unit,
    onAdd: (String) -> Unit,
    onBack: () -> Unit,
    onWeightTrends: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val profileState by healthProfileViewModel.state.collectAsState()
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showWeightSheet by rememberSaveable { mutableStateOf(false) }
    var editingWeightEntry by rememberSaveable { mutableStateOf<TrackerEntryResponse?>(null) }
    var showGoalSheet by rememberSaveable { mutableStateOf(false) }
    val entries = state.entries.filter { it.type == type }
    val selectedEntries = entries.filter { entryDate(it) == selectedDate }
    val definition = state.types.firstOrNull { it.type == type }
    val chart = (6 downTo 0).map { offset ->
        val date = selectedDate.minusDays(offset.toLong())
        val dayEntries = entries.filter { entryDate(it) == date }
        val value = if (type == "WEIGHT") dayEntries.maxByOrNull { it.recordedAt }?.amount ?: 0.0
            else dayEntries.sumOf { it.amount }
        date to value
    }

    // Weight-specific derived data
    val isWeight = type == "WEIGHT"
    val profile = profileState.profile
    val latestWeight = selectedEntries.maxByOrNull { it.recordedAt }?.amount
    val latestDetail = selectedEntries.maxByOrNull { it.recordedAt }?.detail
    val storedBodyFat = latestDetail?.toDoubleOrNull()
    val bmi = if (isWeight && latestWeight != null && profile != null) {
        latestWeight / ((profile.heightCm / 100.0) * (profile.heightCm / 100.0))
    } else null
    val calculatedBodyFat = if (bmi != null && profile != null) {
        val age = runCatching {
            Period.between(LocalDate.parse(profile.dateOfBirth), LocalDate.now()).years.coerceAtLeast(0)
        }.getOrDefault(0)
        val sexTerm = if (profile.sex == "MALE") 16.2 else 5.4
        1.20 * bmi + 0.23 * age - sexTerm
    } else null
    val bodyFat = storedBodyFat ?: calculatedBodyFat
    val bodyFatLabel = if (storedBodyFat != null) "Body Fat*" else "Body Fat"
    val latestEntryDate = selectedEntries.maxByOrNull { it.recordedAt }?.let { entryDate(it) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(typeLabel(type), style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onBack) { Text("Back") }
            }
            ErrorBanner(state.error, viewModel::clearError)
        }

        // Current info card (weight specific)
        if (isWeight && latestWeight != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FF)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Current Status", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            InfoChip("Weight", "${formatAmount(latestWeight)} kg")
                            InfoChip("BMI", bmi?.let { formatAmount(it) } ?: "--")
                            InfoChip(bodyFatLabel, bodyFat?.let { "${formatAmount(it)}%" } ?: "--%")
                        }
                        if (storedBodyFat != null) {
                            Text(
                                "* Measured value",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        latestEntryDate?.let {
                            Text(
                                "Recorded: $it",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        // Chart
        item {
            if (isWeight) {
                WeightLineChart(
                    values = chart,
                    unit = definition?.unit.orEmpty(),
                    onClick = onWeightTrends,
                )
            } else {
                SevenDayBarChart(chart, definition?.unit.orEmpty())
            }
            Text(selectedDate.toString(), style = MaterialTheme.typography.titleLarge)
        }

        // Weight plan progress card (with goal set)
        if (isWeight && profile != null && profile.targetWeightKg != null && latestWeight != null) {
            item {
                val target = profile.targetWeightKg
                val startWeight = profile.currentWeightKg
                val totalChange = startWeight - target
                val achieved = startWeight - latestWeight
                val progress = if (totalChange != 0.0) (achieved / totalChange).coerceIn(0.0, 1.0) else 0.0

                val weekAgoWeight = entries
                    .filter { entryDate(it) == selectedDate.minusDays(7) }
                    .maxByOrNull { it.recordedAt }?.amount
                val weeklyChange = weekAgoWeight?.let { latestWeight - it }

                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Weight Goal Progress", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = { showGoalSheet = true }) { Text("Edit Goal") }
                        }
                        Text(
                            "${formatAmount(latestWeight)} kg → ${formatAmount(target)} kg",
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        LinearProgressIndicator(
                            progress = { progress.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = Color(0xFF00B978),
                        )
                        Text(
                            "${(progress * 100).roundToInt()}% complete",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val remaining = target - latestWeight
                        val isGainGoal = remaining > 0
                        val direction = if (isGainGoal) "to gain" else "to lose"
                        val absRemaining = kotlin.math.abs(remaining)
                        val estimateText = if (weeklyChange != null && weeklyChange != 0.0) {
                            val weeks = (absRemaining / kotlin.math.abs(weeklyChange)).roundToInt()
                            "${formatAmount(absRemaining)} kg $direction · ~$weeks weeks remaining"
                        } else {
                            "${formatAmount(absRemaining)} kg $direction"
                        }
                        Text(estimateText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
                        weeklyChange?.let { change ->
                            val arrow = if (change > 0) "↑" else if (change < 0) "↓" else "→"
                            val movingTowardGoal = if (isGainGoal) change > 0 else change < 0
                            val goalLabel = when {
                                change == 0.0 -> "unchanged"
                                movingTowardGoal -> "toward goal"
                                else -> "away from goal"
                            }
                            Text(
                                "Weekly: $arrow ${formatAmount(kotlin.math.abs(change))} kg · $goalLabel",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // Set goal card (when no goal exists)
        if (isWeight && profile != null && profile.targetWeightKg == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { showGoalSheet = true },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🎯 Set Weight Goal", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Track your progress toward a target weight",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Text("Set ›", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Entries list
        if (state.loading) item { LoadingState() }
        else if (selectedEntries.isEmpty()) item { Text("No data for this day.", modifier = Modifier.padding(vertical = 16.dp)) }
        else items(selectedEntries, key = { it.id }) { item ->
            TrackerDayRow(
                item,
                editable = true,
                deletable = !isWeight,
                onEdit = {
                    if (isWeight) {
                        editingWeightEntry = item
                        showWeightSheet = true
                    } else {
                        onEdit(item)
                    }
                },
                onDelete = { deleteId = item.id },
            )
            HorizontalDivider()
        }

        // Add/Update button
        item {
            Button(
                onClick = {
                    if (isWeight) {
                        editingWeightEntry = null
                        showWeightSheet = true
                    } else {
                        val existing = selectedEntries.firstOrNull()
                        if (existing != null) onEdit(existing) else onAdd(type)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                Text(
                    if (isWeight && selectedEntries.isNotEmpty()) "Update this day's weight"
                    else if (isWeight) "Record weight"
                    else if (selectedEntries.isNotEmpty()) "Update this day's entry"
                    else "Add data for this day",
                )
            }
        }
    }

    // Weight input bottom sheet
    if (showWeightSheet) {
        val existingBodyFat = editingWeightEntry?.detail?.toDoubleOrNull()
        WeightInputBottomSheet(
            date = selectedDate,
            existingWeight = editingWeightEntry?.amount,
            existingBodyFat = existingBodyFat,
            existingId = editingWeightEntry?.id,
            onSave = { amount, bodyFat ->
                viewModel.save(
                    id = editingWeightEntry?.id,
                    request = TrackerEntryRequest(
                        type = "WEIGHT",
                        recordedAt = editingWeightEntry?.recordedAt ?: recordedAt(selectedDate),
                        amount = amount,
                        detail = bodyFat?.toString(),
                        notes = null,
                    ),
                    onSaved = {
                        showWeightSheet = false
                        editingWeightEntry = null
                        healthProfileViewModel.refresh()
                    },
                )
            },
            onDismiss = {
                showWeightSheet = false
                editingWeightEntry = null
            },
        )
    }

    // Goal edit bottom sheet
    if (showGoalSheet && profile != null) {
        GoalEditBottomSheet(
            currentTarget = profile.targetWeightKg,
            currentDuration = profile.goalDurationWeeks,
            onSave = { target, duration ->
                healthProfileViewModel.updateGoal(target, duration) {
                    showGoalSheet = false
                }
            },
            onClear = {
                healthProfileViewModel.updateGoal(null, null) {
                    showGoalSheet = false
                }
            },
            onDismiss = { showGoalSheet = false },
        )
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.delete_entry)) },
            text = { Text(stringResource(R.string.delete_confirmation)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(id); deleteId = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun TrackerDayRow(
    item: TrackerEntryResponse,
    editable: Boolean,
    deletable: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(typeLabel(item.type), style = MaterialTheme.typography.titleMedium)
            Text("${formatAmount(item.amount)} ${item.unit}${item.detail?.let { " • $it" } ?: ""}")
            Text(formatTime(item.recordedAt), style = MaterialTheme.typography.bodySmall)
        }
        if (editable) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.edit)) }
            if (deletable) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        }
    }
}

@Composable
fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightInputBottomSheet(
    date: LocalDate,
    existingWeight: Double?,
    existingBodyFat: Double?,
    existingId: Long?,
    onSave: (Double, Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialValue = existingWeight?.let { formatAmount(it) } ?: ""
    var weightText by rememberSaveable { mutableStateOf(initialValue) }
    var replaceOnNextInput by rememberSaveable { mutableStateOf(true) }
    var bodyFatText by rememberSaveable { mutableStateOf(existingBodyFat?.let { formatAmount(it) } ?: "") }
    val weight = weightText.toDoubleOrNull()
    val bodyFat = bodyFatText.toDoubleOrNull()
    val isValid = weight != null && weight in 20.0..500.0

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
            Text(
                "${date.monthValue}/${date.dayOfMonth}  Weight",
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = onDismiss) { Text("Close") }
        }
        HorizontalDivider()

        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Display current weight input
            Text(
                weightText.ifBlank { "0" },
                color = Color(0xFF00B978),
                style = MaterialTheme.typography.displayMedium,
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(0.34f),
                color = Color(0xFF00B978),
            )
            Text(
                "kg",
                color = Color(0xFF00B978),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            // Body fat % input
            OutlinedTextField(
                value = bodyFatText,
                onValueChange = { bodyFatText = it },
                label = { Text("Body Fat % (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
            if (bodyFat != null && (bodyFat < 2.0 || bodyFat > 70.0)) {
                Text(
                    "Body fat % should be between 2–70%.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            // Validation messages
            if (weight != null && weight > 500) {
                Text(
                    "Weight exceeds the 500 kg limit.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (weight != null && weight < 20) {
                Text(
                    "Weight must be at least 20 kg.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            WeightKeypad(
                value = weightText,
                canSave = isValid,
                replaceOnNextInput = replaceOnNextInput,
                onValueChange = { weightText = it },
                onInputStarted = { replaceOnNextInput = false },
                onSave = { weight?.let { onSave(it, bodyFat) } },
            )
        }
    }
}

@Composable
private fun WeightKeypad(
    value: String,
    canSave: Boolean,
    replaceOnNextInput: Boolean,
    onValueChange: (String) -> Unit,
    onInputStarted: () -> Unit,
    onSave: () -> Unit,
) {
    fun append(character: String) {
        if (character == "." && value.contains('.')) return
        if (!replaceOnNextInput && value.length >= 7) return
        val next = when {
            replaceOnNextInput && character == "." -> "0."
            replaceOnNextInput -> character
            value == "0" && character != "." -> character
            else -> value + character
        }
        onInputStarted()
        onValueChange(next)
    }

    fun adjust(delta: Double) {
        val next = ((value.toDoubleOrNull() ?: 0.0) + delta).coerceAtLeast(0.0)
        onInputStarted()
        onValueChange(formatAmount(next))
    }

    val rows = listOf(
        listOf("1", "2", "3", "⌫"),
        listOf("4", "5", "6", "+"),
        listOf("7", "8", "9", "−"),
        listOf("Reset", "0", ".", "Save"),
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
                                    "+" -> adjust(1.0)
                                    "−" -> adjust(-1.0)
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

@Composable
private fun WeightTrendsScreen(
    viewModel: TrackerViewModel,
    profile: ProfileResponse?,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val currentMonth = YearMonth.now()

    LaunchedEffect(Unit) {
        viewModel.loadMonth("WEIGHT", currentMonth)
    }

    val entries = state.entries.filter { it.type == "WEIGHT" }
    val weightData = entries
        .map { entryDate(it) to it.amount }
        .groupBy { it.first }
        .mapValues { (_, pairs) -> pairs.maxByOrNull { it.second }?.second ?: 0.0 }
        .filter { it.value > 0.0 }
        .toList()
        .sortedBy { it.first }

    val bmiData = if (profile != null) {
        weightData.map { (date, weight) ->
            val bmiValue = weight / ((profile.heightCm / 100.0) * (profile.heightCm / 100.0))
            date to bmiValue
        }
    } else emptyList()

    // Build map of date → stored body fat % from entry detail field
    val storedBodyFatMap = entries
        .mapNotNull {
            val date = entryDate(it)
            val bf = it.detail?.toDoubleOrNull()
            if (bf != null && bf in 2.0..70.0) date to bf else null
        }
        .groupBy { it.first }
        .mapValues { (_, pairs) -> pairs.maxByOrNull { it.second }?.second ?: 0.0 }

    val bodyFatData = if (profile != null) {
        val age = runCatching {
            Period.between(LocalDate.parse(profile.dateOfBirth), LocalDate.now()).years.coerceAtLeast(0)
        }.getOrDefault(0)
        val sexTerm = if (profile.sex == "MALE") 16.2 else 5.4
        weightData.map { (date, weight) ->
            val stored = storedBodyFatMap[date]
            val bf = if (stored != null) {
                stored
            } else {
                val bmiValue = weight / ((profile.heightCm / 100.0) * (profile.heightCm / 100.0))
                1.20 * bmiValue + 0.23 * age - sexTerm
            }
            date to bf
        }
    } else emptyList()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Weight Trends", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onBack) { Text("Back") }
            }
        }

        item {
            Text("Weight (kg)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            TrendsLineChart(weightData, Color(0xFF4A90D9))
        }

        if (bmiData.isNotEmpty()) {
            item {
                Text("BMI", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                TrendsLineChart(bmiData, Color(0xFF00B978))
            }
        }

        if (bodyFatData.isNotEmpty()) {
            item {
                Text("Body Fat (%)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                TrendsLineChart(bodyFatData, Color(0xFFE29A33))
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🤖 AI Analysis", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "AI-powered trend analysis will be available soon. This feature will analyze your weight, BMI, and body fat trends to provide personalized insights and recommendations.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditBottomSheet(
    currentTarget: Double?,
    currentDuration: Int?,
    onSave: (Double, Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var targetText by rememberSaveable { mutableStateOf(currentTarget?.let { formatAmount(it) } ?: "") }
    var durationText by rememberSaveable { mutableStateOf(currentDuration?.toString() ?: "12") }
    val target = targetText.toDoubleOrNull()
    val duration = durationText.toIntOrNull()
    val isValid = target != null && target in 20.0..500.0 && duration != null && duration in 1..104

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
            Text("Weight Goal", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onDismiss) { Text("Close") }
        }
        HorizontalDivider()

        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it },
                label = { Text("Target Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = durationText,
                onValueChange = { durationText = it },
                label = { Text("Goal Duration (weeks)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            if (target != null && (target < 20.0 || target > 500.0)) {
                Text(
                    "Target weight must be between 20–500 kg.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (duration != null && (duration < 1 || duration > 104)) {
                Text(
                    "Duration must be 1–104 weeks.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Button(
                onClick = {
                    if (target != null && duration != null) onSave(target, duration)
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B978)),
            ) {
                Text("Save Goal")
            }
            if (currentTarget != null) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear Goal", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun TrackerFormScreen(
    type: String,
    id: Long?,
    selectedDate: LocalDate,
    viewModel: TrackerViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val definition = state.types.firstOrNull { it.type == type }
    val existing = id?.let { target -> state.entries.firstOrNull { it.id == target } }
    var initialized by rememberSaveable(id) { mutableStateOf(false) }
    var amount by rememberSaveable(id) { mutableStateOf("") }
    var detail by rememberSaveable(id) { mutableStateOf("") }
    var notes by rememberSaveable(id) { mutableStateOf("") }
    var localError by rememberSaveable(id) { mutableStateOf<String?>(null) }

    LaunchedEffect(existing) {
        if (existing != null && !initialized) {
            amount = formatAmount(existing.amount)
            detail = existing.detail.orEmpty()
            notes = existing.notes.orEmpty()
            initialized = true
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text(
            stringResource(if (id == null) R.string.add_tracker_title else R.string.edit_tracker_title, typeLabel(type)),
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("${definition?.amountLabel ?: stringResource(R.string.amount)} (${definition?.unit.orEmpty()})") },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (definition?.integerOnly == true) KeyboardType.Number else KeyboardType.Decimal,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        if (definition?.detailLabel != null) {
            OutlinedTextField(
                value = detail,
                onValueChange = { detail = it },
                label = { Text(definition.detailLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(stringResource(R.string.notes_optional)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            minLines = 3,
        )
        (localError ?: state.error)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        Button(
            onClick = {
                val numeric = amount.toDoubleOrNull()
                if (numeric == null || (definition?.detailRequired == true && detail.isBlank())) {
                    localError = "Enter a valid amount and required detail."
                } else {
                    localError = null
                    viewModel.save(
                        id = id,
                        request = TrackerEntryRequest(
                            type = type,
                            recordedAt = existing?.recordedAt ?: recordedAt(selectedDate),
                            amount = numeric,
                            detail = detail.ifBlank { null },
                            notes = notes.ifBlank { null },
                        ),
                        onSaved = onDone,
                    )
                }
            },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            if (state.saving) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text(stringResource(R.string.save))
        }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.cancel))
        }
    }
}

fun recordedAt(date: LocalDate): String {
    val zone = ZoneId.systemDefault()
    return if (date == LocalDate.now(zone)) Instant.now().toString()
    else date.atTime(12, 0).atZone(zone).toInstant().toString()
}

@Composable
fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.loading), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
fun ErrorBanner(error: String?, dismiss: () -> Unit) {
    error?.let {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = dismiss) { Text(stringResource(R.string.dismiss)) }
            }
        }
    }
}

private fun typeLabel(type: String): String = type.lowercase().replaceFirstChar(Char::uppercase)
fun formatAmount(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
fun formatTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault(value)

fun entryDate(item: TrackerEntryResponse): LocalDate = runCatching {
    Instant.parse(item.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate()
}.getOrDefault(LocalDate.MIN)
