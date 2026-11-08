package org.example.t3gymmanagementsystem.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stroke icons using the SVG path data from the prototype (24x24, stroke width 1.8). */
object GxIcons {
    val paths: Map<String, List<String>> = mapOf(
        "grid" to listOf("M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z"),
        "users" to listOf(
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z",
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M16 3.13a4 4 0 0 1 0 7.75",
        ),
        "card" to listOf("M3 5h18v14H3z", "M3 10h18", "M7 15h4"),
        "scan" to listOf("M3 7V3h4M17 3h4v4M21 17v4h-4M7 21H3v-4", "M7 8h10v8H7z"),
        "nfc" to listOf("M7.5 8.5a5 5 0 0 1 0 7", "M4.5 5.5a9 9 0 0 1 0 13", "M10.5 11.5a1 1 0 0 1 0 1", "M15 7v10"),
        "receipt" to listOf("M5 3h14v18l-3-2-3 2-3-2-3 2-2-2z", "M9 8h6M9 12h6M9 16h3"),
        "box" to listOf("M21 16V8l-9-5-9 5v8l9 5z", "M3.3 7.2 12 12l8.7-4.8M12 22V12"),
        "cart" to listOf("M3 3h2l2.2 10h9.8l2-7H6", "M9 19a1 1 0 1 0 0 .01M17 19a1 1 0 1 0 0 .01"),
        "chart" to listOf("M4 19V9M10 19V5M16 19v-7M22 19H2"),
        "settings" to listOf(
            "M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7z",
            "M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.12 2.12-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1.04 1.55V20h-3v-.08a1.7 1.7 0 0 0-1.05-1.56 1.7 1.7 0 0 0-1.88.34l-.06.06-2.12-2.12.06-.06A1.7 1.7 0 0 0 7 14.7a1.7 1.7 0 0 0-1.55-1.04H5.3v-3h.09A1.7 1.7 0 0 0 6.94 9.6a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.12-2.12.06.06a1.7 1.7 0 0 0 1.88.34 1.7 1.7 0 0 0 1.04-1.55V4.3h3v.09a1.7 1.7 0 0 0 1.05 1.55 1.7 1.7 0 0 0 1.88-.34l.06-.06 2.12 2.12-.06.06a1.7 1.7 0 0 0-.34 1.88 1.7 1.7 0 0 0 1.55 1.04H22v3h-.09A1.7 1.7 0 0 0 20.36 14z",
        ),
        "plus" to listOf("M12 5v14M5 12h14"),
        "clock" to listOf("M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20z", "M12 6v6l4 2"),
        "download" to listOf("M12 3v12M7 10l5 5 5-5", "M5 21h14"),
        "database" to listOf(
            "M20 6c0 2.2-3.6 4-8 4S4 8.2 4 6s3.6-4 8-4 8 1.8 8 4z",
            "M4 6v6c0 2.2 3.6 4 8 4s8-1.8 8-4V6",
            "M4 12v6c0 2.2 3.6 4 8 4s8-1.8 8-4v-6",
        ),
        "alert" to listOf(
            "M12 9v4M12 17h.01",
            "M10.3 3.6 2.5 18a2 2 0 0 0 1.76 3h15.48a2 2 0 0 0 1.76-3L13.7 3.6a2 2 0 0 0-3.4 0z",
        ),
        "close" to listOf("M18 6 6 18M6 6l12 12"),
        "check" to listOf("M5 12l4 4L19 6"),
        "arrow" to listOf("M5 12h14M13 6l6 6-6 6"),
        "logout" to listOf("M10 17l5-5-5-5M15 12H3", "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"),
    )

    fun vector(name: String, color: Color): ImageVector {
        val builder = ImageVector.Builder(
            name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
        )
        (paths[name] ?: paths.getValue("grid")).forEach { d ->
            builder.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(color),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return builder.build()
    }
}

@Composable
fun GxIcon(name: String, tint: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    val vector = remember(name, tint) { GxIcons.vector(name, tint) }
    Image(imageVector = vector, contentDescription = null, modifier = modifier.size(size))
}