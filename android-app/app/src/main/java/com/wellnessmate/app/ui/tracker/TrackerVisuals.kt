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
import androidx.compose.ui.geometry.CornerRadius
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
fun SevenDayBarChart(values: List<Pair<LocalDate, Double>>, unit: String) {
    val barColor = MaterialTheme.colorScheme.primary
    val max = values.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Last 7 days · $unit", style = MaterialTheme.typography.titleMedium)
        Canvas(modifier = Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp)) {
            val slot = size.width / values.size.coerceAtLeast(1)
            values.forEachIndexed { index, value ->
                val height = (size.height * (value.second / max)).toFloat()
                drawRoundRect(
                    color = barColor,
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
