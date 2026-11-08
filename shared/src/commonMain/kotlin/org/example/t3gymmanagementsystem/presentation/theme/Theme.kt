package org.example.t3gymmanagementsystem.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette taken from index.css :root variables and the literal hex values used in the prototype. */
object Gx {
    val Bg = Color(0xFF0D0D0E)
    val Sidebar = Color(0xFF111112)
    val Surface = Color(0xFF181819)
    val Surface2 = Color(0xFF1E1E20)
    val Surface3 = Color(0xFF242427)
    val Input = Color(0xFF202023)
    val Border = Color(0xFF2D2D30)
    val BorderStrong = Color(0xFF3A3A3E)
    val SidebarBorder = Color(0xFF29292C)

    val Text = Color(0xFFF5F5F6)
    val TextSoft = Color(0xFFB7B7BC)
    val Muted = Color(0xFF77777E)
    val Faint = Color(0xFF66666D)
    val NavIdle = Color(0xFFA2A2A8)

    val Red = Color(0xFFE53935)
    val RedBright = Color(0xFFEF5551)
    val Amber = Color(0xFFF59B32)
    val Green = Color(0xFF33B889)
    val Blue = Color(0xFF4A9CDB)

    fun tint(color: Color, alpha: Float) = color.copy(alpha = alpha)
}

private val scheme: ColorScheme = darkColorScheme(
    primary = Gx.Red,
    onPrimary = Color.White,
    background = Gx.Bg,
    onBackground = Gx.Text,
    surface = Gx.Surface,
    onSurface = Gx.Text,
    outline = Gx.Border,
)

@Composable
fun GymTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}