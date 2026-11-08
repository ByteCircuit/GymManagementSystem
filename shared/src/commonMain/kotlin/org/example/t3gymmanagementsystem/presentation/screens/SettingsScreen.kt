package org.example.t3gymmanagementsystem.presentation.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.BackupInfo
import org.example.t3gymmanagementsystem.DatabaseStatus
import org.example.t3gymmanagementsystem.GymInfo
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxPanel
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.components.StatusPill
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import kotlin.math.roundToInt

/**
 * Phase 1: gym info (SQLite) plus database status and backup. Prices, discount and payment
 * options arrive with the modules that use them. Restore is implemented and tested in the
 * data layer; its button comes in Phase 7 because it needs an app-restart flow.
 */
@Composable
fun SettingsScreen(services: AppServices, onMessage: (String, Boolean) -> Unit, onBackupsChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<DatabaseStatus?>(null) }
    var backups by remember { mutableStateOf<List<BackupInfo>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching {
            val info = services.getGymInfo()
            name = info.name
            contact = info.contact
            address = info.address
            status = services.databaseStatus()
            backups = services.listBackups()
        }.onFailure { onMessage(it.message ?: "Could not load settings.", true) }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GxPanel("Gym information") {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                GxTextField("Gym name", name, { name = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    GxTextField("Contact number", contact, { contact = it }, Modifier.weight(1f))
                    GxTextField("Gym address", address, { address = it }, Modifier.weight(2f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GxButton("Save changes", onClick = {
                        if (name.isBlank()) {
                            onMessage("Gym name cannot be empty.", true)
                        } else {
                            scope.launch {
                                runCatching { services.saveGymInfo(GymInfo(name.trim(), contact.trim(), address.trim())) }
                                    .onSuccess { onMessage("Gym information was saved locally.", false) }
                                    .onFailure { onMessage("Could not save settings: ${it.message}", true) }
                            }
                        }
                    })
                }
            }
        }

        GxPanel(
            "Database & backup",
            action = {
                GxButton("Create backup", icon = "download", enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        runCatching {
                            val created = services.createBackup()
                            backups = services.listBackups()
                            status = services.databaseStatus()
                            created
                        }.onSuccess {
                            onBackupsChanged()
                            onMessage("Backup ${it.fileName} created.", false)
                        }.onFailure { onMessage(it.message ?: "Backup failed.", true) }
                        busy = false
                    }
                })
            },
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                status?.let { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusPill("Connected", Gx.Green)
                        Text("Schema v${s.schemaVersion} \u00B7 ${formatSize(s.sizeBytes)}", color = Gx.TextSoft, fontSize = 12.sp)
                    }
                    Text(s.path, color = Gx.Muted, fontSize = 11.sp)
                    Text("Backups folder: ${s.backupDir}", color = Gx.Muted, fontSize = 11.sp)
                }
                Text("BACKUP HISTORY", color = Gx.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, modifier = Modifier.padding(top = 8.dp))
                if (backups.isEmpty()) {
                    Text("No backups yet.", color = Gx.Muted, fontSize = 12.sp)
                } else {
                    backups.take(10).forEach {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(it.fileName, color = Gx.TextSoft, fontSize = 12.sp)
                            Text("${it.createdLabel} \u00B7 ${formatSize(it.sizeBytes)}", color = Gx.Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_048_576 -> "${oneDecimal(bytes / 1_048_576.0)} MB"
    bytes >= 1024 -> "${oneDecimal(bytes / 1024.0)} KB"
    else -> "$bytes B"
}

private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}