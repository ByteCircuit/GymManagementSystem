package org.example.t3gymmanagementsystem.presentation.screens
import androidx.compose.foundation.BorderStroke
import org.example.t3gymmanagementsystem.Outcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.CartLine
import org.example.t3gymmanagementsystem.MemberRow
import org.example.t3gymmanagementsystem.PaymentInfo
import org.example.t3gymmanagementsystem.ReaderEvent
import org.example.t3gymmanagementsystem.PaymentMethod
import org.example.t3gymmanagementsystem.ProductRow
import org.example.t3gymmanagementsystem.ProductSaleRow
import org.example.t3gymmanagementsystem.StockFilter
import org.example.t3gymmanagementsystem.StockLevel
import org.example.t3gymmanagementsystem.presentation.components.CategoryChip
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxSegmented
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.components.NfcHint
import org.example.t3gymmanagementsystem.presentation.components.PaymentChooser
import org.example.t3gymmanagementsystem.presentation.components.ProductBadge
import org.example.t3gymmanagementsystem.presentation.components.productTone
import org.example.t3gymmanagementsystem.presentation.components.productInitials
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import org.example.t3gymmanagementsystem.util.Money

@Composable
private fun RowScope.PosTh(text: String, weight: Float) {
    Text(text, color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, modifier = Modifier.weight(weight))
}

@Composable
private fun PosLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Gx.Border))
}

@Composable
fun PosScreen(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var products by remember { mutableStateOf<List<ProductRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf<List<ProductSaleRow>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    val cart = remember { mutableStateMapOf<Long, Int>() }
    var customerMode by remember { mutableStateOf(0) }
    var memberQuery by remember { mutableStateOf("") }
    var memberResults by remember { mutableStateOf<List<MemberRow>>(emptyList()) }
    var member by remember { mutableStateOf<MemberRow?>(null) }
    var methodIndex by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var voidRef by remember { mutableStateOf<String?>(null) }
    var voidReason by remember { mutableStateOf("") }
    var voidBusy by remember { mutableStateOf(false) }
    val voidScope = rememberCoroutineScope()

    LaunchedEffect(tick) {
        products = runCatching { services.listProducts("", null, StockFilter.All) }.getOrDefault(emptyList())
        recent = runCatching { services.recentProductSales() }.getOrDefault(emptyList())
        loaded = true
        // drop cart lines for products that are gone or no longer have stock
        cart.keys.toList().forEach { id ->
            val p = products.firstOrNull { it.id == id }
            if (p == null || p.stock == 0) cart.remove(id) else if ((cart[id] ?: 0) > p.stock) cart[id] = p.stock
        }
    }
    // a member card tapped on the reader selects that member for this sale
    LaunchedEffect(Unit) {
        runCatching {
            services.cardEvents().collect { event ->
                if (event is ReaderEvent.Scan) {
                    val found = runCatching { services.memberByCard(event.uid) }.getOrNull()
                    if (found == null) {
                        onMessage("That card is not assigned to a member.", true)
                    } else {
                        customerMode = 1
                        member = found
                        memberQuery = ""
                        memberResults = emptyList()
                        onMessage("${found.name} selected.", false)
                    }
                }
            }
        }
    }
    LaunchedEffect(memberQuery, customerMode) {
        if (customerMode == 1 && memberQuery.isNotBlank()) {
            delay(200)
            memberResults = runCatching { services.searchMembers(memberQuery) }.getOrDefault(emptyList())
        } else {
            memberResults = emptyList()
        }
    }

    val categories = products.map { it.category }.distinct().sortedBy { it.lowercase() }
    val visible = products.filter {
        (category == null || it.category == category) &&
            (search.isBlank() || it.name.contains(search, ignoreCase = true) || it.sku.contains(search, ignoreCase = true))
    }
    val cartLines = cart.toMap().mapNotNull { (id, qty) -> products.firstOrNull { it.id == id }?.let { it to qty } }
    val total = cartLines.sumOf { (p, q) -> p.priceCents * q }

    fun add(p: ProductRow) {
        val current = cart[p.id] ?: 0
        if (current < p.stock) cart[p.id] = current + 1 else onMessage("Only ${p.stock} of ${p.name} in stock.", true)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        // ---------------------------------------------------------------- catalogue
        Column(Modifier.weight(1.7f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GxTextField("Search", search, { search = it }, Modifier.fillMaxWidth(), placeholder = "Search product name or SKU")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryChip("All", category == null) { category = null }
                categories.forEach { c -> CategoryChip(c, category == c) { category = c } }
            }
            if (visible.isEmpty()) {
                Text(
                    if (!loaded) "Loading..." else if (products.isEmpty()) "No products yet. Add them on the Products page." else "No products match.",
                    color = Gx.Muted, fontSize = 12.sp,
                )
            }
            visible.chunked(3).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { p -> ProductTile(p, Modifier.weight(1f)) { add(p) } }
                    repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            GxPanel("Recent product purchases") {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val toVoid = voidRef
                    if (toVoid != null) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Gx.Surface2)
                                .border(BorderStroke(1.dp, Gx.Border), RoundedCornerShape(7.dp)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("Void sale $toVoid", color = Gx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("The items go back into stock and the sale stops counting in payments and reports. The record is kept.", color = Gx.Muted, fontSize = 11.sp)
                            GxTextField("Reason", voidReason, { voidReason = it }, Modifier.fillMaxWidth(), placeholder = "e.g. Wrong item rung up")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GxButton("Confirm void", onClick = {
                                    voidBusy = true
                                    voidScope.launch {
                                        val r = runCatching { services.voidProductSale(toVoid, voidReason) }.getOrNull()
                                        voidBusy = false
                                        if (r is Outcome.Ok) {
                                            onMessage("Sale $toVoid voided. Stock returned.", false)
                                            voidRef = null
                                            voidReason = ""
                                            products = runCatching { services.listProducts("", null, StockFilter.All) }.getOrDefault(emptyList())
                                            recent = runCatching { services.recentProductSales() }.getOrDefault(emptyList())
                                        } else if (r is Outcome.Fail) {
                                            onMessage(r.message, true)
                                        } else {
                                            onMessage("The sale could not be voided.", true)
                                        }
                                    }
                                }, enabled = !voidBusy)
                                GxButton("Cancel", onClick = { voidRef = null; voidReason = "" })
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                        PosTh("TRANSACTION", 1.5f)
                        PosTh("MEMBER / CUSTOMER", 1.6f)
                        PosTh("ITEMS PURCHASED", 2.4f)
                        PosTh("TOTAL", 1f)
                        PosTh("TIME", 1.3f)
                        PosTh("", 0.8f)
                    }
                    PosLine()
                    if (recent.isEmpty()) {
                        Text(if (!loaded) "Loading..." else "No product sales yet.", color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                    }
                    recent.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.reference, color = Gx.TextSoft, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.weight(1.5f))
                            Column(Modifier.weight(1.6f)) {
                                Text(r.customer, color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                r.customerNumber?.let { Text(it, color = Gx.Muted, fontSize = 10.sp) }
                            }
                            Text(r.items, color = Gx.TextSoft, fontSize = 11.sp, modifier = Modifier.weight(2.4f))
                            Text(Money.format(r.totalCents), color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text(r.timeLabel, color = Gx.Muted, fontSize = 11.sp, modifier = Modifier.weight(1.3f))
                            Text("Void", color = Gx.Red, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.8f).clickable { voidRef = r.reference; voidReason = "" })
                        }
                        PosLine()
                    }
                }
            }
        }

        // ---------------------------------------------------------------- current sale
        Column(Modifier.weight(1f)) {
            GxPanel("Current sale") {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("MEMBER / CUSTOMER", color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, modifier = Modifier.weight(1f))
                        NfcHint("Tap a member card")
                    }
                    GxSegmented(listOf("Walk-in customer", "Member"), customerMode, {
                        customerMode = it
                        if (it == 0) { member = null; memberQuery = "" }
                    }, Modifier.fillMaxWidth())
                    if (customerMode == 1) {
                        val chosen = member
                        if (chosen != null) {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Gx.Input).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${chosen.number} - ${chosen.name}", color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${chosen.status.label} - ${chosen.membershipLabel}", color = Gx.Muted, fontSize = 11.sp)
                                }
                                Text("Change", color = Gx.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { member = null })
                            }
                        } else {
                            GxTextField("Find member", memberQuery, { memberQuery = it }, Modifier.fillMaxWidth(), placeholder = "Name or member number")
                            if (memberQuery.isBlank()) {
                                Text("Type a name or member number.", color = Gx.Muted, fontSize = 11.sp)
                            } else if (memberResults.isEmpty()) {
                                Text("No member found.", color = Gx.Muted, fontSize = 11.sp)
                            }
                            memberResults.take(5).forEach { m ->
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).clickable { member = m; memberResults = emptyList() }.padding(8.dp),
                                ) {
                                    Text("${m.number} - ${m.name}", color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${m.status.label} - ${m.membershipLabel}", color = Gx.Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    PosLine()

                    if (cartLines.isEmpty()) {
                        Text("Tap a product to add it to the sale.", color = Gx.Muted, fontSize = 12.sp)
                    }
                    cartLines.forEach { (p, q) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProductBadge(p.name, p.category, 36.dp, 11.sp)
                            Column(Modifier.weight(1f)) {
                                Text(p.name, color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("${Money.format(p.priceCents)} each", color = Gx.Muted, fontSize = 10.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QtyButton("-") { if (q <= 1) cart.remove(p.id) else cart[p.id] = q - 1 }
                                    Text("$q", color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    QtyButton("+") { add(p) }
                                    Text("Remove", color = Gx.Red, fontSize = 11.sp, modifier = Modifier.clickable { cart.remove(p.id) }.padding(start = 6.dp))
                                }
                            }
                            Text(Money.format(p.priceCents * q), color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    PosLine()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Total amount", color = Gx.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(Money.format(total), color = Gx.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }

                    PaymentChooser(services, methodIndex, { methodIndex = it }, note, { note = it })

                    GxButton("Complete sale  -  ${Money.format(total)}", modifier = Modifier.fillMaxWidth(), onClick = {
                        if (busy) return@GxButton
                        if (cartLines.isEmpty()) {
                            onMessage("Add at least one product to the sale.", true)
                            return@GxButton
                        }
                        if (customerMode == 1 && member == null) {
                            onMessage("Choose a member, or switch to Walk-in customer.", true)
                            return@GxButton
                        }
                        busy = true
                        val lines = cartLines.map { (p, q) -> CartLine(p.id, q) }
                        val buyer = if (customerMode == 1) member?.id else null
                        val payment = PaymentInfo(method = PaymentMethod.entries[methodIndex], note = note)
                        scope.launch {
                            val result = runCatching { services.sellProducts(buyer, lines, payment) }
                            busy = false
                            reportOutcome(result, onMessage) { sale ->
                                onMessage("Sale ${sale.reference} recorded: ${Money.format(sale.totalCents)}. The receipt is on the Payments page.", false)
                                cart.clear()
                                note = ""
                                member = null
                                memberQuery = ""
                                customerMode = 0
                            }
                            tick++
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun QtyButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.width(24.dp).height(24.dp).clip(RoundedCornerShape(5.dp)).background(Gx.Input)
            .border(1.dp, Gx.Border, RoundedCornerShape(5.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Gx.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProductTile(p: ProductRow, modifier: Modifier, onClick: () -> Unit) {
    val tone = productTone(p.category)
    val out = p.stock == 0
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier.height(176.dp).clip(shape).background(Gx.Input).border(1.dp, Gx.Border, shape)
            .then(if (out) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        Box(
            Modifier.fillMaxWidth().height(92.dp).background(tone.copy(alpha = if (out) 0.07f else 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(productInitials(p.name), color = if (out) Gx.Muted else tone, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${p.category} - ${p.sku}", color = Gx.Faint, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.name, color = if (out) Gx.Muted else Gx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(Money.format(p.priceCents), color = if (out) Gx.Muted else Gx.Text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (out) "Out of stock" else "${p.stock} in stock",
                    color = when (p.level) {
                        StockLevel.Out -> Gx.Red
                        StockLevel.Low -> Gx.Amber
                        StockLevel.InStock -> Gx.Muted
                    },
                    fontSize = 10.sp,
                )
            }
        }
    }
}