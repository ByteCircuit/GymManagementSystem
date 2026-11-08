package org.example.t3gymmanagementsystem.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.CardInfo
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.ReaderEvent
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.theme.Gx

/** Assign, replace or remove a member's NFC card, by tapping it on the reader or typing its ID. */
@Composable
fun CardSection(services: AppServices, memberId: Long, onMessage: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var card by remember { mutableStateOf<CardInfo?>(null) }
    var assigning by remember { mutableStateOf(false) }
    var uidText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(memberId, reload) {
        card = runCatching { services.getMemberCard(memberId) }.getOrNull()
    }

    suspend fun assign(uid: String) {
        val result = runCatching { services.assignCard(memberId, uid) }
        val outcome = result.getOrNull()
        when {
            outcome is Outcome.Ok -> {
                assigning = false
                uidText = ""
                error = null
                reload++
                onMessage("Card assigned.", false)
            }
            outcome is Outcome.Fail -> error = outcome.message
            else -> error = result.exceptionOrNull()?.message ?: "Could not assign the card."
        }
    }

    // While assigning, the first card tapped on the reader is taken.
    LaunchedEffect(assigning) {
        if (assigning) {
            val scan = services.cardEvents().first { it is ReaderEvent.Scan } as ReaderEvent.Scan
            assign(scan.uid)
        }
    }

    GxPanel("NFC card") {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val current = card
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (current == null) "No card assigned" else "Card ID ${current.uid}",
                        color = Gx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        current?.assignedLabel ?: "Assign a card so this member can check in by tapping it.",
                        color = Gx.Muted, fontSize = 11.sp,
                    )
                }
                GxButton(
                    if (current == null) "Assign card" else "Replace card",
                    onClick = { assigning = true; error = null },
                )
                if (current != null) {
                    GxButton("Remove card", onClick = {
                        scope.launch {
                            val result = runCatching { services.removeCard(memberId) }
                            val outcome = result.getOrNull()
                            if (outcome is Outcome.Fail) onMessage(outcome.message, true)
                            else if (outcome == null) onMessage(result.exceptionOrNull()?.message ?: "Could not remove the card.", true)
                            else onMessage("Card removed.", false)
                            reload++
                        }
                    })
                }
            }
            if (assigning) {
                Text("Tap the card on the reader now, or type the card ID below.", color = Gx.Amber, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    GxTextField("Card ID", uidText, { uidText = it; error = null }, Modifier.weight(1f), placeholder = "e.g. 04A1B2C3")
                    GxButton("Save card ID", onClick = { scope.launch { assign(uidText) } })
                    Text(
                        "Cancel", color = Gx.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { assigning = false; uidText = ""; error = null }.padding(8.dp),
                    )
                }
            }
            error?.let { Text(it, color = Color(0xFFF06A66), fontSize = 12.sp) }
        }
    }
}