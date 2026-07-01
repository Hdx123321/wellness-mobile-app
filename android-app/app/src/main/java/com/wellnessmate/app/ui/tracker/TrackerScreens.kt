package com.wellnessmate.app.ui.tracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private const val HOME = "home"
private const val COACH = "coach"
private const val ADVISOR = "advisor"
private const val USER_MANAGEMENT = "user-management"
private const val REMINDER = "reminder"
private const val HEALTH_PROFILE = "health-profile"
private const val HEIGHT_PICKER = "height-picker"
private const val TRACKER = "tracker/{type}"
private const val FORM = "form/{type}/{id}"
private const val FOOD = "food"
private const val FOOD_SELECT = "food-select/{date}/{meal}"
private const val FOOD_CAMERA = "food-camera/{date}/{meal}"
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
            if (route != FOOD_SELECT && route != FOOD_CAMERA) TopAppBar(
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
                    healthProfileViewModel = healthProfileViewModel,
                    onHealth = { navController.navigate(HEALTH_PROFILE) },
                    onTracker = { type ->
                        navController.navigate(if (type == "FOOD") FOOD else "tracker/$type")
                    },
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
                TrackerDetailScreen(
                    type = trackerType,
                    viewModel = viewModel,
                    selectedDate = selectedDate,
                    onEdit = { navController.navigate("form/${it.type}/${it.id}") },
                    onAdd = { navController.navigate("form/$it/-1") },
                    onBack = {
                        if (trackerType == "WEIGHT") healthProfileViewModel.refresh()
                        navController.popBackStack()
                    },
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
                route = FORM,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.LongType },
                ),
            ) { entry ->
                TrackerFormScreen(
                    type = entry.arguments?.getString("type") ?: "WATER",
                    id = entry.arguments?.getLong("id")?.takeIf { it >= 0 },
                    selectedDate = selectedDate,
                    viewModel = viewModel,
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
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
    healthProfileViewModel: HealthProfileViewModel,
    onHealth: () -> Unit,
    onTracker: (String) -> Unit,
    selectedDate: LocalDate,
) {
    val state by viewModel.state.collectAsState()
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
private fun TrackerDetailScreen(
    type: String,
    viewModel: TrackerViewModel,
    selectedDate: LocalDate,
    onEdit: (TrackerEntryResponse) -> Unit,
    onAdd: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
            SevenDayBarChart(chart, definition?.unit.orEmpty())
            Text(selectedDate.toString(), style = MaterialTheme.typography.titleLarge)
        }
        if (state.loading) item { LoadingState() }
        else if (selectedEntries.isEmpty()) item { Text("No data for this day.", modifier = Modifier.padding(vertical = 16.dp)) }
        else items(selectedEntries, key = { it.id }) { item ->
            TrackerDayRow(
                item,
                editable = true,
                onEdit = { onEdit(item) },
                onDelete = { deleteId = item.id },
            )
            HorizontalDivider()
        }
        item {
            Button(
                onClick = {
                    val weight = selectedEntries.firstOrNull().takeIf { type == "WEIGHT" }
                    if (weight != null) onEdit(weight) else onAdd(type)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                Text(
                    if (type == "WEIGHT" && selectedEntries.isNotEmpty()) "Update this day's weight"
                    else "Add data for this day",
                )
            }
        }
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
private fun TrackerDayRow(item: TrackerEntryResponse, editable: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
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
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
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

private fun recordedAt(date: LocalDate): String {
    val zone = ZoneId.systemDefault()
    return if (date == LocalDate.now(zone)) Instant.now().toString()
    else date.atTime(12, 0).atZone(zone).toInstant().toString()
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.loading), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ErrorBanner(error: String?, dismiss: () -> Unit) {
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
private fun formatAmount(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
private fun formatTime(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrDefault(value)

private fun entryDate(item: TrackerEntryResponse): LocalDate = runCatching {
    Instant.parse(item.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate()
}.getOrDefault(LocalDate.MIN)
