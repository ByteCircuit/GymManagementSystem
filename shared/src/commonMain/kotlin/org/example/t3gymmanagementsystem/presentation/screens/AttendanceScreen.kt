package org.example.t3gymmanagementsystem.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.AttendanceRow
import org.example.t3gymmanagementsystem.CheckInResult
import org.example.t3gymmanagementsystem.CheckInStatus
import org.example.t3gymmanagementsystem.DeniedRow
import org.example.t3gymmanagementsystem.MemberRow
import org.example.t3gymmanagementsystem.NfcCheckInController
import org.example.t3gymmanagementsystem.NfcUiState
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.PaymentInfo
import org.example.t3gymmanagementsystem.PaymentMethod
import org.example.t3gymmanagementsystem.ReaderState
import org.example.t3gymmanagementsystem.WalkInRow
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxTabs
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.components.PaymentChooser
import org.example.t3gymmanagementsystem.presentation.components.StatCard
import org.example.t3gymmanagementsystem.presentation.components.equalHeight
import org.example.t3gymmanagementsystem.presentation.components.NfcGlyph
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import org.example.t3gymmanagementsystem.util.Money

private val AttErrorText = Color(0xFFF06A66)

// ---------------------------------------------------------------- small helpers

@Composable
private fun RowScope.AttTh(text: String, weight: Float) {
    Text(text, color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, modifier = Modifier.weight(weight))
}

@Composable
private fun RowScope.AttTd(text: String, weight: Float, strong: Boolean = false, color: Color? = null) {
    Text(
        text, color = color ?: if (strong) Gx.Text else Gx.TextSoft, fontSize = 12.sp,
        fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(weight),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun HDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Gx.Faint.copy(alpha = 0.3f)))
}

private fun hourOf(label: String): Int? {
    val h = label.take(2).toIntOrNull() ?: return null
    val pm = label.trim().endsWith("PM", ignoreCase = true)
    return (h % 12) + if (pm) 12 else 0
}

private fun hourText(hour: Int): String {
    val h = hour % 24
    val n = if (h % 12 == 0) 12 else h % 12
    return "$n ${if (h < 12) "AM" else "PM"}"
}

private fun hourShort(hour: Int): String {
    val h = hour % 24
    val n = if (h % 12 == 0) 12 else h % 12
    return "$n${if (h < 12) "a" else "p"}"
}

private fun initialsOf(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

// ---------------------------------------------------------------- scan area

private data class Face(val title: String, val subtitle: String, val tone: Color, val glyph: String, val pill: String)

private fun faceOf(nfc: NfcUiState): Face {
    val r = nfc.result
    return when (nfc.state) {
        ReaderState.IDLE -> Face("Ready to Scan", "Tap the NFC membership card on the connected reader.", Gx.Blue, "", "Reader ready")
        ReaderState.READING -> Face("Reading card...", "Keep the card on the reader.", Gx.Amber, "...", "Card detected")
        ReaderState.PROCESSING -> Face("Looking up member...", "Checking the membership.", Gx.Amber, "...", "Processing")
        ReaderState.SUCCESS -> {
            val detail = r?.detail ?: ""
            when (r?.status) {
                CheckInStatus.Welcome -> Face("Check-in Approved", detail, Gx.Green, "\u2713", "Approved")
                CheckInStatus.AlreadyIn -> Face("Already Checked In", detail, Gx.Blue, "\u2713", "Already in")
                CheckInStatus.Expired -> Face("Check-in Denied", detail, Gx.Red, "\u2715", "Expired")
                CheckInStatus.NoMembership -> Face("Check-in Denied", detail, Gx.Amber, "!", "No membership")
                else -> Face("Done", detail, Gx.Muted, "\u2713", "Done")
            }
        }
        ReaderState.UNKNOWN_CARD -> Face(
            "Unknown Card",
            r?.detail ?: "This card is not assigned to a member.",
            Gx.Amber, "?", "Unknown card",
        )
        ReaderState.ERROR -> Face("Reader Problem", nfc.errorMessage ?: "Something went wrong. Tap the card again.", Gx.Red, "!", "Error")
        ReaderState.DISCONNECTED -> Face("Reader Disconnected", nfc.readerMessage, Gx.Red, "!", "Disconnected")
    }
}

@Composable
private fun MemberLine(r: CheckInResult, tone: Color) {
    val name = r.memberName ?: return
    val badge = when (r.status) {
        CheckInStatus.Welcome -> "Checked in"
        CheckInStatus.AlreadyIn -> "Already in"
        CheckInStatus.Expired -> "Expired"
        CheckInStatus.NoMembership -> "No plan"
        CheckInStatus.UnknownCard -> ""
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Gx.Muted.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, Gx.Muted.copy(alpha = 0.3f)), RoundedCornerShape(8.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(tone.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initialsOf(name), color = tone, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, color = Gx.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            r.memberNumber?.let { Text("Member no. $it", color = Gx.Muted, fontSize = 12.sp) }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (badge.isNotEmpty()) Text(badge, color = tone, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            r.timeLabel?.let { Text(it, color = Gx.Muted, fontSize = 11.sp) }
        }
    }
}

@Composable
private fun ScanArea(nfc: NfcUiState, modifier: Modifier = Modifier) {
    val face = faceOf(nfc)
    val member = nfc.result
    Column(
        modifier.fillMaxWidth().heightIn(min = 520.dp).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape)
                .background(face.tone.copy(alpha = 0.12f))
                .border(BorderStroke(1.dp, face.tone.copy(alpha = 0.5f)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (face.glyph.isEmpty()) NfcGlyph(face.tone, 40.dp) else Text(face.glyph, color = face.tone, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier.clip(RoundedCornerShape(20.dp))
                .background(Gx.Muted.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(face.tone))
            Text(face.pill.uppercase(), color = Gx.TextSoft, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
        Text(face.title, color = Gx.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(face.subtitle, color = Gx.TextSoft, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (nfc.state == ReaderState.SUCCESS && member != null && member.memberName != null) {
            MemberLine(member, face.tone)
        }
    }
}

/** Top bar of the NFC station: connection state on the left, refresh on the right. */
@Composable
private fun StationBar(nfc: NfcUiState, onRefresh: () -> Unit) {
    val tone = if (nfc.connected) Gx.Green else Gx.Red
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(tone))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                if (nfc.connected) "NFC reader connected" else "NFC reader not connected",
                color = Gx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(nfc.readerMessage, color = Gx.Muted, fontSize = 11.sp)
        }
        GxButton("Refresh reader", onClick = onRefresh)
    }
}

// ---------------------------------------------------------------- overview: check-in status panel

/** What the last card tap did, and how many cards were refused today. */
@Composable
private fun CardStatusBlock(nfc: NfcUiState, denied: List<DeniedRow>) {
    val face = faceOf(nfc)
    val who = nfc.result?.memberName
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(face.tone.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, face.tone.copy(alpha = 0.35f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Card status")
            Text(face.pill.uppercase(), color = face.tone, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
        Text(
            if (who != null) "$who  \u00B7  ${face.title}" else face.title,
            color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
        val latest = denied.firstOrNull()
        Text(
            if (latest == null) "No denied cards today"
            else "${denied.size} denied today  \u00B7  last: ${latest.who} (${latest.reason})",
            color = Gx.Muted, fontSize = 11.sp,
        )
    }
}

// ---------------------------------------------------------------- right pane of the NFC station

@Composable
private fun ManualSearch(services: AppServices, onCheckIn: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<MemberRow>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        note = null
        delay(200)
        matches = if (query.trim().length < 2) emptyList()
        else runCatching { services.searchMembers(query) }.getOrDefault(emptyList()).take(5)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel("Manual member search")
        Text("Use this alternative if a card or the reader is unavailable.", color = Gx.TextSoft, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            GxTextField("Name, number or contact", query, { query = it }, Modifier.weight(1f), placeholder = "e.g. T3-0001")
            GxButton("Verify", onClick = {
                if (query.trim().isEmpty()) {
                    note = "Type a name, member number or contact first."
                    return@GxButton
                }
                scope.launch {
                    val found = runCatching { services.searchMembers(query) }.getOrDefault(emptyList())
                    when {
                        found.isEmpty() -> note = "No members match."
                        found.size == 1 -> {
                            onCheckIn(found[0].id)
                            query = ""
                        }
                        else -> {
                            matches = found.take(5)
                            note = "Several members match. Choose one."
                        }
                    }
                }
            })
        }
        note?.let { Text(it, color = Gx.Amber, fontSize = 12.sp) }
        matches.forEach { m ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${m.number}  \u00B7  ${m.membershipLabel}  \u00B7  ${m.status.label}", color = Gx.Muted, fontSize = 11.sp)
                }
                GxButton("Check in", onClick = {
                    onCheckIn(m.id)
                    query = ""
                })
            }
        }
    }
}

@Composable
private fun LocalVerificationNote() {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.padding(top = 4.dp).size(8.dp).clip(CircleShape).background(Gx.Green))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Local verification only", color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "The card ID looks up the member in the local database. No personal data is stored on the card.",
                color = Gx.Muted, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun WalkInForm(services: AppServices, price: Long?, onMessage: (String, Boolean) -> Unit, onRecorded: () -> Unit) {
    val scope = rememberCoroutineScope()
    var visitor by remember { mutableStateOf("") }
    var methodIndex by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("Walk-in visitor")
        Text(
            if (price != null) "Walk-in price: ${Money.format(price)}" else "Set the walk-in price on the Memberships page first.",
            color = if (price != null) Gx.TextSoft else Gx.Amber, fontSize = 12.sp,
        )
        GxTextField("Visitor name (optional)", visitor, { visitor = it }, Modifier.fillMaxWidth())
        PaymentChooser(services, methodIndex, { methodIndex = it }, note, { note = it })
        error?.let { Text(it, color = AttErrorText, fontSize = 12.sp) }
        GxButton(if (busy) "Saving..." else "Record walk-in", onClick = {
            if (price == null) {
                error = "Set the walk-in price on the Memberships page first."
                return@GxButton
            }
            if (busy) return@GxButton
            busy = true
            scope.launch {
                val res = runCatching { services.recordWalkIn(visitor, PaymentInfo(PaymentMethod.entries[methodIndex], note)) }
                val outcome = res.getOrNull()
                when {
                    outcome is Outcome.Ok -> {
                        onMessage("Walk-in recorded. Reference ${outcome.value.reference}.", false)
                        visitor = ""
                        note = ""
                        error = null
                        onRecorded()
                    }
                    outcome is Outcome.Fail -> error = outcome.message
                    else -> error = res.exceptionOrNull()?.message ?: "Could not record the walk-in."
                }
                busy = false
            }
        })
    }
}

// ---------------------------------------------------------------- chart and logs

@Composable
private fun TrendChart(rows: List<AttendanceRow>) {
    val perHour = rows.mapNotNull { hourOf(it.timeLabel) }.groupingBy { it }.eachCount()
    val first = minOf(6, perHour.keys.minOrNull() ?: 6)
    val last = maxOf(21, perHour.keys.maxOrNull() ?: 21)
    val hours = (first..last).toList()
    val max = (perHour.values.maxOrNull() ?: 0).coerceAtLeast(1)
    val grid = Gx.Faint.copy(alpha = 0.25f)
    val barBrush = Brush.verticalGradient(listOf(Gx.Red, Gx.Red.copy(alpha = 0.5f)))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().height(190.dp).drawBehind {
                for (i in 1..4) {
                    val y = size.height * i / 4f
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
            },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            hours.forEach { hour ->
                val n = perHour[hour] ?: 0
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (n > 0) {
                        Box(
                            Modifier.fillMaxWidth(0.6f).height(maxOf(8f, 170f * n / max).dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(barBrush),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            hours.forEach { hour ->
                Text(
                    if (hour % 2 == 0) hourShort(hour) else "",
                    color = Gx.Muted, fontSize = 10.sp, textAlign = TextAlign.Center,
                    maxLines = 1, softWrap = false, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AttendanceLog(title: String, rows: List<AttendanceRow>) {
    GxPanel(title) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                AttTh("LOG ID", 1f)
                AttTh("MEMBER", 2f)
                AttTh("CARD ID", 1.4f)
                AttTh("TIME IN", 1f)
                AttTh("STATUS", 1f)
                AttTh("HOW", 1f)
            }
            HDivider()
            if (rows.isEmpty()) {
                Text("No check-ins yet today.", color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    AttTd(r.logId, 1f, color = Gx.Amber)
                    AttTd(r.memberName, 2f, strong = true)
                    AttTd(r.cardUid, 1.4f)
                    AttTd(r.timeLabel, 1f)
                    Box(Modifier.weight(1f)) {
                        Text(
                            "Checked in", color = Gx.TextSoft, fontSize = 10.sp,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Gx.Muted.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    AttTd(r.source, 1f)
                }
                HDivider()
            }
        }
    }
}

@Composable
private fun DeniedLog(denied: List<DeniedRow>) {
    GxPanel("Denied cards today") {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                AttTh("TIME", 1f)
                AttTh("MEMBER", 2f)
                AttTh("CARD ID", 1.6f)
                AttTh("REASON", 1.6f)
            }
            if (denied.isEmpty()) {
                Text("No denied cards today.", color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
            denied.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    AttTd(d.timeLabel, 1f)
                    AttTd(d.who, 2f, strong = true)
                    AttTd(d.cardUid, 1.6f)
                    AttTd(d.reason, 1.6f)
                }
            }
        }
    }
}

@Composable
private fun WalkInLog(walkIns: List<WalkInRow>) {
    GxPanel("Walk-ins today") {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                AttTh("TIME", 1f)
                AttTh("VISITOR", 2f)
                AttTh("REFERENCE", 1.6f)
                AttTh("PAID", 1f)
            }
            if (walkIns.isEmpty()) {
                Text("No walk-in visits yet today.", color = Gx.Muted, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
            walkIns.forEach { w ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    AttTd(w.timeLabel, 1f)
                    AttTd(w.visitor, 2f, strong = true)
                    AttTd(w.reference, 1.6f)
                    AttTd(Money.format(w.totalCents), 1f)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- screen

@Composable
fun AttendanceScreen(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<List<AttendanceRow>>(emptyList()) }
    var walkIns by remember { mutableStateOf<List<WalkInRow>>(emptyList()) }
    var denied by remember { mutableStateOf<List<DeniedRow>>(emptyList()) }
    var walkInPrice by remember { mutableStateOf<Long?>(null) }
    var reload by remember { mutableStateOf(0) }

    val controller = remember(services) {
        NfcCheckInController(
            events = services.cardEvents(),
            lookup = { uid -> services.checkInByCard(uid) },
            manualLookup = { id -> services.checkInMember(id) },
            onLookupDone = { reload++ },
        )
    }
    // The reader is only listened to while this page is showing.
    LaunchedEffect(controller) { controller.run() }
    val nfc by controller.state.collectAsState()

    LaunchedEffect(reload) {
        rows = runCatching { services.todayAttendance() }.getOrDefault(emptyList())
        walkIns = runCatching { services.todayWalkIns() }.getOrDefault(emptyList())
        denied = runCatching { services.todayDenied() }.getOrDefault(emptyList())
        walkInPrice = runCatching { services.getPricing() }.getOrNull()?.walkInCents
    }

    val peak = rows.mapNotNull { hourOf(it.timeLabel) }.groupingBy { it }.eachCount().maxByOrNull { it.value }
    val deniedSummary = denied.groupingBy { it.reason }.eachCount().entries
        .joinToString("  \u00B7  ") { "${it.value} ${it.key.lowercase()}" }
    val lineColor = Gx.Faint.copy(alpha = 0.3f)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GxTabs(listOf("Attendance overview", "NFC check-in", "Attendance logs"), tab, { tab = it })

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Checked in today", rows.size.toString(), "Time-ins recorded today", "users", Gx.Green, Modifier.weight(1f))
            StatCard("Walk-ins today", walkIns.size.toString(), "Walk-in visits today", "clock", Gx.Blue, Modifier.weight(1f))
            StatCard(
                "Peak hour",
                if (peak == null) "\u2014" else "${hourText(peak.key)}\u2013${hourText(peak.key + 1)}",
                if (peak == null) "No check-ins yet" else "${peak.value} check-in(s)",
                "clock", Gx.Amber, Modifier.weight(1f),
            )
            StatCard(
                "Denied today",
                denied.size.toString(),
                if (denied.isEmpty()) "No denied cards today" else deniedSummary,
                "card",
                if (denied.isEmpty()) Gx.Green else Gx.Red,
                Modifier.weight(1f),
            )
        }

        when (tab) {
            0 -> {
                var trendH by remember { mutableStateOf(0) }
                var statusH by remember { mutableStateOf(0) }
                val equalH = maxOf(trendH, statusH)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1.45f)) {
                        GxPanel("Today's attendance trend") {
                            Column(Modifier.equalHeight(equalH) { trendH = it }.padding(15.dp)) { TrendChart(rows) }
                        }
                    }
                    Column(Modifier.weight(0.8f)) {
                        GxPanel("Check-in status") {
                            Column(
                                Modifier.equalHeight(equalH) { statusH = it }.fillMaxWidth().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (nfc.connected) Gx.Green else Gx.Red))
                                    Text(
                                        if (nfc.connected) "NFC reader connected" else "NFC reader not connected",
                                        color = Gx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Text(nfc.readerMessage, color = Gx.Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
                                CardStatusBlock(nfc, denied)
                                GxButton("Open NFC check-in", onClick = { tab = 1 })
                            }
                        }
                    }
                }
                AttendanceLog("Today's attendance log", rows)
            }
            1 -> {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .border(BorderStroke(1.dp, lineColor), RoundedCornerShape(8.dp)),
                ) {
                    StationBar(nfc) { services.refreshReader() }
                    HDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        ScanArea(
                            nfc,
                            Modifier.weight(1.45f).drawBehind {
                                drawLine(lineColor, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), 1.dp.toPx())
                            },
                        )
                        Column(
                            Modifier.weight(0.75f).drawBehind {
                                drawLine(lineColor, Offset(0.5f, 0f), Offset(0.5f, size.height), 1.dp.toPx())
                            }.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ManualSearch(services) { id -> controller.manualCheckIn(id) }
                            // local verification note removed
                            HDivider()
                            WalkInForm(services, walkInPrice, onMessage) { reload++ }
                        }
                    }
                }
            }
            else -> {
                AttendanceLog("Today's attendance log", rows)
                DeniedLog(denied)
                WalkInLog(walkIns)
            }
        }
    }
}