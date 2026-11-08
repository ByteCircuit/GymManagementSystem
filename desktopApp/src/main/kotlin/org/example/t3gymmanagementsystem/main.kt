package org.example.t3gymmanagementsystem

import androidx.compose.ui.window.WindowPlacement

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import javax.swing.JOptionPane
import kotlin.system.exitProcess

fun main() {
    val services = try {
        JvmAppServices()
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(null, "The app could not start:\n${e.message}", "T3 Gym", JOptionPane.ERROR_MESSAGE)
        exitProcess(1)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Triple 3 Gym Management System",
            state = rememberWindowState(placement = WindowPlacement.Maximized, 
                position = WindowPosition.Aligned(Alignment.Center),
                size = DpSize(1360.dp, 860.dp),
            ),
        ) {
            App(services)
        }
    }
}