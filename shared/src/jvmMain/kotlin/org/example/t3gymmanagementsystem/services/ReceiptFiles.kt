package org.example.t3gymmanagementsystem.services

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

object ReceiptFiles {
    /** Asks where to save the PDF. Returns the saved path, or null when the user cancels. */
    fun savePdf(reference: String, bytes: ByteArray): String? {
        val dialog = FileDialog(null as Frame?, "Save receipt as PDF", FileDialog.SAVE)
        dialog.file = "Receipt-$reference.pdf"
        dialog.isVisible = true
        val name = dialog.file
        val dir = dialog.directory
        dialog.dispose()
        if (name == null || dir == null) return null
        val target = File(dir, if (name.lowercase().endsWith(".pdf")) name else "$name.pdf")
        target.writeBytes(bytes)
        return target.absolutePath
    }
}