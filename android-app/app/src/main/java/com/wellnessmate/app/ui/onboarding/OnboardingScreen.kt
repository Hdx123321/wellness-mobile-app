package com.wellnessmate.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wellnessmate.app.R
import com.wellnessmate.app.data.OnboardingRequest
import com.wellnessmate.app.ui.OnboardingViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val sexOptions = listOf("FEMALE", "MALE", "INTERSEX", "PREFER_NOT_TO_SAY")
private val ethnicityOptions = listOf(
    "EAST_ASIAN", "SOUTH_ASIAN", "SOUTHEAST_ASIAN", "WHITE", "BLACK",
    "MIDDLE_EASTERN_NORTH_AFRICAN", "LATIN_AMERICAN", "MIXED", "OTHER", "PREFER_NOT_TO_SAY",
)
private val routineOptions = listOf("MOSTLY_SITTING", "MOSTLY_STANDING", "MIXED", "PHYSICALLY_DEMANDING")
private val activityOptions = listOf("LOW", "MODERATE", "HIGH")
private val exerciseOptions = listOf(
    "WALKING", "RUNNING", "CYCLING", "SWIMMING", "STRENGTH", "YOGA", "PILATES",
    "TEAM_SPORTS", "HOME_WORKOUT", "GYM_WORKOUT", "OTHER",
)
private val needOptions = listOf(
    "FIND_COACH", "TRACK_EXERCISE", "HEALTHY_MEAL_PLANNING", "WEIGHT_MANAGEMENT",
    "BUILD_FITNESS", "IMPROVE_SLEEP", "STRESS_MANAGEMENT",
)

/** First-login profile form using mostly choice controls. @author TODO(team member) */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onCompleted: () -> Unit,
    onLogout: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var dateOfBirth by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf("PREFER_NOT_TO_SAY") }
    var ethnicity by rememberSaveable { mutableStateOf("PREFER_NOT_TO_SAY") }
    var targetWeight by rememberSaveable { mutableStateOf("") }
    var durationWeeks by rememberSaveable { mutableStateOf("") }
    var routine by rememberSaveable { mutableStateOf("MOSTLY_SITTING") }
    var activity by rememberSaveable { mutableStateOf("MODERATE") }
    var exercisesCsv by rememberSaveable { mutableStateOf("") }
    var needsCsv by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var showBirthDatePicker by rememberSaveable { mutableStateOf(false) }

    if (state.loading) {
        CenteredProgress(stringResource(R.string.loading_questions))
        return
    }
    if (state.questions.isEmpty()) {
        ErrorRetry(state.error ?: stringResource(R.string.questions_unavailable), viewModel::load, onLogout)
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Text(stringResource(R.string.tell_us_about_you), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.onboarding_intro), modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))

        Button(
            onClick = { showBirthDatePicker = true },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            val displayDate = dateOfBirth.takeIf(String::isNotBlank)?.let {
                LocalDate.parse(it).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            }
            Text(displayDate?.let { "Date of birth: $it" } ?: "Select date of birth")
        }
        TextInput(height, { height = it }, R.string.height_cm, KeyboardType.Decimal)
        TextInput(weight, { weight = it }, R.string.current_weight_kg, KeyboardType.Decimal)
        SingleChoice(stringResource(R.string.sex_at_birth), options(state.questions, "sex", sexOptions), sex) { sex = it }
        SingleChoice(stringResource(R.string.ethnicity_optional), options(state.questions, "ethnicity", ethnicityOptions), ethnicity) { ethnicity = it }
        TextInput(targetWeight, { targetWeight = it }, R.string.target_weight_optional, KeyboardType.Decimal)
        TextInput(durationWeeks, { durationWeeks = it }, R.string.goal_weeks_optional, KeyboardType.Number)
        SingleChoice(stringResource(R.string.daily_routine), options(state.questions, "dailyRoutine", routineOptions), routine) { routine = it }
        SingleChoice(stringResource(R.string.activity_level), options(state.questions, "activityLevel", activityOptions), activity) { activity = it }
        MultiChoice("${stringResource(R.string.exercise_preferences)} (optional)", options(state.questions, "exercisePreferences", exerciseOptions), exercisesCsv) { exercisesCsv = it }
        MultiChoice("${stringResource(R.string.core_needs)} (optional)", options(state.questions, "coreNeeds", needOptions), needsCsv) { needsCsv = it }

        (localError ?: state.error)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
        Button(
            onClick = {
                val request = buildRequest(
                    dateOfBirth, height, weight, sex, ethnicity, targetWeight, durationWeeks,
                    routine, activity, csvSet(exercisesCsv), csvSet(needsCsv),
                )
                if (request == null) localError = "Complete all required fields using valid values."
                else {
                    localError = null
                    viewModel.save(request, onCompleted)
                }
            },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        ) {
            if (state.saving) CircularProgressIndicator(strokeWidth = 2.dp)
            else Text(stringResource(R.string.complete_setup))
        }
        TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.logout))
        }
    }

    if (showBirthDatePicker) {
        val initialDate = dateOfBirth.takeIf(String::isNotBlank)?.let(LocalDate::parse)
            ?: LocalDate.now().minusYears(25)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() < LocalDate.now()
                override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showBirthDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        dateOfBirth = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showBirthDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showBirthDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun TextInput(value: String, onChange: (String) -> Unit, label: Int, keyboardType: KeyboardType) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

@Composable
private fun SingleChoice(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun MultiChoice(title: String, options: List<String>, csv: String, onChange: (String) -> Unit) {
    val selected = csvSet(csv)
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option in selected,
                onClick = {
                    val updated = if (option in selected) selected - option else selected + option
                    onChange(updated.sorted().joinToString(","))
                },
                label = { Text(label(option)) },
            )
        }
    }
}

@Composable
private fun CenteredProgress(text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(text, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ErrorRetry(message: String, retry: () -> Unit, logout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = retry, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.retry)) }
        TextButton(onClick = logout) { Text(stringResource(R.string.logout)) }
    }
}

private fun buildRequest(
    dob: String, height: String, weight: String, sex: String, ethnicity: String,
    target: String, weeks: String, routine: String, activity: String,
    exercises: Set<String>, needs: Set<String>,
): OnboardingRequest? = runCatching {
    LocalDate.parse(dob)
    val targetValue = target.ifBlank { null }?.toDouble()
    val weeksValue = weeks.ifBlank { null }?.toInt()
    require((targetValue == null) == (weeksValue == null))
    OnboardingRequest(
        dateOfBirth = dob,
        heightCm = height.toDouble(),
        currentWeightKg = weight.toDouble(),
        sex = sex,
        ethnicity = ethnicity,
        targetWeightKg = targetValue,
        goalDurationWeeks = weeksValue,
        dailyRoutine = routine,
        activityLevel = activity,
        exercisePreferences = exercises,
        coreNeeds = needs,
    )
}.getOrNull()

private fun csvSet(csv: String): Set<String> = csv.split(',').filter(String::isNotBlank).toSet()
private fun label(value: String): String = value.lowercase().split('_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
private fun options(questions: List<com.wellnessmate.app.data.OnboardingQuestion>, id: String, fallback: List<String>): List<String> =
    questions.firstOrNull { it.id == id }?.options?.takeIf { it.isNotEmpty() } ?: fallback
