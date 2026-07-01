package com.wellnessmate.app.ui.tracker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
    val data = values.filter { it.second > 0.0 }
    if (data.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Last 7 days · $unit", style = MaterialTheme.typography.titleMedium)
            Text(
                "No data available",
                modifier = Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    val lineColor = Color(0xFF4A90D9)
    val fillColor = Color(0x334A90D9)
    val max = data.maxOf { it.second }
    val range = max.coerceAtLeast(1.0)

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Last 7 days · $unit", style = MaterialTheme.typography.titleMedium)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 24.dp)
                .clickable { onClick() }
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
                val cy = (chartHeight * (1 - data[0].second / range)).toFloat()
                drawCircle(lineColor, radius = 6f, center = Offset(cx, cy))
            } else {
                val fillPath = Path().apply {
                    moveTo(0f, chartHeight)
                    data.forEachIndexed { i, (_, v) ->
                        lineTo(i * slotWidth, (chartHeight * (1 - v / range)).toFloat())
                    }
                    lineTo((data.size - 1) * slotWidth, chartHeight)
                    close()
                }
                drawPath(fillPath, fillColor)

                val linePath = Path()
                data.forEachIndexed { i, (_, v) ->
                    val x = i * slotWidth
                    val y = (chartHeight * (1 - v / range)).toFloat()
                    if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                }
                drawPath(linePath, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))

                data.forEachIndexed { i, (_, v) ->
                    val cx = i * slotWidth
                    val cy = (chartHeight * (1 - v / range)).toFloat()
                    drawCircle(lineColor, radius = 5f, center = Offset(cx, cy))
                    drawCircle(Color.White, radius = 2.5f, center = Offset(cx, cy))
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
}

@Composable
fun MiniWeightLineChart(
    values: List<Pair<LocalDate, Double>>,
    modifier: Modifier = Modifier,
) {
    val data = values.filter { it.second > 0.0 }
    if (data.isEmpty()) return
    val lineColor = Color(0xFF4A90D9)
    val minimum = data.minOf { it.second }
    val range = (data.maxOf { it.second } - minimum).coerceAtLeast(1.0)
    Canvas(modifier = modifier.fillMaxWidth().height(56.dp)) {
        val step = if (data.size > 1) size.width / (data.size - 1) else 0f
        val path = Path()
        val points = mutableListOf<Offset>()
        data.forEachIndexed { index, (_, value) ->
            val x = if (data.size == 1) size.width / 2f else index * step
            val y = size.height - ((value - minimum) / range * size.height * 0.75 + size.height * 0.125).toFloat()
            points += Offset(x, y)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        points.forEach { point ->
            drawCircle(Color.White, radius = 9f, center = point)
            drawCircle(lineColor, radius = 6f, center = point)
        }
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
