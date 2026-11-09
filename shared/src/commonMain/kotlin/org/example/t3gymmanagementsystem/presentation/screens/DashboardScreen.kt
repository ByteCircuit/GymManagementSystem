package org.example.t3gymmanagementsystem.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.AttentionItem
import org.example.t3gymmanagementsystem.DashboardData
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.ReportBundle
import org.example.t3gymmanagementsystem.ReportPreset
import org.example.t3gymmanagementsystem.changeText
import org.example.t3gymmanagementsystem.presentation.components.CategoryChip
import org.example.t3gymmanagementsystem.presentation.components.ChartSeries
import org.example.t3gymmanagementsystem.presentation.components.Donut
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.LineChart
import org.example.t3gymmanagementsystem.presentation.components.ProductBadge
import org.example.t3gymmanagementsystem.presentation.components.StatCard
import org.example.t3gymmanagementsystem.presentation.components.compactPesos
import org.example.t3gymmanagementsystem.presentation.components.equalHeight
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import org.example.t3gymmanagementsystem.util.Money

private fun grouped(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

private fun percent(part: Int, total: Int): String = if (total == 0) "0%" else "${part * 100 / total}%"

private fun changeColor(text: String): Color = when {
    text.startsWith("+") || text == "New" -> Gx.Green
    text.startsWith("-") -> Gx.Red
    else -> Gx.Muted
}

@Composable
private fun QuickAction(label: String, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.clip(shape)
            .background(if (primary) Gx.Red else Gx.Input)
            .border(1.dp, if (primary) Gx.Red else Gx.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
    ) {
        Text(label, color = Gx.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(
        "$text  \u203A", color = Gx.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Gx.Border))
}

/**
 * Each navigation callback may be null when that page does not exist in the sidebar. The matching button is then left out.
 */
@Composable
fun DashboardScreen(
    services: AppServices,
    onMessage: (String, Boolean) -> Unit,
    onRegister: (() -> Unit)?,
    onPayments: (() -> Unit)?,
    onCheckIn: (() -> Unit)?,
    onSale: (() -> Unit)?,
    onMembers: (() -> Unit)?,
    onAttendance: (() -> Unit)?,
    onInventory: (() -> Unit)?,
    onSettings: (() -> Unit)?,
) {
    var data by remember { mutableStateOf<DashboardData?>(null) }
    var range by remember { mutableStateOf(2) }
    var bundle by remember { mutableStateOf<ReportBundle?>(null) }
    var noBackup by remember { mutableStateOf(false) }
    var revenueH by remember { mutableStateOf(0) }
    var statusH by remember { mutableStateOf(0) }
    var recentH by remember { mutableStateOf(0) }
    var attentionH by remember { mutableStateOf(0) }
    val topH = maxOf(revenueH, statusH)
    val bottomH = maxOf(recentH, attentionH)

    LaunchedEffect(Unit) {
        runCatching { services.dashboard() }
            .onSuccess { data = it }
            .onFailure { onMessage(it.message ?: "Could not load the dashboard.", true) }
        noBackup = runCatching { services.listBackups().isEmpty() }.getOrDefault(false)
    }
    LaunchedEffect(range) {
        val preset = listOf(ReportPreset.Today, ReportPreset.Last7, ReportPreset.Last30)[range]
        val r = runCatching { services.reportRange(preset) }.getOrNull() ?: return@LaunchedEffect
        val outcome = runCatching { services.buildReport(r.from, r.to) }.getOrNull()
        if (outcome is Outcome.Ok) bundle = outcome.value
    }

    val d = data
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GxPanel("Quick actions", headerHeight = 44.dp) {
            Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                onCheckIn?.let { QuickAction("Check in member", true, it) }
                onSale?.let { QuickAction("New sale", false, it) }
                onRegister?.let { QuickAction("Register member", false, it) }
                onPayments?.let { QuickAction("Payments", false, it) }
            }
        }

        if (d == null) {
            Text("Loading...", color = Gx.Muted, fontSize = 12.sp)
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Total members", grouped(d.totalMembers), "${d.newThisMonth} new this month", "users", Gx.Red, Modifier.weight(1f))
            StatCard("Active members", grouped(d.activeMembers), "${percent(d.activeMembers, d.totalMembers)} of total", "users", Gx.Green, Modifier.weight(1f))
            StatCard(
                "Check-ins today", grouped(d.checkInsToday),
                if (d.checkInsLastWeek == 0) "No check-ins last ${d.lastWeekDay}"
                else "${changeText(d.checkInsToday.toLong(), d.checkInsLastWeek.toLong())} vs last ${d.lastWeekDay}",
                "clock", Gx.Amber, Modifier.weight(1f),
            )
            StatCard(
                "Revenue today", Money.format(d.revenueTodayCents),
                if (d.revenueYesterdayCents == 0L) "No sales yesterday"
                else "${changeText(d.revenueTodayCents, d.revenueYesterdayCents)} vs yesterday",
                "card", Gx.Blue, Modifier.weight(1f),
            )
            val needStock = d.lowStock + d.outOfStock
            StatCard(
                "Low stock items", needStock.toString(),
                if (needStock == 0) "All items stocked" else "${d.outOfStock} out of stock",
                "alert", Gx.Amber, Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.55f)) {
                GxPanel("Revenue overview", action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Today", "7D", "30D").forEachIndexed { i, label -> CategoryChip(label, range == i) { range = i } }
                    }
                }) {
                    Column(Modifier.equalHeight(topH) { revenueH = it }.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val b = bundle
                        if (b == null) {
                            Text("Loading...", color = Gx.Muted, fontSize = 12.sp)
                        } else {
                            Text("TOTAL REVENUE", color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(Money.format(b.current.revenueCents), color = Gx.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                val change = changeText(b.current.revenueCents, b.previous.revenueCents)
                                Text(change, color = changeColor(change), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            LineChart(
                                labels = b.daily.map { it.date.substring(5) },
                                series = listOf(
                                    ChartSeries("Membership", b.daily.map { it.membershipCents }, Gx.Red),
                                    ChartSeries("Product sales", b.daily.map { it.productCents }, Gx.Amber, dashed = true),
                                    ChartSeries("Walk-ins", b.daily.map { it.walkInCents }, Gx.Blue),
                                ),
                                formatY = { compactPesos(it) },
                            )
                        }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                GxPanel("Membership status", action = { onMembers?.let { TextLink("View members", it) } }) {
                    Row(
                        Modifier.equalHeight(topH, center = true) { statusH = it }.padding(18.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val activeOnly = d.activeMembers - d.expiringSoon
                        Donut(
                            listOf(Gx.Green to activeOnly, Gx.Amber to d.expiringSoon, Gx.Red to d.expiredMembers, Gx.Blue to d.noMembership),
                            grouped(d.totalMembers), "TOTAL",
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                Triple("Active", Gx.Green, activeOnly),
                                Triple("Expiring soon", Gx.Amber, d.expiringSoon),
                                Triple("Expired", Gx.Red, d.expiredMembers),
                                Triple("No membership", Gx.Blue, d.noMembership),
                            ).forEach { (name, color, count) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                    Column(Modifier.weight(1f)) {
                                        Text(name, color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text(percent(count, d.totalMembers), color = Gx.Faint, fontSize = 10.sp)
                                    }
                                    Text(grouped(count), color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("Expiring soon: ends within 7 days.", color = Gx.Faint, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1.55f)) { // widths match the top row
                GxPanel("Recent check-ins", action = { onAttendance?.let { TextLink("View log", it) } }) {
                    Column(Modifier.equalHeight(bottomH) { recentH = it }.padding(horizontal = 15.dp, vertical = 6.dp)) {
                        if (d.recent.isEmpty()) {
                            Text("No check-ins yet.", color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                        }
                        d.recent.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ProductBadge(r.name, r.name, 34.dp, 11.sp)
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(r.name, color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(r.number, color = Gx.Muted, fontSize = 10.sp)
                                }
                                Text(r.source, color = Gx.TextSoft, fontSize = 10.sp, modifier = Modifier.padding(end = 14.dp))
                                Text(r.timeLabel, color = Gx.Muted, fontSize = 11.sp, modifier = Modifier.width(96.dp))
                            }
                            Divider()
                        }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                GxPanel("Needs attention") {
                    val items = d.attention + (if (noBackup) listOf(AttentionItem("SETTINGS", "No backup yet", "Create one in Settings")) else emptyList())
                    Column(Modifier.equalHeight(bottomH) { attentionH = it }.padding(horizontal = 15.dp, vertical = 6.dp)) {
                        if (items.isEmpty()) {
                            Text("Nothing needs attention right now.", color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                        }
                        items.forEach { item ->
                            val go: (() -> Unit)? = when (item.target) {
                                "MEMBERS" -> onMembers
                                "INVENTORY" -> onInventory
                                "SETTINGS" -> onSettings
                                else -> null
                            }
                            val tone = when (item.target) {
                                "MEMBERS" -> Gx.Red
                                "INVENTORY" -> Gx.Amber
                                else -> Gx.Blue
                            }
                            Row(
                                Modifier.fillMaxWidth().then(if (go != null) Modifier.clickable(onClick = go) else Modifier).padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(tone.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                    Text("!", color = tone, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(item.title, color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(item.detail, color = Gx.Muted, fontSize = 10.sp)
                                }
                                if (go != null) Text("\u203A", color = Gx.Muted, fontSize = 16.sp)
                            }
                            Divider()
                        }
                    }
                }
            }
        }
    }
}