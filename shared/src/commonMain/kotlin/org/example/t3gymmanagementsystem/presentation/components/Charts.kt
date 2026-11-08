package org.example.t3gymmanagementsystem.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.t3gymmanagementsystem.presentation.theme.Gx

data class ChartSeries(val name: String, val values: List<Long>, val color: Color, val dashed: Boolean = false)

/** 1, 2 or 5 times a power of ten that is at least value, so the axis ends on a round number. */
private fun niceCeil(value: Long): Long {
    if (value <= 1L) return 1L
    var magnitude = 1L
    while (magnitude * 10 <= value) magnitude *= 10
    return listOf(1L, 2L, 5L, 10L).map { it * magnitude }.first { it >= value }
}

/** Centavos as a short peso amount, for example 12.5k. */
fun compactPesos(cents: Long): String {
    val pesos = cents / 100
    return "\u20B1" + when {
        pesos >= 1_000_000 -> "${pesos / 1_000_000}.${(pesos % 1_000_000) / 100_000}M"
        pesos >= 1_000 -> "${pesos / 1_000}.${(pesos % 1_000) / 100}k"
        else -> pesos.toString()
    }
}

@Composable
fun LineChart(
    labels: List<String>,
    series: List<ChartSeries>,
    formatY: (Long) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
) {
    val maxValue = niceCeil(series.maxOfOrNull { s -> s.values.maxOrNull() ?: 0L } ?: 0L)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(height)) {
            Column(
                Modifier.fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                for (i in 4 downTo 0) Text(formatY(maxValue * i / 4), color = Gx.Faint, fontSize = 9.sp)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val w = size.width
                val h = size.height
                for (i in 0..4) {
                    val gy = h - h * i / 4f
                    drawLine(Gx.Border, Offset(0f, gy), Offset(w, gy), strokeWidth = 1f)
                }
                series.forEachIndexed { index, s ->
                    val n = s.values.size
                    if (n == 0) return@forEachIndexed
                    fun px(i: Int) = if (n == 1) w / 2f else w * i / (n - 1f)
                    fun py(v: Long) = h - h * (v.toFloat() / maxValue.toFloat())
                    val line = Path()
                    s.values.forEachIndexed { i, v -> if (i == 0) line.moveTo(px(i), py(v)) else line.lineTo(px(i), py(v)) }
                    if (index == 0 && n > 1) {
                        val area = Path()
                        area.moveTo(px(0), h)
                        s.values.forEachIndexed { i, v -> area.lineTo(px(i), py(v)) }
                        area.lineTo(px(n - 1), h)
                        area.close()
                        drawPath(area, s.color.copy(alpha = 0.12f))
                    }
                    drawPath(
                        line, s.color,
                        style = Stroke(width = 3f, pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null),
                    )
                    if (n == 1) drawCircle(s.color, 5f, Offset(px(0), py(s.values[0])))
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 40.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val shown = if (labels.size >= 3) listOf(labels.first(), labels[labels.size / 2], labels.last()) else labels
                shown.forEach { Text(it, color = Gx.Faint, fontSize = 9.sp) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(s.color))
                    Text(s.name, color = Gx.TextSoft, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun Donut(values: List<Pair<Color, Int>>, centerTop: String, centerBottom: String, diameter: Dp = 170.dp) {
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 26f
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val total = values.sumOf { it.second }
            if (total == 0) {
                drawArc(color = Gx.Border, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            } else {
                var start = -90f
                values.forEach { (color, v) ->
                    if (v > 0) {
                        val sweep = 360f * v / total
                        drawArc(color = color, startAngle = start, sweepAngle = sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                        start += sweep
                    }
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, color = Gx.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(centerBottom, color = Gx.Muted, fontSize = 9.sp)
        }
    }
}