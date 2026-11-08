package org.example.t3gymmanagementsystem.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.DiscountType
import org.example.t3gymmanagementsystem.MemberDetail
import org.example.t3gymmanagementsystem.MemberRow
import org.example.t3gymmanagementsystem.MemberStatus
import org.example.t3gymmanagementsystem.MembershipKind
import org.example.t3gymmanagementsystem.NewMember
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.PaymentInfo
import org.example.t3gymmanagementsystem.PaymentMethod
import org.example.t3gymmanagementsystem.PickedPhoto
import org.example.t3gymmanagementsystem.Plan
import org.example.t3gymmanagementsystem.Quote
import org.example.t3gymmanagementsystem.StudentDiscount
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.PaymentChooser
import org.example.t3gymmanagementsystem.presentation.components.GxDropdown
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxSegmented
import org.example.t3gymmanagementsystem.presentation.components.GxSwitch
import org.example.t3gymmanagementsystem.presentation.components.GxTabs
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.components.StatusPill
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import org.example.t3gymmanagementsystem.util.Money

private val ErrorText = Color(0xFFF06A66)
private const val PAGE_SIZE = 10

private sealed interface MembersView {
    data object Directory : MembersView
    data class Profile(val id: Long) : MembersView
    data object Register : MembersView
}

@Composable
fun MembersScreen(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    var view by remember { mutableStateOf<MembersView>(MembersView.Directory) }
    when (val v = view) {
        MembersView.Directory -> Directory(
            services, onMessage,
            onOpen = { view = MembersView.Profile(it) },
            onRegister = { view = MembersView.Register },
        )
        is MembersView.Profile -> Profile(services, v.id, onMessage, onBack = { view = MembersView.Directory })
        MembersView.Register -> RegisterWizard(
            services, onMessage,
            onCancel = { view = MembersView.Directory },
            onDone = { view = MembersView.Profile(it) },
        )
    }
}

// ---------------------------------------------------------------- shared bits

@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    Text(
        text, color = Gx.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp),
    )
}

@Composable
private fun Avatar(name: String, photo: ImageBitmap?, size: Dp) {
    if (photo != null) {
        Image(
            bitmap = photo, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        val initials = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
        Box(Modifier.size(size).clip(CircleShape).background(Gx.Input), contentAlignment = Alignment.Center) {
            Text(initials, color = Gx.Amber, fontSize = (size.value / 3).sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun statusPill(status: MemberStatus, expiring: Boolean): Pair<String, Color> = when {
    status == MemberStatus.Active && expiring -> "Expiring" to Gx.Amber
    status == MemberStatus.Active -> "Active" to Gx.Green
    status == MemberStatus.Expired -> "Expired" to Gx.Red
    else -> "No membership" to Gx.Muted
}

@Composable
private fun RowScope.Th(text: String, weight: Float) {
    Text(text, color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, modifier = Modifier.weight(weight))
}

@Composable
private fun RowScope.Td(text: String, weight: Float) {
    Text(text, color = Gx.TextSoft, fontSize = 12.sp, modifier = Modifier.weight(weight))
}

@Composable
private fun LineRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Gx.Muted, fontSize = 12.sp)
        Text(
            value, color = if (strong) Gx.Text else Gx.TextSoft, fontSize = 12.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun QuoteLines(quote: Outcome<Quote>?) {
    when (quote) {
        null -> Text("Calculating price...", color = Gx.Muted, fontSize = 12.sp)
        is Outcome.Fail -> Text(quote.message, color = ErrorText, fontSize = 12.sp)
        is Outcome.Ok -> {
            val q = quote.value
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LineRow("Period", q.periodLabel)
                LineRow("Price", Money.format(q.subtotalCents))
                if (q.discountCents > 0) LineRow("Student discount", "- " + Money.format(q.discountCents))
                LineRow("Total", Money.format(q.totalCents), strong = true)
            }
        }
    }
}

@Composable
private fun PaymentFields(methodIndex: Int, onMethod: (Int) -> Unit, note: String, onNote: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PAYMENT METHOD", color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
        GxSegmented(PaymentMethod.entries.map { it.label }, methodIndex, onMethod, Modifier.fillMaxWidth())
        if (PaymentMethod.entries[methodIndex] == PaymentMethod.Online) {
            Text(
                "Show the gym's QR code to the member and confirm once the payment arrives. The QR image display is added with the payments phase.",
                color = Gx.Muted, fontSize = 11.sp,
            )
        }
        GxTextField("Reference / note (optional)", note, onNote, Modifier.fillMaxWidth(), placeholder = "e.g. online reference number")
    }
}

// ---------------------------------------------------------------- directory

@Composable
private fun Directory(services: AppServices, onMessage: (String, Boolean) -> Unit, onOpen: (Long) -> Unit, onRegister: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var statusIndex by remember { mutableStateOf(0) }
    var categoryIndex by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(0) }
    var all by remember { mutableStateOf<List<MemberRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(200)
        runCatching { services.searchMembers(query) }
            .onSuccess { all = it; loaded = true }
            .onFailure { onMessage(it.message ?: "Could not load members.", true) }
    }

    val statusOptions = listOf("All statuses", "Active", "Expiring soon", "Expired", "No membership")
    val categoryOptions = listOf("All categories", "Regular", "Student")
    val filtered = all.filter { r ->
        val statusOk = when (statusIndex) {
            1 -> r.status == MemberStatus.Active
            2 -> r.status == MemberStatus.Active && r.expiringSoon
            3 -> r.status == MemberStatus.Expired
            4 -> r.status == MemberStatus.NoMembership
            else -> true
        }
        val categoryOk = when (categoryIndex) {
            1 -> !r.isStudent
            2 -> r.isStudent
            else -> true
        }
        statusOk && categoryOk
    }
    val pages = maxOf(1, (filtered.size + PAGE_SIZE - 1) / PAGE_SIZE)
    val current = minOf(page, pages - 1)
    val visible = filtered.drop(current * PAGE_SIZE).take(PAGE_SIZE)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Members directory", color = Gx.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${all.size} member${if (all.size == 1) "" else "s"} shown from the search", color = Gx.Muted, fontSize = 12.sp)
            }
            GxButton("Register member", onClick = onRegister)
        }
        GxPanel("Members") {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    GxTextField("Search", query, { query = it; page = 0 }, Modifier.weight(3f), placeholder = "Name, member number or contact")
                    GxDropdown(statusOptions, statusIndex, { statusIndex = it; page = 0 }, Modifier.weight(1f))
                    GxDropdown(categoryOptions, categoryIndex, { categoryIndex = it; page = 0 }, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Th("MEMBER", 2.4f)
                    Th("CONTACT", 1.4f)
                    Th("CATEGORY", 1f)
                    Th("PLAN", 1f)
                    Th("EXPIRY", 1.3f)
                    Th("STATUS", 1.2f)
                }
                if (visible.isEmpty()) {
                    Text(
                        if (!loaded) "Loading members..." else if (all.isEmpty() && query.isBlank()) "No members yet. Use Register member to add the first one." else "No members match.",
                        color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp),
                    )
                }
                visible.forEach { r ->
                    val (statusLabel, statusTone) = statusPill(r.status, r.expiringSoon)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp)).clickable { onOpen(r.id) }.padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(Modifier.weight(2.4f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(r.name, null, 32.dp)
                            Column {
                                Text(r.name, color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(r.number, color = Gx.Muted, fontSize = 11.sp)
                            }
                        }
                        Td(r.contact, 1.4f)
                        Box(Modifier.weight(1f)) {
                            if (r.isStudent) StatusPill("Student", Gx.Blue) else StatusPill("Regular", Gx.Muted)
                        }
                        Td(r.membershipLabel, 1f)
                        Td(r.endLabel, 1.3f)
                        Box(Modifier.weight(1.2f)) { StatusPill(statusLabel, statusTone) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    val from = if (filtered.isEmpty()) 0 else current * PAGE_SIZE + 1
                    val to = current * PAGE_SIZE + visible.size
                    Text("Showing $from-$to of ${filtered.size}", color = Gx.Muted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (current > 0) TextAction("Previous") { page = current - 1 }
                        Text("Page ${current + 1} of $pages", color = Gx.Muted, fontSize = 11.sp)
                        if (current < pages - 1) TextAction("Next") { page = current + 1 }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- profile

@Composable
private fun Profile(services: AppServices, id: Long, onMessage: (String, Boolean) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<MemberDetail?>(null) }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    var tab by remember { mutableStateOf(0) }
    var renewing by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(id, reload) {
        runCatching { services.getMember(id) }
            .onSuccess { detail = it }
            .onFailure { onMessage(it.message ?: "Could not load the member.", true) }
        photo = runCatching { services.memberPhoto(id) }.getOrNull()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextAction("\u2190 Back to members", onBack)
        val d = detail
        if (d == null) {
            Text("Loading member...", color = Gx.Muted, fontSize = 12.sp)
        } else {
            val (statusLabel, statusTone) = statusPill(d.status, false)
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Gx.Surface)
                    .border(BorderStroke(1.dp, Gx.Border), RoundedCornerShape(7.dp))
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Avatar(d.name, photo, 88.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(d.name, color = Gx.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Member no. ${d.number}  \u00B7  ${d.contact}", color = Gx.Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(statusLabel, statusTone)
                        if (d.studentLabel != null) StatusPill("Student", Gx.Blue) else StatusPill("Regular", Gx.Muted)
                        d.lastKind?.let { StatusPill(it.label, Gx.Muted) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GxButton("Renew membership", onClick = { renewing = true })
                    GxButton("Change photo", onClick = {
                        scope.launch {
                            runCatching {
                                val picked = services.choosePhoto()
                                if (picked != null) {
                                    when (val r = services.saveMemberPhoto(id, picked.bytes)) {
                                        is Outcome.Ok -> {
                                            reload++
                                            onMessage("Photo updated.", false)
                                        }
                                        is Outcome.Fail -> onMessage(r.message, true)
                                    }
                                }
                            }.onFailure { onMessage(it.message ?: "Could not read the photo.", true) }
                        }
                    })
                }
            }

            if (renewing) {
                RenewPanel(services, d, onMessage, onClose = { renewing = false }, onDone = { renewing = false; reload++ })
            }

            CardSection(services, d.id, onMessage)
            GxTabs(listOf("Overview", "Membership history"), tab, { tab = it })
            if (tab == 0) {
                GxPanel("Member details") {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailRow("Full name", d.name)
                        DetailRow("Contact", d.contact)
                        DetailRow("Email", d.email)
                        DetailRow("Address", d.address)
                        DetailRow("Emergency contact", d.emergencyContact)
                        DetailRow("Registered", d.registeredLabel)
                        DetailRow("Student", d.studentLabel ?: "No")
                    }
                }
            } else {
                GxPanel("Membership history") {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Th("PLAN", 1f)
                            Th("PERIOD", 2.4f)
                            Th("PAID", 1f)
                            Th("REFERENCE", 1.6f)
                        }
                        if (d.history.isEmpty()) {
                            Text("No memberships yet.", color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                        }
                        d.history.forEach { h ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                                Td(h.kindLabel, 1f)
                                Td(h.periodLabel, 2.4f)
                                Td(h.totalCents?.let { Money.format(it) } ?: "\u2014", 1f)
                                Td(h.reference ?: "\u2014", 1.6f)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.weight(0.35f))
        Text(value.ifBlank { "\u2014" }, color = Gx.TextSoft, fontSize = 12.sp, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun RenewPanel(
    services: AppServices, d: MemberDetail, onMessage: (String, Boolean) -> Unit,
    onClose: () -> Unit, onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var kindIndex by remember { mutableStateOf(0) }
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    LaunchedEffect(Unit) { plans = runCatching { services.listPlans() }.getOrDefault(emptyList()) }
    var methodIndex by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var quote by remember { mutableStateOf<Outcome<Quote>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val selectedPlan = plans.getOrNull(kindIndex)
    val kind = selectedPlan?.kind ?: MembershipKind.Monthly

    LaunchedEffect(kindIndex, plans) {
        quote = null
        quote = runCatching { services.quoteRenewalPlan(d.id, selectedPlan?.id ?: -1L) }
            .getOrElse { Outcome.Fail(it.message ?: "Could not price the renewal.") }
    }

    GxPanel("Renew membership", action = { TextAction("Close", onClose) }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PLAN", color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
            GxDropdown(
                if (plans.isEmpty()) listOf("No plans yet. Create one on the Memberships page.") else plans.map { "${it.name} - ${Money.format(it.priceCents)}" },
                kindIndex, { kindIndex = it; error = null }, Modifier.fillMaxWidth(),
            )
            QuoteLines(quote)
            PaymentChooser(services, methodIndex, { methodIndex = it }, note, { note = it })
            error?.let { Text(it, color = ErrorText, fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GxButton(if (busy) "Saving..." else "Confirm renewal", onClick = {
                    if (busy || quote !is Outcome.Ok) return@GxButton
                    busy = true
                    scope.launch {
                        val result = runCatching {
                            services.renewMembershipPlan(d.id, selectedPlan?.id ?: -1L, PaymentInfo(PaymentMethod.entries[methodIndex], note))
                        }
                        val outcome = result.getOrNull()
                        when {
                            outcome is Outcome.Ok -> {
                                onMessage("Renewed ${d.name}. Reference ${outcome.value.reference}.", false)
                                onDone()
                            }
                            outcome is Outcome.Fail -> error = outcome.message
                            else -> error = result.exceptionOrNull()?.message ?: "Could not renew the membership."
                        }
                        busy = false
                    }
                })
            }
        }
    }
}

// ---------------------------------------------------------------- register wizard

@Composable
private fun RegisterWizard(services: AppServices, onMessage: (String, Boolean) -> Unit, onCancel: () -> Unit, onDone: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val stepTitles = listOf("Profile", "Membership", "Verification", "Payment")
    var step by remember { mutableStateOf(0) }

    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var emergency by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<PickedPhoto?>(null) }

    var kindIndex by remember { mutableStateOf(0) }
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    LaunchedEffect(Unit) { plans = runCatching { services.listPlans() }.getOrDefault(emptyList()) }
    var student by remember { mutableStateOf(false) }
    var school by remember { mutableStateOf("") }
    var idNumber by remember { mutableStateOf("") }
    var methodIndex by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }

    var quote by remember { mutableStateOf<Outcome<Quote>?>(null) }
    var discount by remember { mutableStateOf<StudentDiscount?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val selectedPlan = plans.getOrNull(kindIndex)
    val kind = selectedPlan?.kind ?: MembershipKind.Monthly

    LaunchedEffect(kindIndex, student, plans) {
        quote = null
        quote = runCatching { services.quotePlan(selectedPlan?.id ?: -1L, student) }
            .getOrElse { Outcome.Fail(it.message ?: "Could not price the membership.") }
    }
    LaunchedEffect(Unit) { discount = runCatching { services.getDiscount() }.getOrNull() }

    fun submit() {
        if (busy) return
        busy = true
        scope.launch {
            val input = NewMember(
                fullName = name, contact = contact, email = email, address = address, emergencyContact = emergency,
                kind = kind, isStudent = student, schoolName = school, studentIdNumber = idNumber, planId = selectedPlan?.id,
                payment = PaymentInfo(PaymentMethod.entries[methodIndex], note),
            )
            val result = runCatching { services.registerMember(input) }
            val outcome = result.getOrNull()
            when {
                outcome is Outcome.Ok -> {
                    val reg = outcome.value
                    var photoOk = true
                    photo?.let { p ->
                        val saved = runCatching { services.saveMemberPhoto(reg.memberId, p.bytes) }.getOrNull()
                        photoOk = saved is Outcome.Ok
                    }
                    onMessage(
                        if (photoOk) "Registered ${name.trim()}, member no. ${reg.memberNumber}. Reference ${reg.reference}."
                        else "Registered ${name.trim()}, member no. ${reg.memberNumber}, but the photo could not be saved.",
                        !photoOk,
                    )
                    onDone(reg.memberId)
                }
                outcome is Outcome.Fail -> error = outcome.message
                else -> error = result.exceptionOrNull()?.message ?: "Could not register the member."
            }
            busy = false
        }
    }

    fun next() {
        error = null
        when (step) {
            0 -> when {
                name.isBlank() -> error = "Enter the member's full name."
                contact.isBlank() -> error = "Enter a contact number."
                else -> step = 1
            }
            1 -> if (quote is Outcome.Ok) step = 2 else error = (quote as? Outcome.Fail)?.message ?: "Wait for the price to load."
            2 -> step = 3
            else -> submit()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextAction("\u2190 Back to members", onCancel)
        Text("Register new member", color = Gx.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stepTitles.forEachIndexed { i, title ->
                val tone = when {
                    i < step -> Gx.Green
                    i == step -> Gx.Amber
                    else -> Gx.Muted
                }
                StatusPill("${i + 1}. $title", tone)
            }
        }
        GxPanel(stepTitles[step]) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (step) {
                    0 -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Avatar(name, photo?.preview, 84.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                GxButton("Select photo", onClick = {
                                    scope.launch {
                                        runCatching { services.choosePhoto() }
                                            .onSuccess { if (it != null) photo = it }
                                            .onFailure { onMessage(it.message ?: "Could not read the photo.", true) }
                                    }
                                })
                                if (photo != null) TextAction("Remove photo") { photo = null }
                                Text("Optional. PNG or JPG.", color = Gx.Muted, fontSize = 11.sp)
                            }
                        }
                        GxTextField("Full name", name, { name = it; error = null }, Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GxTextField("Contact number", contact, { contact = it; error = null }, Modifier.weight(1f))
                            GxTextField("Email (optional)", email, { email = it }, Modifier.weight(1f))
                        }
                        GxTextField("Address (optional)", address, { address = it }, Modifier.fillMaxWidth())
                        GxTextField("Emergency contact (optional)", emergency, { emergency = it }, Modifier.fillMaxWidth())
                    }
                    1 -> {
                        LineRow("Member number", "Assigned automatically when saved")
                        Text("PLAN", color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                        GxDropdown(
                            if (plans.isEmpty()) listOf("No plans yet. Create one on the Memberships page.") else plans.map { "${it.name} - ${Money.format(it.priceCents)}" },
                            kindIndex, { kindIndex = it; error = null }, Modifier.fillMaxWidth(),
                        )
                        QuoteLines(quote)
                    }
                    2 -> {
                        GxSwitch("Staff confirm this person is an eligible student", student, { student = it })
                        Text(
                            "Only staff decide eligibility. No certificate of registration is needed.",
                            color = Gx.Muted, fontSize = 11.sp,
                        )
                        if (student) {
                            if (kind == MembershipKind.Daily) {
                                Text("The student discount applies to Monthly memberships only.", color = Gx.Amber, fontSize = 11.sp)
                            }
                            val d = discount
                            Text(
                                when {
                                    d == null || !d.enabled -> "No student discount is active, so the full price is charged."
                                    d.type == DiscountType.Percentage -> "Student discount: ${d.value}% off the Monthly price."
                                    else -> "Student discount: ${Money.format(d.value)} off the Monthly price."
                                },
                                color = Gx.TextSoft, fontSize = 12.sp,
                            )
                            GxTextField("School (optional)", school, { school = it }, Modifier.fillMaxWidth())
                            GxTextField("Student ID number (optional)", idNumber, { idNumber = it }, Modifier.fillMaxWidth())
                        }
                    }
                    else -> {
                        LineRow("Member", name.trim())
                        LineRow("Plan", selectedPlan?.name ?: kind.label)
                        QuoteLines(quote)
                        val total = (quote as? Outcome.Ok)?.value?.totalCents
                        if (total == 0L) {
                            Text("The total is zero, so no payment is recorded.", color = Gx.Muted, fontSize = 11.sp)
                        } else {
                            PaymentChooser(services, methodIndex, { methodIndex = it }, note, { note = it })
                        }
                    }
                }
                error?.let { Text(it, color = ErrorText, fontSize = 12.sp) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    TextAction(if (step == 0) "Cancel" else "Back") {
                        error = null
                        if (step == 0) onCancel() else step -= 1
                    }
                    Box(Modifier.padding(start = 18.dp)) {
                        GxButton(
                            when {
                                step < 3 -> "Next"
                                busy -> "Saving..."
                                else -> "Register member"
                            },
                            onClick = { next() },
                        )
                    }
                }
            }
        }
    }
}


