package com.wellnessmate.app.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.wellnessmate.app.data.FoodDetailResponse
import com.wellnessmate.app.data.ServingSizeResponse
import com.wellnessmate.app.ui.FoodViewModel

@Composable
fun FoodDetailScreen(
    foodId: Long,
    viewModel: FoodViewModel,
    onAdd: (Double) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var selectedGrams by rememberSaveable { mutableStateOf("100") }

    LaunchedEffect(foodId) { viewModel.loadFoodDetail(foodId) }

    val detail = state.foodDetail
    if (state.detailLoading || detail == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text("Loading…", modifier = Modifier.padding(top = 8.dp))
        }
        return
    }

    val defaultSize = detail.servingSizes.firstOrNull { it.isDefault }
    var selectedSize by rememberSaveable { mutableStateOf(defaultSize) }

    // 当切换分量选项时，更新克数
    LaunchedEffect(selectedSize) {
        selectedSize?.let { selectedGrams = it.grams.toLong().toString() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                viewModel.clearDetail()
                onBack()
            }) { Text("Back") }
            Text(detail.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        }

        // 营养信息卡片
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Per 100g", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${format(detail.caloriesPer100g)} kcal  |  Protein ${format(detail.proteinPer100g)}g  |  Carbs ${format(detail.carbohydratePer100g)}g  |  Fat ${format(detail.fatPer100g)}g  |  Fiber ${format(detail.fiberPer100g)}g",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // 分量选择
        Text("Serving size", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        if (detail.servingSizes.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                detail.servingSizes.forEach { size ->
                    FilterChip(
                        selected = selectedSize == size,
                        onClick = { selectedSize = size },
                        label = { Text(size.labelCn.ifBlank { size.label }) },
                    )
                }
            }
        }

        // 自定义克数
        OutlinedTextField(
            value = selectedGrams,
            onValueChange = {
                selectedGrams = it
                selectedSize = null
            },
            label = { Text("Grams") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )

        // 估算营养（按选择的克数计算）
        val grams = selectedGrams.toDoubleOrNull()
        if (grams != null && grams > 0) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Estimated nutrition for ${format(grams)}g", style = MaterialTheme.typography.titleMedium)
                    val factor = grams / 100.0
                    Text(
                        "${format(detail.caloriesPer100g * factor)} kcal  |  " +
                            "Protein ${format(detail.proteinPer100g * factor)}g  |  " +
                            "Carbs ${format(detail.carbohydratePer100g * factor)}g  |  " +
                            "Fat ${format(detail.fatPer100g * factor)}g  |  " +
                            "Fiber ${format(detail.fiberPer100g * factor)}g",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        // 加入按钮
        Button(
            onClick = {
                val g = grams ?: return@Button
                if (g in 1.0..5000.0) {
                    onAdd(g)
                    onBack()
                }
            },
            enabled = grams != null && grams > 0 && grams <= 5000,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        ) {
            Text(
                if (grams == null || grams <= 0) "Enter grams"
                else if (grams > 5000) "Maximum 5000g"
                else "Add ${format(grams)}g to meal"
            )
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private fun format(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)
