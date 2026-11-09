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
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.EligibilityType
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.components.StatusPill
import org.example.t3gymmanagementsystem.presentation.theme.Gx

/** The list of eligibility types staff can verify a member against (Student, PWD, ...). */
@Composable
fun EligibilityTypesPanel(services: AppServices, onMessage: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var types by remember { mutableStateOf<List<EligibilityType>>(emptyList()) }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) { types = runCatching { services.listEligibilityTypes() }.getOrDefault(emptyList()) }

    GxPanel("Eligibility types") {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Staff verify a member against one of these types at registration. A plan can be limited to one type.",
                color = Gx.Muted, fontSize = 12.sp,
            )
            if (types.isEmpty()) Text("No types yet. Add one below.", color = Gx.Muted, fontSize = 12.sp)
            types.forEach { t ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(t.name, color = Gx.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (t.inUse) StatusPill("In use", Gx.Blue)
                        Text(
                            if (t.inUse) "Archive" else "Delete",
                            color = Gx.Amber, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    val result = runCatching { services.deleteEligibilityType(t.id) }
                                    val outcome = result.getOrNull()
                                    when {
                                        outcome is Outcome.Ok -> { reload++; onMessage(outcome.value, false) }
                                        outcome is Outcome.Fail -> onMessage(outcome.message, true)
                                        else -> onMessage(result.exceptionOrNull()?.message ?: "Could not change the type.", true)
                                    }
                                }
                            }.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                GxTextField("New type name", name, { name = it; error = null }, Modifier.weight(1f), placeholder = "e.g. Senior citizen")
                GxButton("Add type", onClick = {
                    scope.launch {
                        val result = runCatching { services.saveEligibilityType(name) }
                        val outcome = result.getOrNull()
                        when {
                            outcome is Outcome.Ok -> { name = ""; error = null; reload++; onMessage("Type added.", false) }
                            outcome is Outcome.Fail -> error = outcome.message
                            else -> error = result.exceptionOrNull()?.message ?: "Could not add the type."
                        }
                    }
                })
            }
            error?.let { Text(it, color = Color(0xFFF06A66), fontSize = 12.sp) }
        }
    }
}