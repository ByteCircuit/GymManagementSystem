package org.example.t3gymmanagementsystem.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.t3gymmanagementsystem.presentation.theme.Gx

private val tonePalette = listOf(Gx.Red, Gx.Amber, Gx.Blue, Gx.Green, Color(0xFFA78BFA))

/** A stable colour per category, so the same kind of product always looks the same. */
fun productTone(category: String): Color =
    tonePalette[(category.trim().lowercase().hashCode() and 0x7fffffff) % tonePalette.size]

fun productInitials(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }

/** Initials on a tinted square. Products have no photos, so this stands in for the picture. */
@Composable
fun ProductBadge(name: String, category: String, size: Dp, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val tone = productTone(category)
    Box(
        modifier.size(size).clip(RoundedCornerShape(6.dp)).background(tone.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(productInitials(name), color = tone, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.clip(shape)
            .background(if (selected) Gx.Red.copy(alpha = 0.18f) else Gx.Input)
            .border(1.dp, if (selected) Gx.Red else Gx.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, color = if (selected) Gx.Text else Gx.TextSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}