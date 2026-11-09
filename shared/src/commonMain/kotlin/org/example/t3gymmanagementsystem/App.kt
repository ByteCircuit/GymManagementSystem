package org.example.t3gymmanagementsystem
import org.example.t3gymmanagementsystem.presentation.navigation.ScreenIntent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.example.t3gymmanagementsystem.presentation.components.GxToast
import org.example.t3gymmanagementsystem.presentation.components.PhasePending
import org.example.t3gymmanagementsystem.presentation.components.Sidebar
import org.example.t3gymmanagementsystem.presentation.components.TopBar
import org.example.t3gymmanagementsystem.presentation.navigation.Screen
import org.example.t3gymmanagementsystem.presentation.screens.AttendanceScreen
import org.example.t3gymmanagementsystem.presentation.screens.DashboardScreen
import org.example.t3gymmanagementsystem.presentation.screens.InventoryScreen
import org.example.t3gymmanagementsystem.presentation.screens.LoginScreen
import org.example.t3gymmanagementsystem.presentation.screens.MembersScreen
import org.example.t3gymmanagementsystem.presentation.screens.MembershipsScreen
import org.example.t3gymmanagementsystem.presentation.screens.PaymentsScreen
import org.example.t3gymmanagementsystem.presentation.screens.PosScreen
import org.example.t3gymmanagementsystem.presentation.screens.ReportsScreen
import org.example.t3gymmanagementsystem.presentation.screens.SettingsScreen
import org.example.t3gymmanagementsystem.presentation.theme.Gx
import org.example.t3gymmanagementsystem.presentation.theme.GymTheme
import org.example.t3gymmanagementsystem.presentation.theme.appFontFamily

/** Text in the main content area is scaled up from the prototype's small CSS sizes for readability. */
private const val CONTENT_FONT_SCALE = 1.0f

@Composable
fun App(services: AppServices) {
    GymTheme {
        val family = appFontFamily()
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(
                fontFamily = family,
                lineHeight = 1.15.em,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ))
        ) {
            var loggedInAs by remember { mutableStateOf<String?>(null) }
            val user = loggedInAs
            if (user == null) {
                LoginScreen(services) { loggedInAs = it }
            } else {
                MainShell(services, user) { loggedInAs = null }
            }
        }
    }
}

private data class ToastState(val message: String, val isError: Boolean)

@Composable
private fun MainShell(services: AppServices, username: String, onLogout: () -> Unit) {
    var screen by remember { mutableStateOf(Screen.Dashboard) }
    var toast by remember { mutableStateOf<ToastState?>(null) }
    var clock by remember { mutableStateOf(services.clock()) }
    var lastBackup by remember { mutableStateOf("No backup yet") }
    var backupTick by remember { mutableStateOf(0) }
    var lowStock by remember { mutableStateOf(0) }
    val notify: (String, Boolean) -> Unit = { message, isError -> toast = ToastState(message, isError) }

    LaunchedEffect(Unit) {
        while (true) {
            clock = services.clock()
            delay(15_000)
        }
    }
    LaunchedEffect(screen) { lowStock = runCatching { services.lowStockCount() }.getOrDefault(0) }
    LaunchedEffect(backupTick) {
        val latest = runCatching { services.listBackups().firstOrNull() }.getOrNull()
        lastBackup = latest?.let { "Last backup: ${it.createdLabel}" } ?: "No backup yet"
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3200)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize().background(Gx.Bg)) {
        Row(Modifier.fillMaxSize()) {
            // Low-stock badge is wired to real inventory data in Phase 5; 0 hides it.
            Sidebar(
                screen,
                { screen = it },
                lowStockCount = lowStock,
                lastBackupLabel = lastBackup,
                userName = username,
                onLogout = onLogout
            )
            Column(Modifier.weight(1f)) {
                TopBar(screen.title, screen.subtitle, clock.dateLine, clock.timeLine)
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 28.dp, vertical = 22.dp),
                ) {
                    val base = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides Density(
                            base.density,
                            base.fontScale * CONTENT_FONT_SCALE
                        )
                    ) {
                        when (screen) {
                            Screen.Members -> MembersScreen(services, notify)
                            Screen.Memberships -> MembershipsScreen(services, notify)
                            Screen.Dashboard -> DashboardScreen(
                                services, notify,
                                onRegister = { ScreenIntent.requestRegister(); screen = Screen.Members },
                                onPayments = { screen = Screen.Payments },
                                onCheckIn = { ScreenIntent.requestAttendanceNfc(); screen = Screen.Attendance },
                                onSale = { screen = Screen.PointOfSale },
                                onMembers = { screen = Screen.Members },
                                onAttendance = { screen = Screen.Attendance },
                                onInventory = { screen = Screen.Inventory },
                                onSettings = { screen = Screen.Settings },
                            )

                            Screen.Reports -> ReportsScreen(services, notify)
                            Screen.Inventory -> InventoryScreen(services, notify, includePos = false)
                            Screen.PointOfSale -> PosScreen(services, notify)
                            Screen.Payments -> PaymentsScreen(services, notify)
                            Screen.Attendance -> AttendanceScreen(services, notify)
                            Screen.Settings -> SettingsScreen(
                                services,
                                onMessage = notify,
                                onBackupsChanged = { backupTick++ },
                            )

                            else -> PhasePending(screen)
                        }
                    }
                }
            }
        }
        toast?.let {
            GxToast(it.message, it.isError, { toast = null }, Modifier.align(Alignment.BottomEnd).padding(20.dp))
        }
    }
}