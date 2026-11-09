package org.example.t3gymmanagementsystem.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.InventorySummary
import org.example.t3gymmanagementsystem.MovementRow
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.ProductInput
import org.example.t3gymmanagementsystem.ProductRow
import org.example.t3gymmanagementsystem.StockFilter
import org.example.t3gymmanagementsystem.StockLevel
import org.example.t3gymmanagementsystem.presentation.components.CategoryChip
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxDropdown
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxTabs
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.components.ProductBadge
import org.example.t3gymmanagementsystem.presentation.components.StatCard
import org.example.t3gymmanagementsystem.presentation.components.StatusPill
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import org.example.t3gymmanagementsystem.util.Money

/** Shows an Outcome as a toast and runs onOk when it succeeded. */
internal fun <T> reportOutcome(result: Result<Outcome<T>>, onMessage: (String, Boolean) -> Unit, onOk: (T) -> Unit) {
    val outcome = result.getOrNull()
    when (outcome) {
        is Outcome.Ok -> onOk(outcome.value)
        is Outcome.Fail -> onMessage(outcome.message, true)
        null -> onMessage(result.exceptionOrNull()?.message ?: "Something went wrong.", true)
    }
}

internal fun parsePesoCents(text: String): Long? {
    val value = text.replace(",", "").replace("\u20B1", "").trim().toDoubleOrNull() ?: return null
    if (value.isNaN() || value.isInfinite() || value < 0) return null
    return (value * 100).roundToLong()
}

private sealed interface FormMode {
    object Create : FormMode
    data class Edit(val product: ProductRow) : FormMode
    data class Restock(val product: ProductRow) : FormMode
    data class Adjust(val product: ProductRow) : FormMode
}

@Composable
private fun RowScope.InvTh(text: String, weight: Float) {
    Text(text, color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, modifier = Modifier.weight(weight))
}

@Composable
private fun InvLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Gx.Border))
}

/**
 * Products and stock movements. When the sidebar has no separate Point of sale entry, includePos adds it as a third tab.
 */
@Composable
fun InventoryScreen(services: AppServices, onMessage: (String, Boolean) -> Unit, includePos: Boolean = false) {
    var tab by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }
    var form by remember { mutableStateOf<FormMode?>(null) }
    val tabs = if (includePos) listOf("Products", "Stock movements", "Point of sale") else listOf("Products", "Stock movements")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { GxTabs(tabs, tab, { tab = it }) }
            if (tab == 0) GxButton("Add product", onClick = { form = FormMode.Create })
        }
        when (tab) {
            0 -> ProductsTab(services, onMessage, form, { form = it }, tick) { tick++ }
            1 -> MovementsTab(services, tick)
            else -> PosScreen(services, onMessage)
        }
    }
}

@Composable
private fun ProductsTab(
    services: AppServices,
    onMessage: (String, Boolean) -> Unit,
    form: FormMode?,
    onForm: (FormMode?) -> Unit,
    tick: Int,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var categoryPick by remember { mutableStateOf<String?>(null) }
    var filterIndex by remember { mutableStateOf(0) }
    var products by remember { mutableStateOf<List<ProductRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<InventorySummary?>(null) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    val filters = StockFilter.entries

    LaunchedEffect(tick) {
        summary = runCatching { services.inventorySummary() }.getOrNull()
        categories = runCatching { services.productCategories() }.getOrDefault(emptyList())
    }
    LaunchedEffect(query, categoryPick, filterIndex, tick) {
        delay(150)
        runCatching { services.listProducts(query, categoryPick, filters[filterIndex]) }
            .onSuccess { products = it; loaded = true }
            .onFailure { onMessage(it.message ?: "Could not load products.", true) }
    }

    val categoryOptions = listOf("All categories") + categories
    val categoryIndex = categoryPick?.let { categoryOptions.indexOf(it) }?.takeIf { it >= 0 } ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val s = summary
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total products", s?.totalProducts?.toString() ?: "\u2014", if (s == null) "" else "${s.activeProducts} active", "card", Gx.Blue, Modifier.weight(1f))
            StatCard("Stock units", s?.stockUnits?.toString() ?: "\u2014", "Across active products", "card", Gx.Green, Modifier.weight(1f))
            StatCard("Low stock", s?.lowCount?.toString() ?: "\u2014", "At or below the product's level", "alert", Gx.Amber, Modifier.weight(1f))
            StatCard("Out of stock", s?.outCount?.toString() ?: "\u2014", "Restock required", "alert", Gx.Red, Modifier.weight(1f))
        }

        when (val f = form) {
            FormMode.Create -> key("create") {
                ProductForm(services, null, categories, onMessage, onCancel = { onForm(null) }, onSaved = { onForm(null); onChanged() })
            }
            is FormMode.Edit -> key("edit-${f.product.id}") {
                ProductForm(services, f.product, categories, onMessage, onCancel = { onForm(null) }, onSaved = { onForm(null); onChanged() })
            }
            is FormMode.Restock -> key("restock-${f.product.id}") {
                StockForm(services, f.product, adjust = false, onMessage = onMessage, onCancel = { onForm(null) }, onSaved = { onForm(null); onChanged() })
            }
            is FormMode.Adjust -> key("adjust-${f.product.id}") {
                StockForm(services, f.product, adjust = true, onMessage = onMessage, onCancel = { onForm(null) }, onSaved = { onForm(null); onChanged() })
            }
            null -> {}
        }

        GxPanel("Product directory", action = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                GxDropdown(categoryOptions, categoryIndex, { categoryPick = if (it == 0) null else categoryOptions[it] }, Modifier.width(170.dp))
                GxDropdown(filters.map { it.label }, filterIndex, { filterIndex = it }, Modifier.width(170.dp))
            }
        }) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GxTextField("Search", query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Product name or SKU")
                InvLine()
                if (products.isEmpty()) {
                    Text(
                        when {
                            !loaded -> "Loading..."
                            query.isBlank() && categoryPick == null && filterIndex == 0 -> "No products yet. Use Add product to create the first one."
                            else -> "No products match."
                        },
                        color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp),
                    )
                }
                products.forEach { p ->
                    ProductLine(
                        p,
                        onEdit = { onForm(FormMode.Edit(p)) },
                        onRestock = { onForm(FormMode.Restock(p)) },
                        onAdjust = { onForm(FormMode.Adjust(p)) },
                        onArchive = {
                            scope.launch {
                                reportOutcome(runCatching { services.setProductActive(p.id, !p.active) }, onMessage) {
                                    onMessage(if (p.active) "${p.name} archived." else "${p.name} restored.", false)
                                    onChanged()
                                }
                            }
                        },
                    )
                    InvLine()
                }
            }
        }
    }
}

@Composable
private fun ProductLine(p: ProductRow, onEdit: () -> Unit, onRestock: () -> Unit, onAdjust: () -> Unit, onArchive: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        ProductBadge(p.name, p.category, 44.dp, 13.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(2.2f)) {
            Text(p.name, color = Gx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(p.sku, color = Gx.Muted, fontSize = 11.sp)
        }
        Text(p.category, color = Gx.TextSoft, fontSize = 12.sp, modifier = Modifier.weight(1.4f))
        Column(Modifier.weight(1.3f)) {
            Text("SELLING PRICE", color = Gx.Faint, fontSize = 9.sp, letterSpacing = 0.6.sp)
            Text(Money.format(p.priceCents), color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1.3f)) {
            Text("AVAILABLE STOCK", color = Gx.Faint, fontSize = 9.sp, letterSpacing = 0.6.sp)
            Text("${p.stock} units", color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(Modifier.weight(1.2f)) {
            when {
                !p.active -> StatusPill("Archived", Gx.Muted)
                p.level == StockLevel.Out -> StatusPill(p.level.label, Gx.Red)
                p.level == StockLevel.Low -> StatusPill(p.level.label, Gx.Amber)
                else -> StatusPill(p.level.label, Gx.Green)
            }
        }
        Box(Modifier.width(40.dp)) {
            Text("...", color = Gx.Muted, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { menu = true }.padding(8.dp))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit product", fontSize = 12.sp) }, onClick = { menu = false; onEdit() })
                if (p.active) {
                    DropdownMenuItem(text = { Text("Restock", fontSize = 12.sp) }, onClick = { menu = false; onRestock() })
                    DropdownMenuItem(text = { Text("Adjust count", fontSize = 12.sp) }, onClick = { menu = false; onAdjust() })
                }
                DropdownMenuItem(text = { Text(if (p.active) "Archive product" else "Restore product", fontSize = 12.sp) }, onClick = { menu = false; onArchive() })
            }
        }
    }
}

@Composable
private fun ProductForm(
    services: AppServices,
    existing: ProductRow?,
    categories: List<String>,
    onMessage: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var sku by remember { mutableStateOf(existing?.sku ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var price by remember { mutableStateOf(existing?.let { Money.plainInput(it.priceCents) } ?: "") }
    var low by remember { mutableStateOf(existing?.lowLevel?.toString() ?: "") }
    var opening by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    GxPanel(if (existing == null) "Add product" else "Edit ${existing.name}") {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GxTextField("Product name", name, { name = it }, Modifier.weight(2f), placeholder = "e.g. Training Gloves")
                GxTextField("SKU", sku, { sku = it }, Modifier.weight(1f), placeholder = "e.g. GLO-001")
            }
            GxTextField("Category", category, { category = it }, Modifier.fillMaxWidth(), placeholder = "Type a category or pick one below")
            if (categories.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.take(8).forEach { CategoryChip(it, category.trim().equals(it, ignoreCase = true)) { category = it } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GxTextField("Selling price (PHP)", price, { price = it }, Modifier.weight(1f), placeholder = "0.00")
                GxTextField("Low-stock level (units)", low, { low = it }, Modifier.weight(1f), placeholder = "Warn at or below")
                if (existing == null) {
                    GxTextField("Opening stock (units)", opening, { opening = it }, Modifier.weight(1f), placeholder = "0")
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (existing != null) {
                Text("To change the stock, use Restock or Adjust count so the movement is recorded.", color = Gx.Muted, fontSize = 11.sp)
            }
            error?.let { Text(it, color = Color(0xFFF06A66), fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GxButton(if (existing == null) "Add product" else "Save changes", onClick = {
                    if (busy) return@GxButton
                    val cents = parsePesoCents(price)
                    val lowLevel = low.trim().toIntOrNull()
                    val openingStock = if (existing == null) (opening.trim().ifEmpty { "0" }).toIntOrNull() else 0
                    when {
                        cents == null || cents <= 0 -> error = "Enter the selling price as a number, for example 850 or 850.50."
                        lowLevel == null || lowLevel < 0 -> error = "Enter the low-stock level as a whole number."
                        openingStock == null || openingStock < 0 -> error = "Enter the opening stock as a whole number."
                        else -> {
                            error = null
                            busy = true
                            scope.launch {
                                val result = runCatching {
                                    services.saveProduct(ProductInput(existing?.id, name, sku, category, cents, lowLevel, openingStock))
                                }
                                busy = false
                                val outcome = result.getOrNull()
                                if (outcome is Outcome.Fail) error = outcome.message
                                reportOutcome(result, { m, _ -> if (outcome !is Outcome.Fail) onMessage(m, true) }) {
                                    onMessage(if (existing == null) "Product added." else "Product saved.", false)
                                    onSaved()
                                }
                            }
                        }
                    }
                })
                GxButton("Cancel", onClick = onCancel)
            }
        }
    }
}

@Composable
private fun StockForm(
    services: AppServices,
    product: ProductRow,
    adjust: Boolean,
    onMessage: (String, Boolean) -> Unit,
    onCancel: () -> Unit,
    onSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    GxPanel(if (adjust) "Adjust count: ${product.name}" else "Restock: ${product.name}") {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Current stock: ${product.stock} units", color = Gx.TextSoft, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GxTextField(
                    if (adjust) "Counted stock (units)" else "Units received", amount, { amount = it }, Modifier.weight(1f),
                    placeholder = if (adjust) "What is on the shelf now" else "e.g. 12",
                )
                GxTextField(
                    if (adjust) "Reason (required)" else "Note (optional)", note, { note = it }, Modifier.weight(2f),
                    placeholder = if (adjust) "e.g. recount, damaged items" else "e.g. supplier delivery",
                )
            }
            error?.let { Text(it, color = Color(0xFFF06A66), fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GxButton(if (adjust) "Save count" else "Add stock", onClick = {
                    if (busy) return@GxButton
                    val n = amount.trim().toIntOrNull()
                    if (n == null) {
                        error = "Enter a whole number."
                        return@GxButton
                    }
                    error = null
                    busy = true
                    scope.launch {
                        val result = runCatching {
                            if (adjust) services.adjustStock(product.id, n, note) else services.restockProduct(product.id, n, note)
                        }
                        busy = false
                        val outcome = result.getOrNull()
                        if (outcome is Outcome.Fail) error = outcome.message
                        reportOutcome(result, { m, _ -> if (outcome !is Outcome.Fail) onMessage(m, true) }) {
                            onMessage(if (adjust) "Stock count saved." else "Stock added.", false)
                            onSaved()
                        }
                    }
                })
                GxButton("Cancel", onClick = onCancel)
            }
        }
    }
}

@Composable
private fun MovementsTab(services: AppServices, tick: Int) {
    var filter by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<List<MovementRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(tick) {
        rows = runCatching { services.stockMovements() }.getOrDefault(emptyList())
        loaded = true
    }
    val shown = when (filter) {
        1 -> rows.filter { it.kindLabel == "Restock" }
        2 -> rows.filter { it.kindLabel == "Sale" }
        3 -> rows.filter { it.kindLabel == "Voided sale" }
        else -> rows
    }
    GxPanel("Stock movements", action = {
        GxDropdown(listOf("All movements", "Restocks", "Sales", "Voided sales"), filter, { filter = it }, Modifier.width(190.dp))
    }) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                InvTh("DATE & TIME", 1.5f)
                InvTh("PRODUCT", 2f)
                InvTh("TYPE", 1.2f)
                InvTh("CHANGE", 0.8f)
                InvTh("STOCK AFTER", 1f)
                InvTh("NOTE", 2f)
            }
            InvLine()
            if (shown.isEmpty()) {
                Text(if (!loaded) "Loading..." else if (rows.isEmpty()) "No stock movements yet." else "No movements match.", color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
            shown.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.timeLabel, color = Gx.TextSoft, fontSize = 12.sp, modifier = Modifier.weight(1.5f))
                    Column(Modifier.weight(2f)) {
                        Text(r.productName, color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(r.sku, color = Gx.Muted, fontSize = 10.sp)
                    }
                    Text(r.kindLabel, color = Gx.TextSoft, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                    Text(
                        (if (r.change > 0) "+" else "") + r.change,
                        color = if (r.change > 0) Gx.Green else Gx.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.8f),
                    )
                    Text("${r.stockAfter} units", color = Gx.TextSoft, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(r.note.ifEmpty { "\u2014" }, color = Gx.Muted, fontSize = 11.sp, modifier = Modifier.weight(2f))
                }
                InvLine()
            }
        }
    }
}