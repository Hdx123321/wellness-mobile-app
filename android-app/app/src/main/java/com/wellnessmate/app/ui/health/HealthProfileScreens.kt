package com.wellnessmate.app.ui.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wellnessmate.app.data.ProfileResponse
import com.wellnessmate.app.ui.HealthMetrics
import com.wellnessmate.app.ui.HealthProfileViewModel
import kotlin.math.roundToInt

@Composable
fun HealthSummaryCard(viewModel: HealthProfileViewModel, onOpen: () -> Unit) {
    val state by viewModel.state.collectAsState()
    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Your health", style = MaterialTheme.typography.titleLarge)
                Text("View ›", color = MaterialTheme.colorScheme.primary)
            }
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.padding(12.dp))
                state.metrics != null -> {
                    val metrics = state.metrics!!
                    Text(
                        "${format(metrics.currentWeightKg)} kg  ·  BMI ${format(metrics.bmi)}  ·  " +
                            "Goal ${state.profile?.targetWeightKg?.let { "${format(it)} kg" } ?: "not set"}",
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    metrics.goalProgress?.let {
                        LinearProgressIndicator(progress = { it.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                        Text("${(it * 100).roundToInt()}% of weight goal", style = MaterialTheme.typography.bodySmall)
                    }
                }
                else -> Text(state.error ?: "Health profile unavailable", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun HealthProfileScreen(
    viewModel: HealthProfileViewModel,
    onHeight: () -> Unit,
    onWeight: () -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val profile = state.profile
    val metrics = state.metrics

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            Header("Health profile", onBack)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.loading) CircularProgressIndicator(modifier = Modifier.padding(24.dp))
        }
        if (profile != null && metrics != null) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Height", "${format(profile.heightCm)} cm", Modifier.weight(1f), onHeight)
                    MetricCard("Weight", "${format(metrics.currentWeightKg)} kg", Modifier.weight(1f), onWeight)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Basal metabolism", metrics.basalMetabolismText, Modifier.weight(1f))
                    MetricCard("Fat-burning heart rate", metrics.fatBurningHeartRateText, Modifier.weight(1f))
                }
                BmiRangeCard(metrics.bmi)
                Text(
                    "Heart-rate estimates are general exercise-intensity guidance and can be affected by medication or heart conditions.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
                GoalCard(profile, metrics)
            }
        }
    }
}

@Composable
private fun BmiRangeCard(bmi: Double) {
    val minimum = 15.0
    val maximum = 40.0
    val ranges = listOf(
        Triple(15.0, 18.5, Color(0xFF64B5F6)),
        Triple(18.5, 25.0, Color(0xFF66BB6A)),
        Triple(25.0, 30.0, Color(0xFFFFCA28)),
        Triple(30.0, 40.0, Color(0xFFEF5350)),
    )
    val category = when {
        bmi < 18.5 -> "Underweight"
        bmi < 25.0 -> "Healthy"
        bmi < 30.0 -> "Overweight"
        else -> "Obesity range"
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text("BMI", style = MaterialTheme.typography.titleMedium)
                Text("${format(bmi)} · $category", style = MaterialTheme.typography.titleMedium)
            }
            Canvas(modifier = Modifier.fillMaxWidth().height(42.dp).padding(top = 12.dp)) {
                val barTop = 8.dp.toPx()
                val barHeight = 12.dp.toPx()
                ranges.forEach { (start, end, color) ->
                    val left = ((start - minimum) / (maximum - minimum) * size.width).toFloat()
                    val width = ((end - start) / (maximum - minimum) * size.width).toFloat()
                    drawRect(color = color, topLeft = Offset(left, barTop), size = Size(width, barHeight))
                }
                val markerX = ((bmi.coerceIn(minimum, maximum) - minimum) /
                    (maximum - minimum) * size.width).toFloat()
                drawLine(
                    color = Color(0xFF212121),
                    start = Offset(markerX, 0f),
                    end = Offset(markerX, barTop + barHeight + 7.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(markerX, barTop + barHeight / 2),
                )
                drawCircle(
                    color = Color(0xFF212121),
                    radius = 2.5.dp.toPx(),
                    center = Offset(markerX, barTop + barHeight / 2),
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "<18.5" to 3.5f,
                    "18.5–24.9" to 6.5f,
                    "25–29.9" to 5f,
                    "30+" to 10f,
                ).forEach { (label, width) ->
                    Text(
                        label,
                        modifier = Modifier.weight(width),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
fun HeightPickerScreen(viewModel: HealthProfileViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val profile = state.profile
    if (profile == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Header("Adjust height", onBack)
            CircularProgressIndicator()
        }
        return
    }
    val options = (160..500).map { it / 2.0 }
    var selected by rememberSaveable(profile.heightCm) { mutableDoubleStateOf(profile.heightCm) }
    val initialIndex = ((profile.heightCm - 80.0) * 2).roundToInt().coerceIn(options.indices)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (initialIndex - 2).coerceAtLeast(0))

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Adjust height", onBack)
        Text("Scroll and tap your height", style = MaterialTheme.typography.titleMedium)
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().height(420.dp).padding(vertical = 12.dp)) {
            itemsIndexed(options) { _, height ->
                val selectedRow = height == selected
                Text(
                    "${format(height)} cm",
                    style = if (selectedRow) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
                    fontWeight = if (selectedRow) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedRow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().clickable { selected = height }.padding(16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                HorizontalDivider()
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = { viewModel.updateHeight(selected, onBack) },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.saving) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Save ${format(selected)} cm")
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Card(modifier = modifier.padding(vertical = 5.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
            if (onClick != null) Text("Tap to change ›", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun GoalCard(profile: ProfileResponse, metrics: HealthMetrics) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Target weight plan", style = MaterialTheme.typography.titleLarge)
            if (profile.targetWeightKg == null) {
                Text("No target weight plan set.", modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("${format(metrics.currentWeightKg)} kg → ${format(profile.targetWeightKg)} kg")
                metrics.goalProgress?.let {
                    LinearProgressIndicator(progress = { it.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                    Text("${(it * 100).roundToInt()}% complete", modifier = Modifier.padding(top = 4.dp))
                }
                Text(
                    "${profile.goalDurationWeeks} weeks" + (metrics.goalEndDate?.let { " · target date $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private fun format(value: Double): String = if (value % 1.0 == 0.0) {
    value.toLong().toString()
} else {
    "%.1f".format(value)
}
