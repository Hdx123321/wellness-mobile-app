package com.wellnessmate.app.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.wellnessmate.app.data.SessionUser
import com.wellnessmate.app.data.TrainingPlanRequest
import com.wellnessmate.app.data.TrainingPlanResponse
import com.wellnessmate.app.ui.TrainingPlanViewModel
import com.wellnessmate.app.ui.components.WellnessIconButton

@Composable
fun TrainingPlanScreen(user: SessionUser, viewModel: TrainingPlanViewModel, onContactCoach: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var creating by rememberSaveable { mutableStateOf(false) }
    val selected = state.selected
    when {
        selected != null -> PlanDetail(selected, user.role == "CLIENT", viewModel::checkIn,
            onContactCoach, { viewModel.select(null) })
        creating -> PlanEditor(viewModel, onDone = { creating = false }, onBack = { creating = false })
        else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Training plans", style = MaterialTheme.typography.headlineMedium)
                        Text("Plans published by WellnessMate coaches")
                    }
                    if (user.role == "COACH") WellnessIconButton("+", "Publish training plan", onClick = { creating = true })
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.loading) CircularProgressIndicator(Modifier.padding(24.dp))
            }
            if (!state.loading && state.plans.isEmpty()) item { Text("No training plans have been published yet.", Modifier.padding(top = 24.dp)) }
            items(state.plans, key = { it.id }) { plan ->
                Card(Modifier.fillMaxWidth().padding(vertical = 7.dp).clickable { viewModel.select(plan) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(plan.title, style = MaterialTheme.typography.titleLarge)
                        Text("${plan.difficulty} · ${plan.durationWeeks} weeks · Coach ${plan.coachName}")
                        if (plan.videoUrl != null) Text("▶ Video included", color = MaterialTheme.colorScheme.primary)
                        Text(plan.goal, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        Text(plan.summary, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanDetail(plan: TrainingPlanResponse, client: Boolean, onCheckIn: () -> Unit,
                       onContact: () -> Unit, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back") }
                Text(plan.title, style = MaterialTheme.typography.headlineSmall)
            }
            Text("Coach ${plan.coachName} · ${plan.difficulty} · ${plan.durationWeeks} weeks")
            PlanSection("Goal", plan.goal)
            PlanSection("Overview", plan.summary)
            PlanSection("Weekly plan", plan.weeklySchedule)
            plan.equipment?.let { PlanSection("Equipment", it) }
            plan.safetyNotes?.let { PlanSection("Safety notes", it) }
            plan.videoUrl?.let { url ->
                Button(
                    onClick = { uriHandler.openUri(url) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) { Text("▶ Watch training video") }
            }
            Text("${plan.checkInCount} total check-ins", modifier = Modifier.padding(vertical = 10.dp))
            if (client) {
                Button(onClick = onCheckIn, enabled = !plan.checkedInToday, modifier = Modifier.fillMaxWidth()) {
                    Text(if (plan.checkedInToday) "✓ Checked in today" else "✓ Check in")
                }
                TextButton(onClick = onContact, modifier = Modifier.fillMaxWidth()) { Text("Contact coach") }
            }
        }
    }
}

@Composable private fun PlanSection(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    Text(body, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun PlanEditor(viewModel: TrainingPlanViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf("Beginner") }
    var weeks by rememberSaveable { mutableStateOf("4") }
    var summary by rememberSaveable { mutableStateOf("") }
    var schedule by rememberSaveable { mutableStateOf("Week 1:\nMon — \nWed — \nFri — ") }
    var equipment by rememberSaveable { mutableStateOf("") }
    var safety by rememberSaveable { mutableStateOf("") }
    var videoUrl by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Publish training plan", style = MaterialTheme.typography.headlineSmall)
        }
        PlanField("Plan title", title) { title = it }
        PlanField("Goal", goal) { goal = it }
        PlanField("Difficulty", difficulty) { difficulty = it }
        OutlinedTextField(weeks, { weeks = it }, label = { Text("Duration (weeks)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        PlanField("Overview", summary, 3) { summary = it }
        PlanField("Weekly schedule", schedule, 6) { schedule = it }
        PlanField("Equipment (optional)", equipment, 2) { equipment = it }
        PlanField("Safety notes (optional)", safety, 3) { safety = it }
        PlanField("Training video URL (HTTPS)", videoUrl, 1) { videoUrl = it }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            viewModel.create(TrainingPlanRequest(title, goal, difficulty, weeks.toIntOrNull() ?: 0,
                summary, schedule, equipment.ifBlank { null }, safety.ifBlank { null },
                videoUrl.ifBlank { null }), onDone)
        }, enabled = title.isNotBlank() && goal.isNotBlank() && summary.isNotBlank() && schedule.isNotBlank() && !state.saving,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text("Publish") }
    }
}

@Composable private fun PlanField(label: String, value: String, lines: Int = 1, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, minLines = lines,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
}
