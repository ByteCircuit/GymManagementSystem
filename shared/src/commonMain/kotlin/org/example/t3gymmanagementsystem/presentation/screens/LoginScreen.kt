package org.example.t3gymmanagementsystem.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.example.t3gymmanagementsystem.AppServices
import org.example.t3gymmanagementsystem.AuthResult
import org.example.t3gymmanagementsystem.presentation.components.BrandMark
import org.example.t3gymmanagementsystem.presentation.components.GxButton
import org.example.t3gymmanagementsystem.presentation.components.GxIcon
import org.example.t3gymmanagementsystem.presentation.components.GxTextField
import org.example.t3gymmanagementsystem.presentation.theme.Gx

/** First run shows "create superadmin"; afterwards a normal sign-in. */
@Composable
fun LoginScreen(services: AppServices, onAuthenticated: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf<Boolean?>(null) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { services.isAdminConfigured() }
            .onSuccess { configured = it }
            .onFailure { error = it.message ?: "Could not read the database." }
    }

    val isSetup = configured == false

    fun submit() {
        if (username.isBlank() || password.isEmpty()) {
            error = "Enter a username and password."
        } else if (isSetup && password != confirm) {
            error = "Passwords do not match."
        } else {
            busy = true
            error = null
            scope.launch {
                val result = runCatching {
                    if (isSetup) services.createAdmin(username, password) else services.login(username, password)
                }
                busy = false
                result.onSuccess { r ->
                    when (r) {
                        is AuthResult.Success -> onAuthenticated(r.username)
                        is AuthResult.Failure -> error = r.message
                    }
                }.onFailure { error = it.message ?: "Something went wrong." }
            }
        }
    }

    Row(Modifier.fillMaxSize().background(Color(0xFF0F0F10))) {
        Column(
            Modifier.weight(0.44f).fillMaxHeight()
                .background(Brush.linearGradient(listOf(Color(0xFF151313), Color(0xFF0C0C0D))))
                .padding(horizontal = 55.dp, vertical = 45.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandMark(58)
                Column {
                    Text("TRIPLE 3", color = Gx.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.5.sp)
                    Text("FITNESS & MARTIAL ARTS GYM", color = Color(0xFF8D8D92), fontSize = 9.sp, letterSpacing = 1.sp)
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text("LOCAL OPERATIONS SYSTEM", color = Gx.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text("One system.\nEvery rep recorded.", color = Gx.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 44.sp, modifier = Modifier.padding(vertical = 14.dp))
                Text(
                    "Memberships, attendance, sales, and inventory, securely managed on this computer.",
                    color = Color(0xFF88888E), fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.width(380.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GxIcon("database", Gx.Green, 20.dp)
                Column {
                    Text("Offline ready", color = Color(0xFFD5D5D8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("No internet connection required", color = Color(0xFF77777C), fontSize = 10.sp)
                }
            }
        }
        Box(Modifier.weight(0.56f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(Modifier.width(365.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (isSetup) "SUPERADMIN SETUP" else "STAFF ACCESS", color = Gx.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text(if (isSetup) "Create your account" else "Welcome back", color = Gx.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (isSetup) "This is the only login for the system. Use a password of at least 8 characters."
                    else "Sign in with the superadmin account.",
                    color = Gx.Muted, fontSize = 12.sp,
                )
                if (configured == null && error == null) {
                    Text("Opening database...", color = Gx.Muted, fontSize = 12.sp)
                } else if (configured != null) {
                    GxTextField("Username", username, { username = it }, Modifier.fillMaxWidth())
                    GxTextField("Password", password, { password = it }, Modifier.fillMaxWidth(), masked = true)
                    if (isSetup) {
                        GxTextField("Confirm password", confirm, { confirm = it }, Modifier.fillMaxWidth(), masked = true)
                    }
                }
                error?.let {
                    Text(
                        it, color = Color(0xFFF06A66), fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(5.dp))
                            .background(Gx.Red.copy(alpha = 0.08f))
                            .border(1.dp, Gx.Red.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                            .padding(10.dp),
                    )
                }
                if (configured != null) {
                    GxButton(
                        if (isSetup) "Create account" else "Sign in to workspace",
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth(),
                        icon = "arrow",
                        enabled = !busy,
                    )
                }
            }
        }
    }
}