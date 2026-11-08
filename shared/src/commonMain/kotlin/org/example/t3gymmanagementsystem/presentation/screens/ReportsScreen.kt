package org.example.t3gymmanagementsystem.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.ExportFormat
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.REPORT_TABS
import org.example.t3gymmanagementsystem.ReportBundle
import org.example.t3gymmanagementsystem.ReportPreset
import org.example.t3gymmanagementsystem.changeHint
import org.example.t3gymmanagementsystem.document
import org.example.t3gymmanagementsystem.perDay
import org.example.t3gymmanagementsystem.performanceRows
import org.example.t3gymmanagementsystem.presentation.components.CategoryChip
import org.example.t3gymmanagementsystem.presentation.components.ChartSeries
import org.example.t3gymmanagementsystem.presentation.components.Donut
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxTabs
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.components.LineChart
import org.example.t3gymmanagementsystem.presentation.components.StatCard
import org.example.t3gymmanagementsystem.presentation.components.compactPesos
import org.example.t3gymmanagementsystem.presentation.components.equalHeight
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import org.example.t3gymmanagementsystem.util.Money

@Composable
fun ReportsScreen(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var bundle by remember { mutableStateOf<ReportBundle?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val range = runCatching { services.reportRange(ReportPreset.Last30) }.getOrNull()
        if (range != null) {
            from = range.from
            to = range.to
        }
    }
    LaunchedEffect(from, to) {
        if (from.isBlank() || to.isBlank()) return@LaunchedEffect
        delay(400)
        val result = runCatching { services.buildReport(from, to) }
        val outcome = result.getOrNull()
        when (outcome) {
            is Outcome.Ok -> {
                bundle = outcome.value
                error = null
            }
            is Outcome.Fail -> error = outcome.message
            null -> error = result.exceptionOrNull()?.message ?: "Could not build the report."
        }
    }

    fun export(format: ExportFormat) {
        val b = bundle
        if (b == null) {
            onMessage("The report is still loading.", true)
            return
        }
        val document = b.document(tab)
        scope.launch {
            reportOutcome(runCatching { services.exportReport(document, format) }, onMessage) { path ->
                if (path != null) onMessage("Report saved to $path", false)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { // aligned with the 40dp controls
            Box(Modifier.weight(1f)) { GxTabs(REPORT_TABS, tab, { tab = it }) }
            DateField("From", from) { from = it }
            DateField("To", to) { to = it }
            Box {
                GxButton("Export report", modifier = Modifier.width(160.dp).height(44.dp), onClick = { menu = true })
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Export as CSV (opens in Excel)", fontSize = 12.sp) }, onClick = { menu = false; export(ExportFormat.Csv) })
                    DropdownMenuItem(text = { Text("Export as PDF", fontSize = 12.sp) }, onClick = { menu = false; export(ExportFormat.Pdf) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportPreset.entries.forEach { preset ->
                CategoryChip(preset.label, false) {
                    scope.launch {
                        val range = runCatching { services.reportRange(preset) }.getOrNull()
                        if (range != null) {
                            from = range.from
                            to = range.to
                        }
                    }
                }
            }
        }
        error?.let { Text(it, color = Color(0xFFF06A66), fontSize = 12.sp) }

        val b = bundle
        if (b == null) {
            Text(if (error == null) "Loading..." else "No report to show.", color = Gx.Muted, fontSize = 12.sp)
        } else {
            Text("Showing ${b.from} to ${b.to}, compared with ${b.previousFrom} to ${b.previousTo}.", color = Gx.Muted, fontSize = 11.sp)
            when (tab) {
                0 -> OverviewTab(b)
                1 -> MembershipTab(b)
                2 -> AttendanceTab(b)
                3 -> SalesTab(b)
                else -> InventoryTab(b)
            }
        }
    }
}

@Composable
private fun ReportTable(
    headers: List<String>,
    rows: List<List<String>>,
    weights: List<Float>,
    emptyText: String,
    tint: (Int, Int, String) -> Color? = { _, _, _ -> null },
) {
    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
            headers.forEachIndexed { i, h ->
                Text(h.uppercase(), color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, modifier = Modifier.weight(weights[i]))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Gx.Border))
        if (rows.isEmpty()) {
            Text(emptyText, color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
        }
        rows.forEachIndexed { r, cells ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                cells.forEachIndexed { i, t ->
                    Text(
                        t, color = tint(r, i, t) ?: if (i == 0) Gx.Text else Gx.TextSoft, fontSize = 12.sp,
                        fontWeight = if (i == 0) FontWeight.Medium else FontWeight.Normal,
                        modifier = Modifier.weight(weights[i]),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Gx.Border))
        }
    }
}

@Composable
private fun StatusDonut(b: ReportBundle, modifier: Modifier = Modifier) {
    Row(modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Donut(listOf(Gx.Green to b.members.active, Gx.Amber to b.members.expired, Gx.Blue to b.members.none), b.members.total.toString(), "MEMBERS")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Triple("Active", Gx.Green, b.members.active),
                Triple("Expired", Gx.Amber, b.members.expired),
                Triple("No membership", Gx.Blue, b.members.none),
            ).forEach { (name, color, count) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                    Text(name, color = Gx.TextSoft, fontSize = 12.sp, modifier = Modifier.width(100.dp))
                    Text(count.toString(), color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("As of today", color = Gx.Faint, fontSize = 10.sp)
        }
    }
}

@Composable
private fun RevenueChart(b: ReportBundle, modifier: Modifier = Modifier) {
    Column(modifier.padding(15.dp)) {
        LineChart(
            labels = b.daily.map { it.date.substring(5) },
            series = listOf(
                ChartSeries("Membership", b.daily.map { it.membershipCents }, Gx.Red),
                ChartSeries("Products", b.daily.map { it.productCents }, Gx.Amber, dashed = true),
                ChartSeries("Walk-ins", b.daily.map { it.walkInCents }, Gx.Blue),
            ),
            formatY = { compactPesos(it) },
            height = 160.dp,
        )
    }
}

@Composable
private fun OverviewTab(b: ReportBundle) {
    val cur = b.current
    val prev = b.previous
    val performance = b.performanceRows()
    var chartH by remember { mutableStateOf(0) }
    var donutH by remember { mutableStateOf(0) }
    val equalH = maxOf(chartH, donutH)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total revenue", Money.format(cur.revenueCents), changeHint(cur.revenueCents, prev.revenueCents), "card", Gx.Green, Modifier.weight(1f))
            StatCard("New members", cur.newMembers.toString(), changeHint(cur.newMembers.toLong(), prev.newMembers.toLong()), "users", Gx.Blue, Modifier.weight(1f))
            StatCard("Total check-ins", cur.checkIns.toString(), "${perDay(cur.checkIns, b.days)} daily average", "clock", Gx.Amber, Modifier.weight(1f))
            StatCard("Product units sold", cur.unitsSold.toString(), "${Money.format(cur.productCents)} revenue", "card", Gx.Red, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.7f)) { GxPanel("Revenue by source") { RevenueChart(b, Modifier.equalHeight(equalH) { chartH = it }) } }
            Column(Modifier.weight(1f)) { GxPanel("Members by status") { StatusDonut(b, Modifier.equalHeight(equalH, center = true) { donutH = it }) } }
        }
        GxPanel("Performance summary") {
            ReportTable(
                listOf("Metric", "Current period", "Previous period", "Change", "Trend"),
                performance.map { listOf(it.metric, it.current, it.previous, it.change, it.trend) },
                listOf(2f, 1.4f, 1.4f, 1f, 1f),
                "No data.",
            ) { r, i, _ ->
                if (i < 3) null else when (performance[r].trend) {
                    "Growing" -> Gx.Green
                    "Declining" -> Gx.Red
                    else -> Gx.Muted
                }
            }
        }
    }
}

@Composable
private fun MembershipTab(b: ReportBundle) {
    val cur = b.current
    val prev = b.previous
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("New members", cur.newMembers.toString(), changeHint(cur.newMembers.toLong(), prev.newMembers.toLong()), "users", Gx.Blue, Modifier.weight(1f))
            StatCard("Renewals", cur.renewals.toString(), changeHint(cur.renewals.toLong(), prev.renewals.toLong()), "users", Gx.Green, Modifier.weight(1f))
            StatCard("Active members", b.members.active.toString(), "As of today", "users", Gx.Green, Modifier.weight(1f))
            StatCard("Expired members", b.members.expired.toString(), "${b.members.none} with no membership", "alert", Gx.Amber, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.7f)) {
                GxPanel("Plan breakdown") {
                    ReportTable(
                        listOf("Plan", "New", "Renewals", "Revenue"),
                        b.plans.map { listOf(it.plan, it.newCount.toString(), it.renewals.toString(), Money.format(it.revenueCents)) },
                        listOf(2f, 1f, 1f, 1.4f),
                        "No memberships sold in this period.",
                    )
                }
            }
            Column(Modifier.weight(1f)) { GxPanel("Members by status") { StatusDonut(b) } }
        }
    }
}

@Composable
private fun AttendanceTab(b: ReportBundle) {
    val cur = b.current
    val prev = b.previous
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Check-ins", cur.checkIns.toString(), changeHint(cur.checkIns.toLong(), prev.checkIns.toLong()), "clock", Gx.Amber, Modifier.weight(1f))
            StatCard("Different members", b.distinctMembers.toString(), "${perDay(cur.checkIns, b.days)} check-ins per day", "users", Gx.Blue, Modifier.weight(1f))
            StatCard("Walk-ins", cur.walkIns.toString(), changeHint(cur.walkIns.toLong(), prev.walkIns.toLong()), "users", Gx.Green, Modifier.weight(1f))
            StatCard("Peak hour", b.peakHour ?: "\u2014", "${b.denied} denied card scans", "alert", Gx.Red, Modifier.weight(1f))
        }
        GxPanel("Daily attendance") {
            Column(Modifier.padding(15.dp)) {
                LineChart(
                    labels = b.daily.map { it.date.substring(5) },
                    series = listOf(
                        ChartSeries("Check-ins", b.daily.map { it.checkIns.toLong() }, Gx.Amber),
                        ChartSeries("Walk-ins", b.daily.map { it.walkIns.toLong() }, Gx.Blue, dashed = true),
                    ),
                    formatY = { it.toString() },
                )
            }
        }
        GxPanel("Attendance by day") {
            ReportTable(
                listOf("Date", "Check-ins", "Walk-ins"),
                b.daily.reversed().take(62).map { listOf(it.date, it.checkIns.toString(), it.walkIns.toString()) },
                listOf(2f, 1f, 1f),
                "No data.",
            )
        }
        if (b.daily.size > 62) Text("The table shows the latest 62 days. Export the report to get every day.", color = Gx.Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SalesTab(b: ReportBundle) {
    val cur = b.current
    val prev = b.previous
    val average = if (cur.transactions == 0) 0L else cur.revenueCents / cur.transactions
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total revenue", Money.format(cur.revenueCents), changeHint(cur.revenueCents, prev.revenueCents), "card", Gx.Green, Modifier.weight(1f))
            StatCard("Transactions", cur.transactions.toString(), changeHint(cur.transactions.toLong(), prev.transactions.toLong()), "card", Gx.Blue, Modifier.weight(1f))
            StatCard("Average sale", Money.format(average), "Revenue per transaction", "card", Gx.Amber, Modifier.weight(1f))
            StatCard("Discounts given", Money.format(cur.discountCents), "Student discounts", "alert", Gx.Red, Modifier.weight(1f))
        }
        GxPanel("Revenue by source") { RevenueChart(b) }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                GxPanel("Revenue by payment method") {
                    ReportTable(
                        listOf("Method", "Payments", "Amount"),
                        b.methods.map { listOf(it.method, it.count.toString(), Money.format(it.cents)) },
                        listOf(1.4f, 1f, 1.4f), "No payments in this period.",
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                GxPanel("Revenue by type") {
                    ReportTable(
                        listOf("Type", "Transactions", "Amount"),
                        b.types.map { listOf(it.label, it.count.toString(), Money.format(it.cents)) },
                        listOf(1.6f, 1.1f, 1.4f), "No sales in this period.",
                    )
                }
            }
        }
    }
}

@Composable
private fun InventoryTab(b: ReportBundle) {
    val cur = b.current
    val prev = b.previous
    val out = b.stockAlerts.count { it.stock == 0 }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Units sold", cur.unitsSold.toString(), changeHint(cur.unitsSold.toLong(), prev.unitsSold.toLong()), "card", Gx.Red, Modifier.weight(1f))
            StatCard("Product revenue", Money.format(cur.productCents), changeHint(cur.productCents, prev.productCents), "card", Gx.Green, Modifier.weight(1f))
            StatCard("Low stock now", (b.stockAlerts.size - out).toString(), "At or below the product's level", "alert", Gx.Amber, Modifier.weight(1f))
            StatCard("Out of stock now", out.toString(), "Restock required", "alert", Gx.Red, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.4f)) {
                GxPanel("Top selling products") {
                    ReportTable(
                        listOf("Product", "SKU", "Units", "Revenue"),
                        b.topProducts.map { listOf(it.name, it.sku, it.units.toString(), Money.format(it.cents)) },
                        listOf(2f, 1.2f, 0.8f, 1.3f), "No product sales in this period.",
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                GxPanel("Needs restocking") {
                    ReportTable(
                        listOf("Product", "Stock", "Level"),
                        b.stockAlerts.map { listOf(it.name, it.stock.toString(), it.lowLevel.toString()) },
                        listOf(2f, 0.8f, 0.8f), "Nothing is low on stock.",
                    ) { r, i, _ -> if (i == 1 && b.stockAlerts[r].stock == 0) Gx.Red else null }
                }
            }
        }
    }
}
/** A compact date box, 40 dp tall like the Export button. */
@Composable
private fun DateField(label: String, value: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        Modifier.width(190.dp).height(44.dp).clip(shape).background(Gx.Input).border(1.dp, Gx.Border, shape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label.uppercase(), color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("YYYY-MM-DD", color = Gx.Faint, fontSize = 13.sp)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = TextStyle(color = Gx.Text, fontSize = 13.sp),
                cursorBrush = SolidColor(Gx.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}