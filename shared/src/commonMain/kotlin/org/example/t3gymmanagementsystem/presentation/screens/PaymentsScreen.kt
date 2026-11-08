package org.example.t3gymmanagementsystem.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.PaymentSummary
import org.example.t3gymmanagementsystem.ReceiptData
import org.example.t3gymmanagementsystem.TransactionRow
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxDropdown
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxTabs
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.components.QrPopup
import org.example.t3gymmanagementsystem.presentation.components.StatCard
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import org.example.t3gymmanagementsystem.util.Money
import org.example.t3gymmanagementsystem.util.ReceiptText

@Composable
private fun RowScope.PayTh(text: String, weight: Float) {
    Text(text, color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, modifier = Modifier.weight(weight))
}

@Composable
private fun RowScope.PayTd(text: String, weight: Float, strong: Boolean = false, mono: Boolean = false) {
    Text(
        text, color = if (strong) Gx.Text else Gx.TextSoft,
        fontSize = if (mono) 11.sp else 12.sp,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun PayLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Gx.Border))
}

@Composable
fun PaymentsScreen(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<TransactionRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var methodFilter by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var receipt by remember { mutableStateOf<ReceiptData?>(null) }
    var qr by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrTick by remember { mutableStateOf(0) }
    var showQr by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<PaymentSummary?>(null) }

    LaunchedEffect(query) {
        delay(200)
        runCatching { services.listTransactions(query) }
            .onSuccess { rows = it; loaded = true }
            .onFailure { onMessage(it.message ?: "Could not load transactions.", true) }
    }
    LaunchedEffect(Unit) { summary = runCatching { services.paymentSummary() }.getOrNull() }
    LaunchedEffect(selected) {
        val id = selected
        receipt = if (id == null) null else runCatching { services.getReceipt(id) }.getOrNull()
    }
    LaunchedEffect(qrTick) { qr = runCatching { services.qrImage() }.getOrNull() }

    val typeOptions = listOf("All transaction types") + rows.map { it.typeLabel }.distinct().sorted()
    val methodOptions = listOf("All payment methods") + rows.map { it.methodLabel }.distinct().sorted()
    val typeIndex = typeFilter?.let { typeOptions.indexOf(it) }?.takeIf { it >= 0 } ?: 0
    val methodIndex = methodFilter?.let { methodOptions.indexOf(it) }?.takeIf { it >= 0 } ?: 0
    val shown = rows.filter {
        (typeIndex == 0 || it.typeLabel == typeOptions[typeIndex]) &&
            (methodIndex == 0 || it.methodLabel == methodOptions[methodIndex])
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                GxTabs(listOf("Transaction history", "Receipts", "Payment QR"), tab, { tab = it })
            }
            GxButton("Show QR", onClick = {
                if (qr == null) onMessage("No QR image yet. Add the gym's QR in the Payment QR tab.", true) else showQr = true
            })
        }

        val s = summary
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                "Today's transactions", s?.todayCount?.toString() ?: "\u2014",
                if (s == null) "" else "Walk-ins: ${Money.format(s.walkInCents)}",
                "card", Gx.Blue, Modifier.weight(1f),
            )
            StatCard("Membership revenue", s?.let { Money.format(it.membershipCents) } ?: "\u2014", "Today", "users", Gx.Green, Modifier.weight(1f))
            StatCard("Product sales", s?.let { Money.format(it.productCents) } ?: "\u2014", "Today", "card", Gx.Amber, Modifier.weight(1f))
            StatCard("Pending amount", s?.let { Money.format(it.pendingCents) } ?: "\u2014", "Awaiting payment", "clock", Gx.Red, Modifier.weight(1f))
        }

        when (tab) {
            0 -> {
                GxPanel("Transactions", action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        GxDropdown(typeOptions, typeIndex, { typeFilter = if (it == 0) null else typeOptions[it] }, Modifier.width(200.dp))
                        GxDropdown(methodOptions, methodIndex, { methodFilter = if (it == 0) null else methodOptions[it] }, Modifier.width(190.dp))
                    }
                }) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        GxTextField("Search", query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Reference, member name or member number")
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                            PayTh("TRANSACTION ID", 1.6f)
                            PayTh("MEMBER", 1.4f)
                            PayTh("TYPE", 1.4f)
                            PayTh("AMOUNT", 1f)
                            PayTh("METHOD", 1f)
                            PayTh("DATE & TIME", 1.7f)
                            PayTh("RECEIPT", 0.8f)
                        }
                        PayLine()
                        if (shown.isEmpty()) {
                            Text(
                                if (!loaded) "Loading..." else if (query.isBlank() && rows.isEmpty()) "No transactions yet." else "No transactions match.",
                                color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp),
                            )
                        }
                        shown.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                PayTd(r.reference, 1.6f, mono = true)
                                PayTd(r.memberName, 1.4f, strong = true)
                                PayTd(r.typeLabel, 1.4f)
                                PayTd(Money.format(r.totalCents), 1f, strong = true)
                                PayTd(r.methodLabel, 1f)
                                PayTd(r.dateLabel, 1.7f)
                                Box(Modifier.weight(0.8f)) {
                                    Text(
                                        "View", color = Gx.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable {
                                            selected = r.id
                                            tab = 1
                                        }.padding(vertical = 2.dp),
                                    )
                                }
                            }
                            PayLine()
                        }
                    }
                }
            }
            1 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1.6f)) {
                        GxPanel("Receipts") {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    PayTh("TRANSACTION ID", 1.6f)
                                    PayTh("MEMBER", 1.4f)
                                    PayTh("TYPE", 1.4f)
                                    PayTh("TOTAL", 1f)
                                }
                                if (rows.isEmpty()) {
                                    Text(
                                        if (!loaded) "Loading..." else "No transactions yet.",
                                        color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp),
                                    )
                                }
                                rows.forEach { r ->
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(if (r.id == selected) Gx.Input else Color.Transparent)
                                            .clickable { selected = r.id }
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        PayTd(r.reference, 1.6f, mono = true)
                                        PayTd(r.memberName, 1.4f, strong = true)
                                        PayTd(r.typeLabel, 1.4f)
                                        PayTd(Money.format(r.totalCents), 1f, strong = true)
                                    }
                                }
                            }
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        GxPanel("Receipt preview", action = {
                            if (selected != null) GxButton("Close", onClick = { selected = null })
                        }) {
                            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val r = receipt
                                if (r == null) {
                                    Text("Select a transaction to see its receipt.", color = Gx.Muted, fontSize = 12.sp)
                                } else {
                                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color(0xFFF4F1EA)).padding(12.dp)) {
                                        Text(
                                            ReceiptText.render(r), color = Color(0xFF1A1A1A),
                                            fontFamily = FontFamily.Monospace, fontSize = 9.sp,
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        GxButton("Save PDF", onClick = {
                                            val id = selected ?: return@GxButton
                                            scope.launch {
                                                val result = runCatching { services.saveReceiptPdf(id) }
                                                val outcome = result.getOrNull()
                                                when {
                                                    outcome is Outcome.Ok -> {
                                                        val path = outcome.value
                                                        if (path != null) onMessage("Receipt saved to $path", false)
                                                    }
                                                    outcome is Outcome.Fail -> onMessage(outcome.message, true)
                                                    else -> onMessage(result.exceptionOrNull()?.message ?: "Could not save the receipt.", true)
                                                }
                                            }
                                        })
                                        GxButton("Print", onClick = {
                                            val id = selected ?: return@GxButton
                                            scope.launch {
                                                val result = runCatching { services.printReceipt(id) }
                                                val outcome = result.getOrNull()
                                                when {
                                                    outcome is Outcome.Ok -> if (outcome.value) onMessage("Sent to the printer.", false)
                                                    outcome is Outcome.Fail -> onMessage(outcome.message, true)
                                                    else -> onMessage(result.exceptionOrNull()?.message ?: "Could not print the receipt.", true)
                                                }
                                            }
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                GxPanel("Payment QR code") {
                    Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        val image = qr
                        if (image != null) {
                            Image(
                                bitmap = image, contentDescription = "Payment QR code", contentScale = ContentScale.Fit,
                                modifier = Modifier.size(170.dp).clip(RoundedCornerShape(6.dp)).background(Color.White)
                                    .clickable { showQr = true }.padding(8.dp),
                            )
                        } else {
                            Box(Modifier.size(170.dp).clip(RoundedCornerShape(6.dp)).background(Gx.Input), contentAlignment = Alignment.Center) {
                                Text("No QR image", color = Gx.Muted, fontSize = 12.sp)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("The gym's own QR code, shown to the member when they pay Online. Click it to enlarge.", color = Gx.TextSoft, fontSize = 12.sp)
                            Text("There is no payment integration: staff confirm the payment arrived.", color = Gx.Muted, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (image != null) GxButton("Enlarge QR", onClick = { showQr = true })
                                GxButton(if (image == null) "Choose QR image" else "Replace QR image", onClick = {
                                    scope.launch {
                                        val result = runCatching { services.chooseQrImage() }
                                        val outcome = result.getOrNull()
                                        when {
                                            outcome is Outcome.Ok -> if (outcome.value) {
                                                qrTick++
                                                onMessage("QR image saved.", false)
                                            }
                                            outcome is Outcome.Fail -> onMessage(outcome.message, true)
                                            else -> onMessage(result.exceptionOrNull()?.message ?: "Could not read the image.", true)
                                        }
                                    }
                                })
                                if (image != null) {
                                    GxButton("Remove QR image", onClick = {
                                        scope.launch {
                                            runCatching { services.removeQrImage() }
                                            qrTick++
                                        }
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val bigQr = qr
    if (showQr && bigQr != null) QrPopup(bigQr) { showQr = false }
}