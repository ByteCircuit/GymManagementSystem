package org.example.t3gymmanagementsystem.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.t3gymmanagementsystem.presentation.theme.Gx

enum class ButtonVariant { Primary, Secondary, Ghost }

@Composable
fun GxButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    icon: String? = null,
    enabled: Boolean = true,
) {
    val bg = when (variant) {
        ButtonVariant.Primary -> Gx.Red
        ButtonVariant.Secondary -> Color(0xFF232326)
        ButtonVariant.Ghost -> Color.Transparent
    }
    val border = when (variant) {
        ButtonVariant.Primary -> Color(0xFFF04945)
        ButtonVariant.Secondary -> Color(0xFF37373B)
        ButtonVariant.Ghost -> Color(0xFF343438)
    }
    val fg = when (variant) {
        ButtonVariant.Primary -> Color.White
        ButtonVariant.Secondary -> Color(0xFFDDDDE0)
        ButtonVariant.Ghost -> Color(0xFF9999A0)
    }
    Row(
        modifier = modifier
            .padding(8.dp) // Outer spacing
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg.copy(alpha = if (enabled) 1f else 0.45f))
            .border(BorderStroke(1.dp, border), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) GxIcon(icon, fg, 16.dp)
        Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GxPanel(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Gx.Surface)
            .border(BorderStroke(1.dp, Gx.Border), RoundedCornerShape(7.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = Gx.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            action?.invoke()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A2A2D)))
        content()
    }
}

@Composable
fun StatusPill(text: String, tone: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(tone.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(tone))
        Text(text, color = tone, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GxTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    masked: Boolean = false,
) {
    Column(modifier) {
        Text(label.uppercase(), color = Color(0xFF74747B), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Gx.Input)
                .border(BorderStroke(1.dp, Color(0xFF353539)), RoundedCornerShape(5.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color(0xFFDEDEE1), fontSize = 13.sp),
                cursorBrush = SolidColor(Gx.Red),
                visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, color = Color(0xFF666970), fontSize = 13.sp)
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
fun GxToast(message: String, isError: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val accent = if (isError) Gx.Red else Gx.Green
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF202022))
            .border(BorderStroke(1.dp, Color(0xFF3A3A3E)), RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
        Column {
            Text(if (isError) "Something went wrong" else "Operation successful", color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = Gx.Muted, fontSize = 11.sp)
        }
        Box(Modifier.clickable(onClick = onDismiss).padding(4.dp)) { GxIcon("close", Gx.Muted, 14.dp) }
    }
}