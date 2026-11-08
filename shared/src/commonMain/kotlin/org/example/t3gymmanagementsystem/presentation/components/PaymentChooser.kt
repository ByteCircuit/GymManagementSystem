package org.example.t3gymmanagementsystem.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.PaymentMethod
import org.example.t3gymmanagementsystem.presentation.theme.Gx

/** Cash or Online. Choosing Online shows the gym's own QR image (set on the Payments page); click it to enlarge. */
@Composable
fun PaymentChooser(
    services: AppServices,
    methodIndex: Int,
    onMethod: (Int) -> Unit,
    note: String,
    onNote: (String) -> Unit,
) {
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var enlarged by remember { mutableStateOf(false) }
    val online = PaymentMethod.entries[methodIndex] == PaymentMethod.Online

    LaunchedEffect(online) {
        if (online) {
            qr = runCatching { services.qrImage() }.getOrNull()
            loaded = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PAYMENT METHOD", color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
        GxSegmented(PaymentMethod.entries.map { it.label }, methodIndex, onMethod, Modifier.fillMaxWidth())
        if (online) {
            val image = qr
            if (image != null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        bitmap = image, contentDescription = "Payment QR code", contentScale = ContentScale.Fit,
                        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(6.dp)).background(Color.White)
                            .clickable { enlarged = true }.padding(8.dp),
                    )
                    GxButton("Enlarge QR", onClick = { enlarged = true })
                    Text("Ask the member to scan, then confirm once the payment arrives.", color = Gx.Muted, fontSize = 11.sp)
                }
                if (enlarged) QrPopup(image) { enlarged = false }
            } else if (loaded) {
                Text("No QR image yet. Add the gym's QR on the Payments page.", color = Gx.Amber, fontSize = 11.sp)
            }
        }
        GxTextField("Reference / note (optional)", note, onNote, Modifier.fillMaxWidth(), placeholder = "e.g. online reference number")
    }
}