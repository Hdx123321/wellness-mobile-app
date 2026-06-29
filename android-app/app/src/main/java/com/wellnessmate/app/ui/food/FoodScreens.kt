package com.wellnessmate.app.ui.food

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.wellnessmate.app.data.CatalogFoodItemRequest
import com.wellnessmate.app.data.FoodCatalogItemResponse
import com.wellnessmate.app.data.FoodEntryResponse
import com.wellnessmate.app.data.FoodNutrients
import com.wellnessmate.app.ui.FoodViewModel
import com.wellnessmate.app.ui.HealthProfileViewModel
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FoodTrackerScreen(
    viewModel: FoodViewModel,
    selectedDate: LocalDate,
    healthProfileViewModel: HealthProfileViewModel,
    onTakePhoto: () -> Unit,
    onBack: () -> Unit,
    onTrackerChanged: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedEntries = state.entries.filter { foodDate(it) == selectedDate }
    val selectedGrams = remember { mutableStateMapOf<Long, String>() }
    val selectedCatalog = remember { mutableStateMapOf<Long, FoodCatalogItemResponse>() }
    val selectedFoods = selectedCatalog.values.toList()
    val preview = previewNutrients(selectedFoods, selectedGrams)

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Food tracker", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = onBack) { Text("Back") }
            }
            if (state.loading) {
                CircularProgressIndicator(modifier = Modifier.padding(24.dp))
            }
            state.error?.let {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearError) { Text("Dismiss") }
                    }
                }
            }
            Text(selectedDate.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            FoodBudgetCard(totalEntries(selectedEntries), healthProfileViewModel)
        }

        items(selectedEntries, key = { "entry-${it.id}" }) { entry ->
            FoodEntryCard(entry, editable = selectedDate == LocalDate.now()) { deleteId = entry.id }
        }

        item {
            if (selectedDate == LocalDate.now()) state.analysis?.let { analysis ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Photo estimate", style = MaterialTheme.typography.titleLarge)
                        Text(analysis.summary, modifier = Modifier.padding(vertical = 6.dp))
                        analysis.items.forEach { item ->
                            Text("${item.name}: ${format(item.estimatedGrams)} g, ${format(item.calories)} kcal")
                            Text(
                                "Protein ${format(item.proteinGrams)} g · Carbs ${format(item.carbohydrateGrams)} g · " +
                                    "Fat ${format(item.fatGrams)} g · Fiber ${format(item.fiberGrams)} g · " +
                                    "Confidence ${format(item.confidence * 100)}%",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text(analysis.disclaimer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = viewModel::discardAnalysis) { Text("Discard") }
                            Button(
                                onClick = { viewModel.confirmAnalysis(onTrackerChanged) },
                                enabled = !state.saving,
                            ) { Text("Confirm and save") }
                        }
                    }
                }
            }
            if (selectedDate == LocalDate.now()) Button(
                onClick = onTakePhoto,
                enabled = !state.analyzing,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                if (state.analyzing) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Photograph meal for AI estimate")
            }
            if (selectedDate == LocalDate.now()) Text("Choose foods", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
            if (selectedDate == LocalDate.now()) Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search food") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { viewModel.search(query) }, modifier = Modifier.padding(start = 8.dp)) {
                    Text("Search")
                }
            }
            if (selectedDate == LocalDate.now() && selectedFoods.isNotEmpty()) {
                Text("Meal preview", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                NutrientSummary(preview)
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Meal notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        val requests = selectedGrams.mapNotNull { (id, grams) ->
                            grams.toDoubleOrNull()?.takeIf { it in 1.0..5000.0 }?.let {
                                CatalogFoodItemRequest(id, it)
                            }
                        }
                        if (requests.size != selectedGrams.size) {
                            localError = "Enter grams from 1 to 5000 for every selected food."
                        } else {
                            localError = null
                            viewModel.saveCatalog(requests, notes.ifBlank { null }) {
                                selectedGrams.clear()
                                selectedCatalog.clear()
                                notes = ""
                                onTrackerChanged()
                            }
                        }
                    },
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                ) { Text("Save meal") }
            }
        }

        if (selectedDate == LocalDate.now()) {
            items(state.catalog, key = { "catalog-${it.id}" }) { food ->
                CatalogFoodCard(
                    food = food,
                    grams = selectedGrams[food.id],
                    onAdd = {
                        selectedCatalog[food.id] = food
                        selectedGrams[food.id] = "100"
                    },
                    onGrams = { selectedGrams[food.id] = it },
                    onRemove = {
                        selectedGrams.remove(food.id)
                        selectedCatalog.remove(food.id)
                    },
                )
            }
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete meal?") },
            text = { Text("The meal and calculated nutrients will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(id, onTrackerChanged)
                    deleteId = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun FoodCameraScreen(viewModel: FoodViewModel, onComplete: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Photograph your meal", style = MaterialTheme.typography.headlineMedium)
        Text("The photo is sent to the configured server AI only when you tap Analyze.", modifier = Modifier.padding(8.dp))
        if (!granted) {
            Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        } else {
            CameraPreview(
                busy = state.analyzing,
                onPhoto = { viewModel.analyze(it, onComplete) },
                onCancel = onCancel,
            )
        }
    }
}

@Composable
private fun CameraPreview(busy: Boolean, onPhoto: (ByteArray) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            executor.shutdown()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        runCatching {
                            val provider = providerFuture.get()
                            cameraProvider = provider
                            val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                            val imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                            capture = imageCapture
                        }.onFailure { cameraError = "Unable to start camera." }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxWidth().height(500.dp),
        )
        cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val target = capture ?: return@Button
                val file = File.createTempFile("food-", ".jpg", context.cacheDir)
                val options = ImageCapture.OutputFileOptions.Builder(file).build()
                target.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                        val bytes = file.readBytes()
                        file.delete()
                        Handler(Looper.getMainLooper()).post { onPhoto(bytes) }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        Handler(Looper.getMainLooper()).post { cameraError = "Photo capture failed." }
                    }
                })
            },
            enabled = capture != null && !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Capture and analyze")
        }
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun CatalogFoodCard(
    food: FoodCatalogItemResponse,
    grams: String?,
    onAdd: () -> Unit,
    onGrams: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(food.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Per 100 g: ${format(food.caloriesPer100g)} kcal · P ${format(food.proteinPer100g)} · " +
                            "C ${format(food.carbohydratePer100g)} · F ${format(food.fatPer100g)} · Fiber ${format(food.fiberPer100g)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (grams == null) TextButton(onClick = onAdd) { Text("Add") }
                else TextButton(onClick = onRemove) { Text("Remove") }
            }
            grams?.let {
                OutlinedTextField(
                    value = it,
                    onValueChange = onGrams,
                    label = { Text("Grams") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun FoodEntryCard(entry: FoodEntryResponse, editable: Boolean, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.items.joinToString { it.name }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (editable) TextButton(onClick = onDelete) { Text("Delete") }
            }
            NutrientSummary(entry.totals)
            if (entry.source == "AI") Text("AI estimate · confirmed by user", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NutrientSummary(nutrients: FoodNutrients) {
    Text("${format(nutrients.calories)} kcal")
    Text(
        "Protein ${format(nutrients.proteinGrams)} g · Carbs ${format(nutrients.carbohydrateGrams)} g · " +
            "Fat ${format(nutrients.fatGrams)} g · Fiber ${format(nutrients.fiberGrams)} g",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun FoodBudgetCard(nutrients: FoodNutrients, healthViewModel: HealthProfileViewModel) {
    val health by healthViewModel.state.collectAsState()
    val activityFactor = when (health.profile?.activityLevel) {
        "HIGH" -> 1.7
        "MODERATE" -> 1.5
        else -> 1.2
    }
    val budget = ((health.metrics?.basalMetabolismKcal ?: 1667) * activityFactor).toInt()
    val intake = nutrients.calories.toInt()
    val exerciseBurn = 0
    val remaining = (budget - intake + exerciseBurn).coerceAtLeast(0)
    val progress = (intake.toFloat() / budget.coerceAtLeast(1)).coerceIn(0f, 1f)
    val carbGoal = budget * 0.50 / 4.0
    val proteinGoal = budget * 0.20 / 4.0
    val fatGoal = budget * 0.30 / 9.0

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Calorie intake", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Food intake")
                    Text(intake.toString(), style = MaterialTheme.typography.headlineMedium)
                }
                Box(modifier = Modifier.size(170.dp), contentAlignment = Alignment.Center) {
                    val ringColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 15.dp.toPx()
                        val inset = stroke / 2
                        drawArc(trackColor, -90f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                        drawArc(ringColor, -90f, 360f * progress, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                        val angle = Math.toRadians((-90 + 360 * progress).toDouble())
                        val radius = (size.width - stroke) / 2
                        drawCircle(
                            ringColor,
                            radius = 7.dp.toPx(),
                            center = Offset(size.width / 2 + radius * cos(angle).toFloat(), size.height / 2 + radius * sin(angle).toFloat()),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Remaining")
                        Text(remaining.toString(), style = MaterialTheme.typography.headlineLarge)
                        Text("Estimated budget $budget", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Exercise burn")
                    Text(exerciseBurn.toString(), style = MaterialTheme.typography.headlineMedium)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MacroProgress("Carbs", nutrients.carbohydrateGrams, carbGoal, Modifier.weight(1f))
                MacroProgress("Protein", nutrients.proteinGrams, proteinGoal, Modifier.weight(1f))
                MacroProgress("Fat", nutrients.fatGrams, fatGoal, Modifier.weight(1f))
            }
            Text("Budget and macro targets are estimates based on profile and activity level.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
private fun MacroProgress(label: String, amount: Double, goal: Double, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(
            progress = { (amount / goal.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        Text("${format(amount)} / ${format(goal)} g", style = MaterialTheme.typography.bodySmall)
    }
}

private fun totalEntries(entries: List<FoodEntryResponse>) = FoodNutrients(
    calories = entries.sumOf { it.totals.calories },
    proteinGrams = entries.sumOf { it.totals.proteinGrams },
    carbohydrateGrams = entries.sumOf { it.totals.carbohydrateGrams },
    fatGrams = entries.sumOf { it.totals.fatGrams },
    fiberGrams = entries.sumOf { it.totals.fiberGrams },
)

private fun previewNutrients(
    foods: List<FoodCatalogItemResponse>,
    grams: Map<Long, String>,
) = FoodNutrients(
    calories = foods.sumOf { it.caloriesPer100g * (grams[it.id]?.toDoubleOrNull() ?: 0.0) / 100.0 },
    proteinGrams = foods.sumOf { it.proteinPer100g * (grams[it.id]?.toDoubleOrNull() ?: 0.0) / 100.0 },
    carbohydrateGrams = foods.sumOf { it.carbohydratePer100g * (grams[it.id]?.toDoubleOrNull() ?: 0.0) / 100.0 },
    fatGrams = foods.sumOf { it.fatPer100g * (grams[it.id]?.toDoubleOrNull() ?: 0.0) / 100.0 },
    fiberGrams = foods.sumOf { it.fiberPer100g * (grams[it.id]?.toDoubleOrNull() ?: 0.0) / 100.0 },
)

private fun format(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

private fun foodDate(entry: FoodEntryResponse): LocalDate = runCatching {
    Instant.parse(entry.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate()
}.getOrDefault(LocalDate.MIN)
