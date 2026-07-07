package com.alpinefitness.app.ui.plan

import android.net.Uri
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.alpinefitness.app.data.SessionUser
import com.alpinefitness.app.data.TrainingPlanRequest
import com.alpinefitness.app.data.TrainingPlanResponse
import com.alpinefitness.app.data.WorkoutBlockRequest
import com.alpinefitness.app.data.WorkoutBlockResponse
import com.alpinefitness.app.ui.TrainingPlanViewModel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.alpinefitness.app.ui.components.WellnessIconButton

// ── Block editor state holder ──
class BlockEditState(
    var title: String = "",
    var content: String = "",
    var imageUrl: String = "",
    var videoUrl: String = "",
) {
    fun toRequest() = WorkoutBlockRequest(
        title, content.ifBlank { null }, imageUrl.ifBlank { null }, videoUrl.ifBlank { null })
    companion object {
        fun from(response: WorkoutBlockResponse) = BlockEditState(
            response.title, response.content ?: "", response.imageUrl ?: "", response.videoUrl ?: "")
        fun empty() = BlockEditState()
    }
}

@Composable
fun TrainingPlanScreen(user: SessionUser, viewModel: TrainingPlanViewModel, onContactCoach: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val selected = state.selected
    // Intercept system back to return to plan list instead of popping the navigation route
    BackHandler(enabled = selected != null || creating || editing) {
        viewModel.select(null)
        creating = false
        editing = false
    }
    when {
        selected != null && !editing -> PlanDetail(
            plan = selected, user = user, viewModel = viewModel,
            onEdit = { editing = true }, onContactCoach = onContactCoach,
            onBack = { viewModel.select(null) })
        creating || editing -> PlanEditor(
            viewModel = viewModel,
            existing = if (editing) selected else null,
            onDone = { creating = false; editing = false },
            onBack = { creating = false; editing = false })
        else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Training plans", style = MaterialTheme.typography.headlineMedium)
                        Text("Plans published by WellnessMate coaches")
                    }
                    if (user.role == "COACH") IconButton(onClick = { creating = true }) {
                        Icon(painterResource(com.alpinefitness.app.R.drawable.ic_plan), "Publish training plan", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.loading) CircularProgressIndicator(Modifier.padding(24.dp))
            }
            if (!state.loading && state.plans.isEmpty()) item { Text("No training plans have been published yet.", Modifier.padding(top = 24.dp)) }
            val planList = state.plans
            items(planList.size, key = { planList[it].id }) { index ->
                val plan = planList[index]
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
private fun PlanDetail(plan: TrainingPlanResponse, user: SessionUser, viewModel: TrainingPlanViewModel,
                       onEdit: () -> Unit, onContactCoach: () -> Unit, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val state by viewModel.state.collectAsState()
    val isCoach = user.role == "COACH" && plan.coachId == user.userId
    val isClient = user.role == "CLIENT"
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showSubscribers by rememberSaveable { mutableStateOf(false) }

    if (showDeleteDialog) AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = { Text("Delete plan") },
        text = { Text("Delete \"${plan.title}\"? This cannot be undone.") },
        confirmButton = { TextButton(onClick = { viewModel.delete(plan.id) { showDeleteDialog = false } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
    )
    if (showSubscribers) {
        val subs = state.subscribers
        AlertDialog(
            onDismissRequest = { showSubscribers = false },
            title = { Text("Subscribers (${subs.size})") },
            text = {
                if (subs.isEmpty()) Text("No subscribers yet.")
                else LazyColumn { items(subs.size) { i ->
                    val s = subs[i]
                    Text("${s.displayName ?: s.username} (@${s.username})", modifier = Modifier.padding(vertical = 4.dp))
                } }
            },
            confirmButton = { TextButton(onClick = { showSubscribers = false }) { Text("Close") } },
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(com.alpinefitness.app.R.drawable.ic_back), "Back", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) }
                Text(plan.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }
            Text("Coach ${plan.coachName} · ${plan.difficulty} · ${plan.durationWeeks} weeks")
            PlanSection("Goal", plan.goal)
            PlanSection("Overview", plan.summary)

            // Workout blocks with inline media
            val blks = plan.blocks
            if (!blks.isNullOrEmpty()) {
                Text("Workout Plan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                for (index in blks.indices) {
                    val block = blks[index]
                    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${index + 1}. ${block.title}", style = MaterialTheme.typography.titleSmall)
                            block.content?.let { Text(it, modifier = Modifier.padding(top = 4.dp)) }
                            // Inline image display
                            block.imageUrl?.let { url ->
                                val fullUrl = if (url.startsWith("/api")) "http://10.0.2.2:18080$url" else url
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(fullUrl)
                                        .build(),
                                    contentDescription = "Block image",
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.FillWidth,
                                )
                            }
                            // Inline video display
                            block.videoUrl?.let { url ->
                                val fullUrl = if (url.startsWith("/api")) "http://10.0.2.2:18080$url" else url
                                AndroidView(
                                    factory = { ctx ->
                                        VideoView(ctx).apply {
                                            setVideoPath(fullUrl)
                                            setOnPreparedListener { start() }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            }
                        }
                    }
                }
            } else if (!plan.weeklySchedule.isNullOrBlank()) {
                PlanSection("Weekly plan", plan.weeklySchedule)
            }

            plan.equipment?.let { PlanSection("Equipment", it) }
            plan.safetyNotes?.let { PlanSection("Safety notes", it) }
            plan.videoUrl?.let { url ->
                Button(onClick = { uriHandler.openUri(url) }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("▶ Watch training video") }
            }
            Text("${plan.checkInCount} total check-ins", modifier = Modifier.padding(vertical = 10.dp))
        }

        // Action buttons
        item {
            if (isCoach) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit plan") }
                    OutlinedButton(onClick = {
                        viewModel.loadSubscribers(plan.id)
                        showSubscribers = true
                    }, modifier = Modifier.weight(1f)) { Text("Subscribers") }
                }
                OutlinedButton(onClick = { showDeleteDialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete plan") }
            }
            if (isClient) {
                OutlinedButton(onClick = onContactCoach, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("💬 Message Coach ${plan.coachName}")
                }
                if (plan.subscribed) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.checkIn() },
                            enabled = !plan.checkedInToday, modifier = Modifier.weight(1f)) {
                            Text(if (plan.checkedInToday) "✓ Checked in today" else "✓ Check in")
                        }
                        OutlinedButton(onClick = { viewModel.unsubscribe() },
                            enabled = !state.saving, modifier = Modifier.weight(1f)) {
                            Text(if (state.saving) "Unsubscribing..." else "Unsubscribe")
                        }
                    }
                } else {
                    Button(onClick = { viewModel.subscribe() },
                        enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.saving) "Subscribing..." else "Subscribe to plan")
                    }
                }
            }
        }
    }
}

@Composable private fun PlanSection(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    Text(body, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun PlanEditor(viewModel: TrainingPlanViewModel, existing: TrainingPlanResponse?,
                       onDone: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var goal by rememberSaveable { mutableStateOf(existing?.goal ?: "") }
    var difficulty by rememberSaveable { mutableStateOf(existing?.difficulty ?: "Beginner") }
    var weeks by rememberSaveable { mutableStateOf((existing?.durationWeeks ?: 4).toString()) }
    var summary by rememberSaveable { mutableStateOf(existing?.summary ?: "") }
    var equipment by rememberSaveable { mutableStateOf(existing?.equipment ?: "") }
    var safety by rememberSaveable { mutableStateOf(existing?.safetyNotes ?: "") }

    // Dynamic block list
    val blocks = remember { mutableStateListOf<BlockEditState>() }
    if (blocks.isEmpty()) {
        val existingBlocks = existing?.blocks
        if (!existingBlocks.isNullOrEmpty()) {
            blocks.addAll(existingBlocks.map { BlockEditState.from(it) })
        } else {
            blocks.add(BlockEditState(title = "Workout 1", content = "Warm-up: 5 min\nMain: 3 sets of...\nCool-down: 5 min"))
        }
    }

    // Image picker launcher
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) uploadMedia(uri, "image/", context, viewModel) { url ->
            val idx = viewModel.state.value.uploadingBlockIndex ?: return@uploadMedia
            if (idx < blocks.size) blocks[idx].imageUrl = url
        }
    }

    // Video picker launcher
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) uploadMedia(uri, "video/", context, viewModel) { url ->
            val idx = viewModel.state.value.uploadingBlockIndex ?: return@uploadMedia
            if (idx < blocks.size) blocks[idx].videoUrl = url
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(painterResource(com.alpinefitness.app.R.drawable.ic_back), "Back", modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary) }
            Text(if (existing != null) "Edit plan" else "Publish training plan",
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        PlanField("Plan title", title) { title = it }
        PlanField("Goal", goal) { goal = it }
        PlanField("Difficulty", difficulty) { difficulty = it }
        OutlinedTextField(weeks, { weeks = it }, label = { Text("Duration (weeks)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        PlanField("Overview", summary, 3) { summary = it }

        // Workout blocks
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text("Workout blocks", style = MaterialTheme.typography.titleMedium)
        for (i in blocks.indices) {
            val b = blocks[i]
            val isUploading = state.uploadingBlockIndex == i
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Block ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { if (i > 0) { val mv = blocks.removeAt(i); blocks.add(i - 1, mv) } }, enabled = i > 0) { Text("↑") }
                        TextButton(onClick = { if (i < blocks.size - 1) { val mv = blocks.removeAt(i); blocks.add(i + 1, mv) } }, enabled = i < blocks.size - 1) { Text("↓") }
                        IconButton(onClick = { if (blocks.size > 1) blocks.removeAt(i) }) { Icon(painterResource(com.alpinefitness.app.R.drawable.ic_delete), "Remove block", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                    }
                    OutlinedTextField(b.title, { b.title = it }, label = { Text("Title (e.g. Dumbbell Curls 4×12)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(b.content, { b.content = it }, label = { Text("Content (optional)") },
                        minLines = 2, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))

                    // Image/video picker row
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        // Image picker button
                        OutlinedButton(onClick = {
                            viewModel.setUploading(i)
                            imagePicker.launch("image/*")
                        }, enabled = !isUploading) {
                            Text(if (b.imageUrl.isNotBlank()) "🖼 Change image" else "🖼 Add image")
                        }
                        // Video picker button
                        OutlinedButton(onClick = {
                            viewModel.setUploading(i)
                            videoPicker.launch("video/*")
                        }, enabled = !isUploading) {
                            Text(if (b.videoUrl.isNotBlank()) "🎬 Change video" else "🎬 Add video")
                        }
                        if (isUploading) CircularProgressIndicator(Modifier.size(20.dp))
                    }

                    // Preview uploaded media
                    if (b.imageUrl.isNotBlank()) {
                        val fullUrl = if (b.imageUrl.startsWith("/api")) "http://10.0.2.2:18080${b.imageUrl}" else b.imageUrl
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(fullUrl).build(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.FillWidth,
                        )
                        TextButton(onClick = { b.imageUrl = "" }) { Text("Remove image") }
                    }
                    if (b.videoUrl.isNotBlank()) {
                        Text("🎬 Video attached", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                        TextButton(onClick = { b.videoUrl = "" }) { Text("Remove video") }
                    }
                }
            }
        }
        OutlinedButton(onClick = { blocks.add(BlockEditState.empty()) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Icon(painterResource(com.alpinefitness.app.R.drawable.ic_plan), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(4.dp))
            Text("Add block")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        PlanField("Equipment (optional)", equipment, 2) { equipment = it }
        PlanField("Safety notes (optional)", safety, 3) { safety = it }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val valid = title.isNotBlank() && goal.isNotBlank() && summary.isNotBlank() &&
            blocks.all { it.title.isNotBlank() }
        Button(onClick = {
            val req = TrainingPlanRequest(title, goal, difficulty, weeks.toIntOrNull() ?: 0,
                summary, null, equipment.ifBlank { null }, safety.ifBlank { null },
                null, blocks.map { it.toRequest() })
            if (existing != null) viewModel.update(existing.id, req, onDone)
            else viewModel.create(req, onDone)
        }, enabled = valid && !state.saving, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            Text(if (existing != null) "Save changes" else "Publish")
        }
    }
}

@Composable private fun PlanField(label: String, value: String, lines: Int = 1, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, minLines = lines,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
}

// Upload helper
private fun uploadMedia(uri: Uri, prefix: String, context: android.content.Context,
                        viewModel: TrainingPlanViewModel, onUrl: (String) -> Unit) {
    val idx = viewModel.state.value.uploadingBlockIndex ?: return
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri) ?: "${prefix}*"
    val filename = "upload_${System.currentTimeMillis()}"
    try {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
        viewModel.uploadBlockFile(idx, bytes, mimeType, filename, onUrl)
    } catch (_: Exception) {}
}
