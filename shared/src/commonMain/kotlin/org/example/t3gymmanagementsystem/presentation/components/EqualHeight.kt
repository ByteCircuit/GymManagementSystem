package org.example.t3gymmanagementsystem.presentation.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Makes two side-by-side panel bodies the same height. Each side reports its natural height through onNatural
 * and is given the taller of the two as minHeightPx, so a panel shrinks again when its content gets smaller.
 */
fun Modifier.equalHeight(minHeightPx: Int, center: Boolean = false, onNatural: (Int) -> Unit): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        onNatural(placeable.height)
        val h = maxOf(placeable.height, minHeightPx).coerceAtMost(constraints.maxHeight)
        layout(placeable.width, h) {
            placeable.placeRelative(0, if (center) (h - placeable.height) / 2 else 0)
        }
    }