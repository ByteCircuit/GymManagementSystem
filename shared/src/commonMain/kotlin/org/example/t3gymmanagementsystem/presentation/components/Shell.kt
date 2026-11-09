package org.example.t3gymmanagementsystem.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.t3gymmanagementsystem.presentation.navigation.Screen
import org.example.t3gymmanagementsystem.presentation.theme.Gx

@Composable
fun BrandMark(size: Int = 42) {
    Box(
        Modifier.size(size.dp).padding(4.dp).border(BorderStroke(2.dp, Gx.Red), RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row {
            Text("T", color = Color.White, fontSize = (size * 0.4).sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
            Text("3", color = Gx.Amber, fontSize = (size * 0.4).sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
fun Sidebar(
    current: Screen,
    onNavigate: (Screen) -> Unit,
    lowStockCount: Int,
    lastBackupLabel: String,
    userName: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.width(238.dp).fillMaxHeight().background(Gx.Sidebar).padding(horizontal = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(83.dp).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            BrandMark()
            Column {
                Text("TRIPLE 3", color = Gx.Text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.9.sp)
                Text("FITNESS & MARTIAL ARTS", color = Color(0xFFA6A6AA), fontSize = 8.sp, letterSpacing = 1.1.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF28282B)))
        Text(
            "WORKSPACE", color = Color(0xFF66666C), fontSize = 10.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp, modifier = Modifier.padding(start = 12.dp, top = 22.dp, bottom = 8.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Screen.entries.forEach { screen ->
                NavItem(screen, screen == current, if (screen == Screen.Inventory) lowStockCount else 0) { onNavigate(screen) }
            }
        }
        Spacer(Modifier.weight(1f))
        SystemState(lastBackupLabel)
        UserRow(userName, onLogout)
    }
}

@Composable
private fun NavItem(screen: Screen, active: Boolean, badge: Int, onClick: () -> Unit) {
    val bg = if (active) Brush.horizontalGradient(listOf(Gx.Red.copy(alpha = 0.19f), Gx.Red.copy(alpha = 0.06f)))
    else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
    Row(
        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp)).background(bg)
            .clickable(onClick = onClick).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        GxIcon(screen.icon, if (active) Gx.RedBright else Gx.NavIdle, 18.dp)
        Text(
            screen.title,
            color = if (active) Color.White else Gx.NavIdle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (badge > 0) {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Gx.Amber.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(badge.toString(), color = Gx.Amber, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SystemState(lastBackupLabel: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Gx.Surface)
            .border(BorderStroke(1.dp, Color(0xFF2B2B2E)), RoundedCornerShape(7.dp)).padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Gx.Green))
            Text("Database connected", color = Gx.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Text("Backup Reminder", color = Color(0xFF74747B), fontSize = 10.sp, modifier = Modifier.padding(start = 15.dp, top = 4.dp, bottom = 10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF28282A)))
        Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GxIcon("database", Color(0xFF797980), 14.dp)
            Text(lastBackupLabel, color = Color(0xFF797980), fontSize = 10.sp)
        }
    }
}

@Composable
private fun UserRow(userName: String, onLogout: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(70.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF583D22)), contentAlignment = Alignment.Center) {
            Text(userName.take(2).uppercase(), color = Color(0xFFFFC170), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(userName, color = Gx.Text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("Superadmin", color = Color(0xFF77777D), fontSize = 10.sp)
        }
        Box(Modifier.clickable(onClick = onLogout).padding(6.dp)) { GxIcon("logout", Color(0xFF75757C), 17.dp) }
    }
}

@Composable
fun TopBar(title: String, subtitle: String, dateLine: String, timeLine: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Gx.Sidebar)) {
        Row(
            Modifier.fillMaxWidth().height(83.dp).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(title, color = Gx.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Gx.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(dateLine, color = Gx.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(timeLine, color = Color(0xFF7D7D83), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Gx.SidebarBorder))
    }
}

/** Shown for modules whose phase is not built yet. No fake controls or data. */
@Composable
fun PhasePending(screen: Screen) {
    Column(
        Modifier.fillMaxWidth().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GxIcon(screen.icon, Gx.Faint, 36.dp)
        Text("${screen.title} is not built yet", color = Gx.TextSoft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Scheduled for Phase ${screen.phase}. No placeholder data is shown.", color = Gx.Muted, fontSize = 12.sp)
    }
}