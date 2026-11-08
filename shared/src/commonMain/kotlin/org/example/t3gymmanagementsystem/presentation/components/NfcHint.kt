package org.example.t3gymmanagementsystem.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.t3gymmanagementsystem.presentation.theme.Gx

/** A small contactless (tap) symbol: a dot with three arcs. */
@Composable
fun NfcGlyph(tint: Color, size: Dp = 14.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.11f
        val cx = s * 0.08f
        val cy = s / 2f
        drawCircle(tint, stroke * 0.9f, Offset(cx, cy))
        listOf(0.38f, 0.66f, 0.92f).forEach { r ->
            val radius = s * r
            drawArc(
                color = tint, startAngle = -45f, sweepAngle = 90f, useCenter = false,
                topLeft = Offset(cx - radius, cy - radius), size = Size(radius * 2, radius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** The tap symbol with a short hint beside it. */
@Composable
fun NfcHint(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        NfcGlyph(Gx.Blue, 10.dp)
        Text(text, color = Gx.Muted, fontSize = 10.sp)
    }
}