package com.alpinefitness.app.ui.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    datesWithData: Set<LocalDate>,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    val cells = List(month.atDay(1).dayOfWeek.value - 1) { null } +
        (1..month.lengthOfMonth()).map(month::atDay)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Text("‹") }
            Text(
                "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                onClick = { onMonthChange(month.plusMonths(1)) },
                enabled = month < YearMonth.from(today),
            ) { Text("›") }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(it, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (week + List(7 - week.size) { null }).forEach { date ->
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                            .background(
                                if (date == selectedDate) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                                MaterialTheme.shapes.small,
                            )
                            .clickable(enabled = date != null && date <= today) {
                                date?.let(onDateSelected)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(date.dayOfMonth.toString())
                                if (date in datesWithData) {
                                    Box(
                                        Modifier.padding(top = 2.dp).background(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.shapes.extraSmall,
                                        ).height(3.dp).fillMaxWidth(0.25f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SevenDayBarChart(
    values: List<Pair<LocalDate, Double>>,
    unit: String,
    showMissingPlaceholders: Boolean = false,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val max = values.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Last 7 days · $unit", style = MaterialTheme.typography.titleMedium)
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp)) {
            val slot = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { index, value ->
                val missing = value.second <= 0.0
                val height = if (missing && showMissingPlaceholders) size.height * 0.18f
                    else (size.height * (value.second / max)).toFloat()
                drawRoundRect(
                    color = if (missing && showMissingPlaceholders) Color(0xFFD7DDD9) else barColor,
                    topLeft = androidx.compose.ui.geometry.Offset(index * slot + slot * 0.2f, size.height - height),
                    size = androidx.compose.ui.geometry.Size(slot * 0.6f, height),
                    cornerRadius = CornerRadius(8f, 8f),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            values.forEach { (date, _) ->
                Text(
                    date.dayOfWeek.name.take(1),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
fun WeightLineChart(
    values: List<Pair<LocalDate, Double>>,
    unit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Last 7 days · $unit", style = MaterialTheme.typography.titleMedium)
        WeightPlot(values, compact = false, modifier = Modifier.clickable { onClick() })
    }
}

private fun formatChartValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

@Composable
fun MiniWeightLineChart(
    values: List<Pair<LocalDate, Double>>,
    modifier: Modifier = Modifier,
) {
    WeightPlot(values, compact = true, modifier = modifier)
}

@Composable
private fun WeightPlot(values: List<Pair<LocalDate, Double>>, compact: Boolean, modifier: Modifier = Modifier) {
    val slots = values.takeLast(7)
    val data = slots.map { it.second }.filter { it > 0.0 }
    val rawMin = data.minOrNull() ?: 0.0
    val rawMax = data.maxOrNull() ?: 1.0
    val axisPadding = ((rawMax - rawMin) * 0.15).coerceAtLeast(0.5)
    val axisMin = (rawMin - axisPadding).coerceAtLeast(0.0)
    val axisMax = rawMax + axisPadding
    val axisRange = (axisMax - axisMin).coerceAtLeast(1.0)
    val ticks = if (compact) 2 else 3
    val plotHeight = if (compact) 64.dp else 150.dp
    val axisWidth = if (compact) 36.dp else 44.dp
    val lineColor = Color(0xFF4A90D9)

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.width(axisWidth).height(plotHeight),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                repeat(ticks) { index ->
                    Text(
                        formatChartValue(axisMax - index * axisRange / (ticks - 1)),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Canvas(modifier = Modifier.weight(1f).height(plotHeight).padding(start = 6.dp)) {
                repeat(ticks) { index ->
                    val y = size.height * index / (ticks - 1)
                    drawLine(Color.LightGray, Offset(0f, y), Offset(size.width, y), 1f)
                }
                val slotWidth = size.width / slots.size.coerceAtLeast(1)
                val path = Path()
                var started = false
                slots.forEachIndexed { index, (_, value) ->
                    if (value <= 0.0) return@forEachIndexed
                    val x = slotWidth * (index + 0.5f)
                    val y = (size.height * (1 - (value - axisMin) / axisRange)).toFloat()
                    if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                }
                if (started) drawPath(
                    path, lineColor,
                    style = Stroke(if (compact) 2.5f else 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                slots.forEachIndexed { index, (_, value) ->
                    if (value <= 0.0) return@forEachIndexed
                    val point = Offset(
                        slotWidth * (index + 0.5f),
                        (size.height * (1 - (value - axisMin) / axisRange)).toFloat(),
                    )
                    drawCircle(Color.White, radius = if (compact) 6f else 7f, center = point)
                    drawCircle(lineColor, radius = if (compact) 4f else 5f, center = point)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(axisWidth + 6.dp))
            Row(modifier = Modifier.weight(1f)) {
                slots.forEach { (date, _) ->
                    Text(
                        "${date.monthValue}/${date.dayOfMonth}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        if (data.isEmpty()) Text(
            "No weight data",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
fun MiniSevenDayBarChart(
    values: List<Pair<LocalDate, Double>>,
    modifier: Modifier = Modifier,
) {
    val color = Color(0xFF00B978)
    val maximum = values.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        val slot = size.width / values.size.coerceAtLeast(1)
        values.forEachIndexed { index, (_, value) ->
            val missing = value <= 0.0
            val barHeight = if (missing) size.height * 0.24f else (size.height * value / maximum).toFloat()
            drawRoundRect(
                color = if (missing) Color(0xFFD7DDD9) else color,
                topLeft = Offset(index * slot + slot * 0.22f, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(slot * 0.56f, barHeight),
                cornerRadius = CornerRadius(5f, 5f),
            )
        }
    }
}

@Composable
fun TrendsLineChart(
    data: List<Pair<LocalDate, Double>>,
    color: Color,
) {
    if (data.isEmpty()) {
        Text("No data available", modifier = Modifier.padding(vertical = 16.dp))
        return
    }
    val max = data.maxOf { it.second }
    val min = data.minOf { it.second }
    val range = (max - min).coerceAtLeast(0.001)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 24.dp)
    ) {
        val chartWidth = size.width
        val chartHeight = size.height
        val slotWidth = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth

        for (i in 0..4) {
            val y = chartHeight * i / 4
            drawLine(Color.LightGray, Offset(0f, y), Offset(chartWidth, y), 1f)
        }

        if (data.size == 1) {
            val cx = chartWidth / 2
            val cy = (chartHeight * (1 - (data[0].second - min) / range)).toFloat()
            drawCircle(color, radius = 6f, center = Offset(cx, cy))
        } else {
            val linePath = Path()
            data.forEachIndexed { i, (_, v) ->
                val x = i * slotWidth
                val y = (chartHeight * (1 - (v - min) / range)).toFloat()
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            drawPath(linePath, color, style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

            data.forEachIndexed { i, (_, v) ->
                val cx = i * slotWidth
                val cy = (chartHeight * (1 - (v - min) / range)).toFloat()
                drawCircle(color, radius = 4f, center = Offset(cx, cy))
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        data.forEach { (date, _) ->
            Text(
                "${date.monthValue}/${date.dayOfMonth}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
