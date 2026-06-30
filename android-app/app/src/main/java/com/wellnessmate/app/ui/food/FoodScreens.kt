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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import java.time.ZoneOffset
import java.util.concurrent.Executors
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FoodTrackerScreen(
    viewModel: FoodViewModel,
    selectedDate: LocalDate,
    healthProfileViewModel: HealthProfileViewModel,
    onTakePhoto: (LocalDate, String) -> Unit,
    onAddFood: (LocalDate, String) -> Unit,
    onBack: () -> Unit,
    onTrackerChanged: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var deleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedEntries = state.entries.filter { foodDate(it) == selectedDate }

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

        FoodMeal.entries.forEach { meal ->
            val mealEntries = selectedEntries.filter { it.mealType == meal.name }
            item(key = "meal-${meal.name}") {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(meal.label, style = MaterialTheme.typography.titleLarge)
                            Text("${format(totalEntries(mealEntries).calories)} kcal")
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Button(onClick = { onAddFood(selectedDate, meal.name) }) { Text("Add food") }
                            if (selectedDate == LocalDate.now()) {
                                TextButton(onClick = { onTakePhoto(selectedDate, meal.name) }) { Text("Take photo") }
                            }
                        }
                    }
                }
            }
            items(mealEntries, key = { "entry-${it.id}" }) { entry ->
                FoodEntryCard(entry, editable = true) { deleteId = entry.id }
            }
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

private enum class FoodMeal(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snacks"),
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FoodSelectionScreen(
    viewModel: FoodViewModel,
    initialDate: LocalDate,
    initialMealType: String,
    onBack: () -> Unit,
    onTrackerChanged: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var selectedDateText by rememberSaveable { mutableStateOf(initialDate.toString()) }
    var selectedMealName by rememberSaveable {
        mutableStateOf(initialMealType.takeIf { value -> FoodMeal.entries.any { it.name == value } } ?: "SNACK")
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var detailFoodId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showSelectedFoods by rememberSaveable { mutableStateOf(false) }
    val selectedDate = LocalDate.parse(selectedDateText)
    val selectedMeal = FoodMeal.valueOf(selectedMealName)
    val selectedGrams = remember { mutableStateMapOf<Long, String>() }
    val selectedCatalog = remember { mutableStateMapOf<Long, FoodCatalogItemResponse>() }
    val selectedFoods = selectedCatalog.values.toList()
    val selectedNutrients = previewNutrients(selectedFoods, selectedGrams)

    LaunchedEffect(selectedDateText) { viewModel.loadDate(selectedDate) }

    fun saveSelectedFoods() {
        val requests = selectedGrams.mapNotNull { (id, grams) ->
            grams.toDoubleOrNull()?.takeIf { it in 1.0..5000.0 }
                ?.let { CatalogFoodItemRequest(id, it) }
        }
        if (requests.isEmpty() || requests.size != selectedGrams.size) {
            localError = "Enter grams from 1 to 5000 for every selected food."
            return
        }
        localError = null
        viewModel.saveCatalog(selectedDate, selectedMeal.name, requests, null) {
            onTrackerChanged()
            onBack()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // 顶栏：返回 + 日期 + 餐次
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("${selectedMeal.label}", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = ::saveSelectedFoods) { Text("Done") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { showDatePicker = true }) {
                Text(
                    "📅 ${selectedDate.monthValue}/${selectedDate.dayOfMonth}",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        // 餐次选择
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            items(FoodMeal.entries) { meal ->
                if (meal == selectedMeal) Button(onClick = {}) { Text(meal.label) }
                else TextButton(onClick = { selectedMealName = meal.name }) { Text(meal.label) }
            }
        }

        // 搜索栏
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
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

        (localError ?: state.error)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 4.dp))
        }

        // 主体：分类侧边栏 + 食物列表
        Row(modifier = Modifier.weight(1f)) {
            // 左侧分类
            Column(modifier = Modifier.weight(0.35f).padding(end = 4.dp)) {
                Text(
                    "Categories",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                // "All" 选项
                TextButton(
                    onClick = { viewModel.selectCategory(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "All",
                        style = if (state.selectedCategoryId == null) MaterialTheme.typography.titleSmall
                        else MaterialTheme.typography.bodyMedium,
                    )
                }
                LazyColumn {
                    items(state.categories, key = { "cat-${it.id}" }) { cat ->
                        TextButton(
                            onClick = { viewModel.selectCategory(cat.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                cat.nameCn,
                                style = if (state.selectedCategoryId == cat.id) MaterialTheme.typography.titleSmall
                                else MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // 右侧食物列表
            LazyColumn(modifier = Modifier.weight(0.65f).padding(start = 4.dp)) {
                if (state.loading) {
                    item { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
                }
                items(state.catalog, key = { "catalog-${it.id}" }) { food ->
                    CompactFoodCard(
                        food = food,
                        selected = selectedCatalog.containsKey(food.id),
                        grams = selectedGrams[food.id],
                        onAdd = {
                            selectedCatalog[food.id] = food
                            selectedGrams[food.id] = "100"
                        },
                        onRemove = {
                            selectedGrams.remove(food.id)
                            selectedCatalog.remove(food.id)
                        },
                        onClick = { detailFoodId = food.id },
                    )
                }
            }
        }

        FoodSelectionBottomBar(
            mealLabel = selectedMeal.label,
            selectedCount = selectedFoods.size,
            calories = selectedNutrients.calories,
            saving = state.saving,
            onSummary = { if (selectedFoods.isNotEmpty()) showSelectedFoods = true },
            onSave = ::saveSelectedFoods,
        )
    }

    if (showDatePicker) {
        val picker = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        selectedDateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            .coerceAtMost(LocalDate.now()).toString()
                    }
                    showDatePicker = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }

    detailFoodId?.let { foodId ->
        FoodDetailBottomSheet(
            foodId = foodId,
            date = selectedDate,
            mealLabel = selectedMeal.label,
            viewModel = viewModel,
            onAdd = { grams ->
                state.catalog.firstOrNull { it.id == foodId }?.let { food ->
                    selectedCatalog[foodId] = food
                    selectedGrams[foodId] = if (grams % 1.0 == 0.0) {
                        grams.toLong().toString()
                    } else {
                        grams.toString()
                    }
                }
            },
            onDismiss = { detailFoodId = null },
        )
    }

    if (showSelectedFoods) {
        SelectedFoodsBottomSheet(
            mealLabel = selectedMeal.label,
            foods = selectedFoods,
            grams = selectedGrams,
            saving = state.saving,
            onRemove = { id ->
                selectedGrams.remove(id)
                selectedCatalog.remove(id)
                if (selectedCatalog.isEmpty()) showSelectedFoods = false
            },
            onSave = ::saveSelectedFoods,
            onDismiss = { showSelectedFoods = false },
        )
    }
}

@Composable
private fun FoodSelectionBottomBar(
    mealLabel: String,
    selectedCount: Int,
    calories: Double,
    saving: Boolean,
    onSummary: () -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onSummary, modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text("🍽  $mealLabel · $selectedCount selected", style = MaterialTheme.typography.titleMedium)
                    Text("${format(calories)} kcal · tap to review", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(
                onClick = onSave,
                enabled = selectedCount > 0 && !saving,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B978)),
            ) { Text(if (saving) "Saving…" else "Done") }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SelectedFoodsBottomSheet(
    mealLabel: String,
    foods: List<FoodCatalogItemResponse>,
    grams: Map<Long, String>,
    saving: Boolean,
    onRemove: (Long) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val nutrients = previewNutrients(foods, grams)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("${foods.size} selected foods", style = MaterialTheme.typography.titleLarge)
                    Text("${format(nutrients.calories)} kcal total · $mealLabel")
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(foods, key = { "selected-${it.id}" }) { food ->
                    val amount = grams[food.id].orEmpty()
                    val calories = food.caloriesPer100g * (amount.toDoubleOrNull() ?: 0.0) / 100.0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(food.name, style = MaterialTheme.typography.titleMedium)
                            Text("${format(calories)} kcal", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("${format(amount.toDoubleOrNull() ?: 0.0)} g")
                        TextButton(onClick = { onRemove(food.id) }) { Text("Delete") }
                    }
                    HorizontalDivider()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("🍽  $mealLabel", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(
                    onClick = onSave,
                    enabled = foods.isNotEmpty() && !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B978)),
                ) { Text(if (saving) "Saving…" else "Done") }
            }
        }
    }
}

@Composable
fun FoodCameraScreen(
    viewModel: FoodViewModel,
    date: LocalDate,
    mealType: String,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
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
                onPhoto = { viewModel.analyze(it, date, mealType, onComplete) },
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
private fun CompactFoodCard(
    food: FoodCatalogItemResponse,
    selected: Boolean,
    grams: String?,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(food.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${format(food.caloriesPer100g)} kcal/100g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!selected) {
                    TextButton(onClick = onAdd) { Text("+") }
                } else {
                    TextButton(onClick = onRemove) { Text("✕") }
                }
            }
            if (selected) Text("${grams.orEmpty()} g selected", style = MaterialTheme.typography.bodySmall)
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
