package org.example.t3gymmanagementsystem.services

import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob

/** Prints receipt text in a monospaced font through the system print dialog. */
class ReceiptPrintable(text: String) : Printable {
    private val lines = text.split("\n")

    override fun print(graphics: Graphics, pageFormat: PageFormat, pageIndex: Int): Int {
        val g = graphics as Graphics2D
        g.font = Font(Font.MONOSPACED, Font.PLAIN, 9)
        val metrics = g.fontMetrics
        val lineHeight = metrics.height
        val perPage = maxOf(1, (pageFormat.imageableHeight / lineHeight).toInt())
        val start = pageIndex * perPage
        if (start >= lines.size) return Printable.NO_SUCH_PAGE
        g.translate(pageFormat.imageableX, pageFormat.imageableY)
        var y = metrics.ascent
        for (i in start until minOf(lines.size, start + perPage)) {
            g.drawString(lines[i], 0, y)
            y += lineHeight
        }
        return Printable.PAGE_EXISTS
    }

    companion object {
        /** Returns false when the user cancels the print dialog. Throws if printing fails. */
        fun print(text: String): Boolean {
            val job = PrinterJob.getPrinterJob()
            job.setPrintable(ReceiptPrintable(text))
            job.jobName = "Receipt"
            if (!job.printDialog()) return false
            job.print()
            return true
        }
    }
}