package org.example.t3gymmanagementsystem.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.example.t3gymmanagementsystem.presentation.theme.Gx

/** The gym's payment QR shown large in a popup, so it is easy to scan. Closes with the button, Esc or a click outside. */
@Composable
fun QrPopup(image: ImageBitmap, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.clip(RoundedCornerShape(10.dp))
                .background(Gx.Bg)
                .border(BorderStroke(1.dp, Gx.Border), RoundedCornerShape(10.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Payment QR code", color = Gx.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Image(
                bitmap = image, contentDescription = "Payment QR code", contentScale = ContentScale.Fit,
                modifier = Modifier.size(400.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(12.dp),
            )
            Text("Ask the member to scan this, then confirm once the payment arrives.", color = Gx.Muted, fontSize = 12.sp)
            GxButton("Close", onClick = onClose)
        }
    }
}