package org.example.t3gymmanagementsystem.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.DiscountType
import org.example.t3gymmanagementsystem.MembershipKind
import org.example.t3gymmanagementsystem.MembershipStats
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.Pricing
import org.example.t3gymmanagementsystem.RenewalRow
import org.example.t3gymmanagementsystem.StudentDiscount
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxDropdown
import org.example.t3gymmanagementsystem.presentation.components.GxIcon
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxSegmented
import org.example.t3gymmanagementsystem.presentation.components.GxSwitch
import org.example.t3gymmanagementsystem.presentation.components.GxTabs
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.components.StatCard
import org.example.t3gymmanagementsystem.presentation.components.StatusPill
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import org.example.t3gymmanagementsystem.util.Money

private val ErrorText = Color(0xFFF06A66)

@Composable
fun MembershipsScreen(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GxTabs(listOf("Membership plans", "Discount verification", "Renewal history"), tab, { tab = it })
        when (tab) {
            0 -> PlansPanel(services, onMessage)
            1 -> DiscountVerificationTab(services, onMessage)
            else -> RenewalsTab(services, onMessage)
        }
    }
}

// ---------------------------------------------------------------- plans

@Composable
private fun PlansTab(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var pricing by remember { mutableStateOf(Pricing(null, null, null)) }
    var stats by remember { mutableStateOf<MembershipStats?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        runCatching { services.getPricing() }
            .onSuccess { pricing = it }
            .onFailure { onMessage(it.message ?: "Could not load prices.", true) }
        stats = runCatching { services.membershipStats() }.getOrNull()
    }

    fun save(update: Pricing) {
        scope.launch {
            runCatching { services.savePricing(update) }
                .onSuccess { r ->
                    when (r) {
                        is Outcome.Ok -> {
                            reload++
                            onMessage("Price saved.", false)
                        }
                        is Outcome.Fail -> onMessage(r.message, true)
                    }
                }
                .onFailure { onMessage(it.message ?: "Could not save the price.", true) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val s = stats
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Active memberships", s?.active?.toString() ?: "\u2014", "Currently valid", "users", Gx.Green, Modifier.weight(1f))
            StatCard("Expiring in 7 days", s?.expiringSoon?.toString() ?: "\u2014", "Ending within a week", "clock", Gx.Amber, Modifier.weight(1f))
            StatCard("Expired", s?.expired?.toString() ?: "\u2014", "Need renewal", "alert", Gx.Red, Modifier.weight(1f))
            StatCard("Members", s?.total?.toString() ?: "\u2014", "All registered members", "card", Gx.Blue, Modifier.weight(1f))
        }
        GxPanel("Membership plans") {
            Row(Modifier.padding(15.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlanCard(Modifier.weight(1f), "Monthly membership", "card", "1 calendar month", pricing.monthlyCents) {
                    save(Pricing(it, null, null))
                }
                PlanCard(Modifier.weight(1f), "Daily pass", "clock", "Registration day only", pricing.dailyCents) {
                    save(Pricing(null, it, null))
                }
                PlanCard(Modifier.weight(1f), "Walk-in visit", "users", "Single visit", pricing.walkInCents) {
                    save(Pricing(null, null, it))
                }
            }
        }
    }
}

@Composable
private fun PlanCard(modifier: Modifier, title: String, icon: String, duration: String, current: Long?, onSave: (Long) -> Unit) {
    var text by remember(current) { mutableStateOf(current?.let { Money.plainInput(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Gx.Bg)
            .border(BorderStroke(1.dp, Gx.Border), RoundedCornerShape(7.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GxIcon(icon, Gx.Amber, 20.dp)
                Text(title, color = Gx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            if (current != null) StatusPill("Active", Gx.Green) else StatusPill("Price not set", Gx.Amber)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Current price", color = Gx.Muted, fontSize = 12.sp)
            Text(current?.let { Money.format(it) } ?: "Not set", color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Duration", color = Gx.Muted, fontSize = 12.sp)
            Text(duration, color = Gx.TextSoft, fontSize = 12.sp)
        }
        GxTextField("New price (PHP)", text, { text = it; error = null }, Modifier.fillMaxWidth(), placeholder = "Amount in pesos")
        error?.let { Text(it, color = ErrorText, fontSize = 11.sp) }
        GxButton("Save price", modifier = Modifier.fillMaxWidth(), onClick = {
            val cents = Money.parsePesos(text)
            if (cents == null || cents <= 0) error = "Enter a valid amount." else onSave(cents)
        })
    }
}

// ---------------------------------------------------------------- student discount

@Composable
private fun DiscountTab(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(true) }
    var typeIndex by remember { mutableStateOf(0) }
    var value by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val type = DiscountType.entries[typeIndex]

    LaunchedEffect(Unit) {
        runCatching { services.getDiscount() }
            .onSuccess { d ->
                if (d != null) {
                    enabled = d.enabled
                    typeIndex = d.type.ordinal
                    value = if (d.type == DiscountType.Fixed) Money.plainInput(d.value) else d.value.toString()
                }
            }
            .onFailure { onMessage(it.message ?: "Could not load the discount.", true) }
    }

    GxPanel("Discount rule", action = { StatusPill(if (enabled) "Enabled" else "Disabled", if (enabled) Gx.Green else Gx.Muted) }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GxSwitch("Give the discount to verified members", enabled, { enabled = it })
            Text("DISCOUNT TYPE", color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
            GxSegmented(DiscountType.entries.map { it.label }, typeIndex, { typeIndex = it; error = null }, Modifier.fillMaxWidth())
            GxTextField(
                if (type == DiscountType.Percentage) "Discount (%)" else "Discount amount (PHP)",
                value, { value = it; error = null }, Modifier.fillMaxWidth(),
                placeholder = if (type == DiscountType.Percentage) "0 to 100" else "Amount in pesos",
            )
            Text(
                if (type == DiscountType.Percentage) "Takes this percent off the Monthly price."
                else "Takes this amount off the Monthly price, never more than the price itself.",
                color = Gx.Muted, fontSize = 11.sp,
            )
            error?.let { Text(it, color = ErrorText, fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GxButton("Save discount", onClick = {
                    val parsed: Long? = if (type == DiscountType.Percentage) value.trim().toLongOrNull() else Money.parsePesos(value)
                    if (parsed == null) {
                        error = "Enter a valid number."
                    } else {
                        scope.launch {
                            runCatching { services.saveDiscount(StudentDiscount(type, parsed, enabled)) }
                                .onSuccess { r ->
                                    when (r) {
                                        is Outcome.Ok -> onMessage("Discount saved.", false)
                                        is Outcome.Fail -> error = r.message
                                    }
                                }
                                .onFailure { error = it.message ?: "Could not save the discount." }
                        }
                    }
                })
            }
        }
    }
    GxPanel("How it is applied") {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Rule("The discount applies to Monthly memberships only.")
            Rule("Only staff decide whether a person is an eligible student. Tick the student box when registering.")
            Rule("A certificate of registration is not required, and the student ID number is optional.")
            Rule("Renewals keep the discount for verified members.")
            Rule("Plans for eligible members only keep their own price. The discount is not applied on top.")
        }
    }
}

@Composable
private fun Rule(text: String) {
    Text("\u2022  $text", color = Gx.TextSoft, fontSize = 12.sp)
}

// ---------------------------------------------------------------- renewal history

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, modifier = Modifier.weight(weight))
}

@Composable
private fun RowScope.Cell(text: String, weight: Float, strong: Boolean = false) {
    Text(
        text,
        color = if (strong) Gx.Text else Gx.TextSoft,
        fontSize = 12.sp,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun RenewalsTab(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    var query by remember { mutableStateOf("") }
    var planIndex by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<List<RenewalRow>>(emptyList()) }

    LaunchedEffect(query, planIndex) {
        delay(200)
        val kind = if (planIndex == 0) null else MembershipKind.entries[planIndex - 1]
        runCatching { services.renewalHistory(query, kind) }
            .onSuccess { rows = it }
            .onFailure { onMessage(it.message ?: "Could not load renewals.", true) }
    }

    GxPanel("Membership renewal history") {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                GxTextField("Search", query, { query = it }, Modifier.weight(3f), placeholder = "Member name, number or renewal ID")
                GxDropdown(listOf("All plans") + MembershipKind.entries.map { it.label }, planIndex, { planIndex = it }, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                HeaderCell("RENEWAL ID", 1.6f)
                HeaderCell("MEMBER", 1.8f)
                HeaderCell("MEMBER NO.", 1f)
                HeaderCell("PREVIOUS PLAN", 1.2f)
                HeaderCell("NEW PLAN", 1.2f)
                HeaderCell("PREVIOUS EXPIRY", 1.5f)
                HeaderCell("NEW EXPIRY", 1.5f)
                HeaderCell("RENEWED ON", 1.5f)
            }
            if (rows.isEmpty()) {
                Text("No renewals yet.", color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            } else {
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cell(r.reference, 1.6f)
                        Cell(r.memberName, 1.8f, strong = true)
                        Cell(r.memberNumber, 1f)
                        Cell(r.previousPlan, 1.2f)
                        Cell(r.newPlan, 1.2f)
                        Cell(r.previousExpiry, 1.5f)
                        Cell(r.newExpiry, 1.5f)
                        Cell(r.renewedLabel, 1.5f)
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DiscountVerificationTab(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        EligibilityTypesPanel(services, onMessage)
    }
}