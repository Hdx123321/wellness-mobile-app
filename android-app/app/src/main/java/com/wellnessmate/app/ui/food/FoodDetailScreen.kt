package com.wellnessmate.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.wellnessmate.app.data.ServingSizeResponse
import com.wellnessmate.app.ui.FoodViewModel
import java.time.LocalDate

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FoodDetailBottomSheet(
    foodId: Long,
    date: LocalDate,
    mealLabel: String,
    viewModel: FoodViewModel,
    onAdd: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detail = state.foodDetail?.takeIf { it.id == foodId }

    LaunchedEffect(foodId) { viewModel.loadFoodDetail(foodId) }

    fun close() {
        viewModel.clearDetail()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::close,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${date.monthValue}/${date.dayOfMonth}  $mealLabel",
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(onClick = ::close) { Text("Close") }
        }
        HorizontalDivider()

        when {
            state.detailLoading -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text("Loading food details…", modifier = Modifier.padding(top = 12.dp))
                }
            }
            detail == null -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error ?: "Unable to load food details.", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.loadFoodDetail(foodId) }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Retry")
                    }
                }
            }
            else -> {
                FoodDetailContent(
                    foodId = foodId,
                    detail = detail,
                    onAdd = { grams ->
                        onAdd(grams)
                        close()
                    },
                )
            }
        }
    }
}

@Composable
private fun FoodDetailContent(
    foodId: Long,
    detail: com.wellnessmate.app.data.FoodDetailResponse,
    onAdd: (Double) -> Unit,
) {
    val defaultSize = detail.servingSizes.firstOrNull { it.isDefault } ?: detail.servingSizes.firstOrNull()
    var quantityText by rememberSaveable(foodId) {
        mutableStateOf(defaultSize?.let(::defaultQuantity) ?: "100")
    }
    var replaceOnNextInput by rememberSaveable(foodId) { mutableStateOf(true) }
    var selectedSizeId by rememberSaveable(foodId) { mutableStateOf(defaultSize?.id) }
    val selectedSize = detail.servingSizes.firstOrNull { it.id == selectedSizeId }
    val quantity = quantityText.toDoubleOrNull()
    val directGramInput = selectedSize?.let(::isGramServing) == true
    val unitGrams = if (directGramInput) 1.0 else selectedSize?.grams ?: 1.0
    val grams = quantity?.times(unitGrams)
    val factor = (grams ?: 0.0) / 100.0
    val unitLabel = if (directGramInput) "克" else selectedSize?.let { it.labelCn.ifBlank { it.label } } ?: "克"

    LaunchedEffect(selectedSizeId) {
        quantityText = selectedSize?.let(::defaultQuantity) ?: "100"
        replaceOnNextInput = true
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(detail.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${format(detail.caloriesPer100g * factor)} kcal",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Text(
                    "Estimated edible portion ${format(grams ?: 0.0)} g · reference ${format(detail.caloriesPer100g)} kcal/100 g",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                NutrientRow("Carbohydrates", detail.carbohydratePer100g * factor)
                NutrientRow("Protein", detail.proteinPer100g * factor)
                NutrientRow("Fat", detail.fatPer100g * factor)
                NutrientRow("Fiber", detail.fiberPer100g * factor)
            }
        }

        Text("Serving size", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
        if (detail.servingSizes.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(detail.servingSizes, key = { it.id }) { size ->
                    FilterChip(
                        selected = selectedSizeId == size.id,
                        onClick = { selectedSizeId = size.id },
                        label = { Text(if (isGramServing(size)) "克" else size.labelCn.ifBlank { size.label }) },
                    )
                }
            }
        }

        Text(
            "Estimated edible portion: ${format(grams ?: 0.0)} g",
            color = Color(0xFFE29A33),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
        )
        Text(
            quantityText.ifBlank { "0" },
            color = Color(0xFF00B978),
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
        )
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(0.34f).align(Alignment.CenterHorizontally),
            color = Color(0xFF00B978),
        )
        Text(
            unitLabel,
            color = Color(0xFF00B978),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, bottom = 14.dp),
        )
        if (grams != null && grams > 5000) {
            Text(
                "Estimated weight exceeds the 5000 g limit.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )
        }

        QuantityKeypad(
            value = quantityText,
            canSave = grams != null && grams in 1.0..5000.0,
            replaceOnNextInput = replaceOnNextInput,
            onValueChange = { quantityText = it },
            onInputStarted = { replaceOnNextInput = false },
            onSave = { grams?.let(onAdd) },
        )
    }
}

@Composable
private fun QuantityKeypad(
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
        onValueChange(format(next))
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
private fun NutrientRow(label: String, grams: Double) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text("${format(grams)} g")
    }
}

private fun format(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

private fun isGramServing(size: ServingSizeResponse): Boolean =
    size.label.matches(Regex("\\d+(\\.\\d+)?g", RegexOption.IGNORE_CASE)) ||
        size.labelCn.matches(Regex("\\d+(\\.\\d+)?克"))

private fun defaultQuantity(size: ServingSizeResponse): String =
    if (isGramServing(size)) format(size.grams) else "1"
