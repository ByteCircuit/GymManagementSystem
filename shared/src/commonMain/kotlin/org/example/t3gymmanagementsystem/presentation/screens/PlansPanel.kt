package org.example.t3gymmanagementsystem.presentation.screens

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
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.EligibilityType
import org.example.t3gymmanagementsystem.MembershipKind
import org.example.t3gymmanagementsystem.MembershipStats
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.Plan
import org.example.t3gymmanagementsystem.PlanInput
import org.example.t3gymmanagementsystem.Pricing
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxDropdown
import org.example.t3gymmanagementsystem.presentation.components.GxSwitch
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxSegmented
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.components.StatCard
import org.example.t3gymmanagementsystem.presentation.components.StatusPill
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import org.example.t3gymmanagementsystem.util.Money

private val PlanErrorText = Color(0xFFF06A66)

private data class PlanDraft(
    val id: Long? = null,
    val name: String = "",
    val kindIndex: Int = 0,
    val price: String = "",
    val locked: Boolean = false,
    val eligibilityTypeId: Long? = null,
    val count: String = "1",
)

private fun durationText(kind: MembershipKind, count: Int = 1): String =
    if (kind == MembershipKind.Monthly) {
        if (count == 1) "1 calendar month from the start date" else "$count calendar months from the start date"
    } else {
        if (count == 1) "Valid for one day" else "Valid for $count days"
    }

private fun durationValue(kind: MembershipKind, count: Int = 1): String =
    if (kind == MembershipKind.Monthly) (if (count == 1) "1 Month" else "$count Months") else (if (count == 1) "1 Day" else "$count Days")

@Composable
private fun PlanAction(text: String, onClick: () -> Unit) {
    Text(
        text, color = Gx.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@Composable
private fun PlanCard(
    plan: Plan,
    tone: Color,
    confirming: Boolean,
    onEdit: () -> Unit,
    onAskDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Gx.Bg)
            .border(BorderStroke(1.dp, Gx.Border), RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                    .background(tone.copy(alpha = 0.12f))
                    .border(BorderStroke(1.dp, tone.copy(alpha = 0.3f)), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (plan.kind == MembershipKind.Monthly) "M" else "D",
                    color = tone, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill("Active", Gx.Green)
                plan.eligibilityName?.let { StatusPill("$it only", Gx.Amber) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(plan.name, color = Gx.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(durationText(plan.kind, plan.durationCount), color = Gx.Muted, fontSize = 11.sp)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Gx.Border))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("DURATION", color = Gx.Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                Text(durationValue(plan.kind, plan.durationCount), color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("PRICE", color = Gx.Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                Text(Money.format(plan.priceCents), color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .border(BorderStroke(1.dp, Gx.Border), RoundedCornerShape(6.dp))
                .clickable(onClick = onEdit)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Edit plan settings  \u2192", color = Gx.TextSoft, fontSize = 12.sp)
        }
        if (confirming) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (plan.inUse) "Archive this plan?" else "Delete this plan?", color = Gx.TextSoft, fontSize = 12.sp, modifier = Modifier.weight(1f))
                GxButton(if (plan.inUse) "Yes, archive" else "Yes, delete", onClick = onConfirmDelete)
                PlanAction("Cancel", onCancelDelete)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                PlanAction(if (plan.inUse) "Archive plan" else "Delete plan", onAskDelete)
            }
        }
    }
}

/** The Membership plans tab: stats, the plan cards with create / edit / delete, and the walk-in price. */
@Composable
fun PlansPanel(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<MembershipStats?>(null) }
    var walkInText by remember { mutableStateOf("") }
    var walkInSet by remember { mutableStateOf(false) }
    var walkInError by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<PlanDraft?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    var reload by remember { mutableStateOf(0) }
    var types by remember { mutableStateOf<List<EligibilityType>>(emptyList()) }

    val tones = listOf(Gx.Red, Gx.Amber, Gx.Blue)

    LaunchedEffect(reload) {
        runCatching { services.listPlans() }
            .onSuccess { plans = it; loaded = true }
            .onFailure { onMessage(it.message ?: "Could not load plans.", true) }
        stats = runCatching { services.membershipStats() }.getOrNull()
        types = runCatching { services.listEligibilityTypes() }.getOrDefault(emptyList())
        val walkIn = runCatching { services.getPricing() }.getOrNull()?.walkInCents
        walkInSet = walkIn != null
        walkInText = walkIn?.let { Money.plainInput(it) } ?: ""
    }

    fun savePlan(d: PlanDraft) {
        val cents = Money.parsePesos(d.price)
        val count = d.count.trim().toIntOrNull()
        val maxCount = if (d.kindIndex == 0) 120 else 3650
        if (count == null || count < 1 || count > maxCount) {
            formError = if (d.kindIndex == 0) "Enter the number of months (1 to 120)." else "Enter the number of days (1 to 3650)."
            return
        }
        if (cents == null || cents <= 0) {
            formError = "Enter a valid price."
            return
        }
        scope.launch {
            val result = runCatching { services.savePlan(PlanInput(d.id, d.name, MembershipKind.entries[d.kindIndex], cents, d.eligibilityTypeId, count)) }
            val outcome = result.getOrNull()
            when {
                outcome is Outcome.Ok -> {
                    draft = null
                    formError = null
                    reload++
                    onMessage(if (d.id == null) "Plan created." else "Plan updated.", false)
                }
                outcome is Outcome.Fail -> formError = outcome.message
                else -> formError = result.exceptionOrNull()?.message ?: "Could not save the plan."
            }
        }
    }

    fun deletePlan(id: Long) {
        scope.launch {
            val result = runCatching { services.deletePlan(id) }
            val outcome = result.getOrNull()
            confirmDelete = null
            when {
                outcome is Outcome.Ok -> {
                    reload++
                    onMessage(outcome.value, false)
                }
                outcome is Outcome.Fail -> onMessage(outcome.message, true)
                else -> onMessage(result.exceptionOrNull()?.message ?: "Could not delete the plan.", true)
            }
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

        val d = draft
        if (d != null) {
            GxPanel(if (d.id == null) "Create plan" else "Edit plan") {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    GxTextField("Plan name", d.name, { draft = d.copy(name = it); formError = null }, Modifier.fillMaxWidth(), placeholder = "e.g. Regular Monthly")
                    Text("DURATION", color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                    GxSegmented(
                        listOf("Months", "Days"), d.kindIndex,
                        { if (!d.locked) draft = d.copy(kindIndex = it) },
                        Modifier.fillMaxWidth(),
                    )
                    Text(
                        if (d.locked) "The duration can't change because members have used this plan."
                        else durationText(MembershipKind.entries[d.kindIndex], d.count.trim().toIntOrNull() ?: 1),
                        color = Gx.Muted, fontSize = 11.sp,
                    )
                    GxTextField(if (d.kindIndex == 0) "Number of months" else "Number of days", d.count, { draft = d.copy(count = it.filter { ch -> ch.isDigit() }.take(4)); formError = null }, Modifier.fillMaxWidth(), placeholder = "e.g. 3")
                    GxTextField("Price (PHP)", d.price, { draft = d.copy(price = it); formError = null }, Modifier.fillMaxWidth(), placeholder = "Amount in pesos")
                    GxSwitch(
                        "Discount plan: only eligible members can take it",
                        d.eligibilityTypeId != null,
                        { draft = d.copy(eligibilityTypeId = if (it) types.firstOrNull()?.id else null) },
                    )
                    if (d.eligibilityTypeId != null) {
                        GxDropdown(
                            types.map { it.name },
                            types.indexOfFirst { it.id == d.eligibilityTypeId }.coerceAtLeast(0),
                            { draft = d.copy(eligibilityTypeId = types.getOrNull(it)?.id) },
                            Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        when {
                            types.isEmpty() -> "Add an eligibility type first (Memberships > Discount verification)."
                            d.eligibilityTypeId == null -> "Any member can take this plan."
                            else -> "Only members verified as this type at registration can take this plan. Set its price as the discounted price."
                        },
                        color = Gx.Muted, fontSize = 11.sp,
                    )
                    formError?.let { Text(it, color = PlanErrorText, fontSize = 12.sp) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        PlanAction("Cancel") { draft = null; formError = null }
                        Row(Modifier.padding(start = 12.dp)) { GxButton("Save plan", onClick = { savePlan(d) }) }
                    }
                }
            }
        }

        GxPanel("Membership plans", action = {
            GxButton("Create plan", onClick = { draft = PlanDraft(); formError = null })
        }) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (plans.isEmpty()) {
                    Text(
                        if (!loaded) "Loading plans..." else "No plans yet. Use Create plan to add the first one.",
                        color = Gx.Muted, fontSize = 12.sp,
                    )
                }
                plans.chunked(3).forEachIndexed { rowIndex, rowPlans ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        rowPlans.forEachIndexed { colIndex, p ->
                            PlanCard(
                                plan = p,
                                tone = tones[(rowIndex * 3 + colIndex) % tones.size],
                                confirming = confirmDelete == p.id,
                                onEdit = {
                                    draft = PlanDraft(p.id, p.name, p.kind.ordinal, Money.plainInput(p.priceCents), p.inUse, p.eligibilityTypeId, p.durationCount.toString())
                                    formError = null
                                },
                                onAskDelete = { confirmDelete = p.id },
                                onConfirmDelete = { deletePlan(p.id) },
                                onCancelDelete = { confirmDelete = null },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - rowPlans.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        GxPanel("Walk-in visit price") {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (walkInSet) "Charged for each walk-in visit." else "Not set yet. Set it before recording walk-in visits.",
                    color = Gx.Muted, fontSize = 12.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    GxTextField("Price (PHP)", walkInText, { walkInText = it; walkInError = null }, Modifier.weight(1f), placeholder = "Amount in pesos")
                    GxButton("Save price", onClick = {
                        val cents = Money.parsePesos(walkInText)
                        if (cents == null || cents <= 0) {
                            walkInError = "Enter a valid amount."
                        } else {
                            scope.launch {
                                val result = runCatching { services.savePricing(Pricing(null, null, cents)) }
                                val outcome = result.getOrNull()
                                when {
                                    outcome is Outcome.Ok -> {
                                        reload++
                                        onMessage("Walk-in price saved.", false)
                                    }
                                    outcome is Outcome.Fail -> walkInError = outcome.message
                                    else -> walkInError = result.exceptionOrNull()?.message ?: "Could not save the price."
                                }
                            }
                        }
                    })
                }
                walkInError?.let { Text(it, color = PlanErrorText, fontSize = 12.sp) }
            }
        }
    }
}