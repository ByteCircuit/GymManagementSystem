package org.example.t3gymmanagementsystem.services

import org.example.t3gymmanagementsystem.ExportFormat
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.ReportDocument
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

/** Turns a report into CSV or a simple PDF table, and saves it where the user chooses. */
object ReportExporter {

    private val MONEY = Regex("^\u20B1(-?[0-9,]+\\.[0-9]{2})\$")

    // ---- CSV ----

    /** Amounts are written as plain numbers so a spreadsheet can add them up. */
    private fun plain(cell: String): String {
        val m = MONEY.matchEntire(cell.trim())
        return if (m != null) m.groupValues[1].replace(",", "") else cell
    }

    private fun quote(cell: String): String {
        // a leading = + or @ would be run as a formula by a spreadsheet
        val safe = if (cell.isNotEmpty() && cell[0] in "=+@") "'" + cell else cell
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun csv(doc: ReportDocument): ByteArray {
        val sb = StringBuilder("\uFEFF")
        fun row(cells: List<String>) {
            sb.append(cells.joinToString(",") { quote(plain(it)) }).append("\r\n")
        }
        row(listOf(doc.title))
        row(listOf(doc.subtitle))
        sb.append("\r\n")
        doc.sections.forEach { s ->
            row(listOf(s.title))
            row(s.headers)
            s.rows.forEach { row(it) }
            sb.append("\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    // ---- PDF ----

    private fun esc(text: String): String {
        val sb = StringBuilder()
        text.replace("\u20B1", "PHP ").replace("\u2014", "-").replace("\u2013", "-").forEach { ch ->
            when {
                ch == '\\' || ch == '(' || ch == ')' -> sb.append('\\').append(ch)
                ch.code < 32 -> sb.append(' ')
                ch.code > 255 -> sb.append('?')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun fit(text: String, maxChars: Int): String =
        if (text.length <= maxChars) text else text.take((maxChars - 2).coerceAtLeast(1)) + ".."

    fun pdf(doc: ReportDocument): ByteArray {
        val pageW = 595
        val pageH = 842
        val margin = 40
        val usable = pageW - 2 * margin
        val pages = mutableListOf<StringBuilder>()
        var cur = StringBuilder()
        var y = 0

        fun newPage() {
            cur = StringBuilder()
            pages += cur
            y = pageH - margin
        }
        fun text(x: Int, yy: Int, s: String, size: Int, bold: Boolean) {
            cur.append("BT /F${if (bold) 2 else 1} $size Tf $x $yy Td (${esc(s)}) Tj ET\n")
        }
        fun rule(yy: Int) {
            cur.append("0.5 w $margin $yy m ${pageW - margin} $yy l S\n")
        }
        fun ensure(h: Int) {
            if (y - h < margin + 20) newPage()
        }

        newPage()
        text(margin, y - 14, fit(doc.title, 70), 14, true)
        y -= 24
        text(margin, y - 10, fit(doc.subtitle, 110), 8, false)
        y -= 26

        doc.sections.forEach { section ->
            ensure(60)
            text(margin, y - 11, fit(section.title, 80), 11, true)
            y -= 22
            val cols = section.headers.size
            val lengths = (0 until cols).map { i ->
                (listOf(section.headers[i].length) + section.rows.map { r -> r.getOrNull(i)?.length ?: 0 }).max().coerceIn(6, 30)
            }
            val total = lengths.sum().toDouble()
            val widths = lengths.map { (usable * it / total).toInt() }
            val xs = widths.runningFold(margin) { acc, w -> acc + w }

            fun cells(values: List<String>, bold: Boolean) {
                values.forEachIndexed { i, v ->
                    if (i < cols) text(xs[i] + 2, y - 9, fit(v, ((widths[i] - 6) / 4.2).toInt()), 8, bold)
                }
            }
            cells(section.headers, true)
            y -= 13
            rule(y)
            if (section.rows.isEmpty()) {
                y -= 12
                text(margin + 2, y, "No data for this period.", 8, false)
                y -= 6
            }
            section.rows.forEach { row ->
                ensure(14)
                cells(row, false)
                y -= 13
            }
            y -= 12
        }

        pages.forEachIndexed { i, p ->
            p.append("BT /F1 8 Tf $margin 24 Td (${esc("Page ${i + 1} of ${pages.size}")}) Tj ET\n")
        }

        val out = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        fun write(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(n: Int, body: String) {
            offsets += out.size()
            write("$n 0 obj\n$body\nendobj\n")
        }
        write("%PDF-1.4\n")
        obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        val kids = pages.indices.joinToString(" ") { "${5 + 2 * it} 0 R" }
        obj(2, "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>")
        obj(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        obj(4, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        pages.forEachIndexed { i, p ->
            obj(
                5 + 2 * i,
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageW $pageH] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${6 + 2 * i} 0 R >>",
            )
            val data = p.toString()
            obj(6 + 2 * i, "<< /Length ${data.length} >>\nstream\n$data\nendstream")
        }
        val xref = out.size()
        write("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write(String.format("%010d 00000 n \n", it)) }
        write("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toByteArray()
    }

    // ---- save ----

    /** Asks where to save. Returns the saved path, or null when the user cancelled. */
    fun save(doc: ReportDocument, format: ExportFormat): Outcome<String?> {
        val ext = if (format == ExportFormat.Csv) "csv" else "pdf"
        val dialog = FileDialog(null as Frame?, "Save report", FileDialog.SAVE)
        dialog.file = "${doc.fileStem}.$ext"
        dialog.isVisible = true
        val name = dialog.file ?: return Outcome.Ok(null)
        val dir = dialog.directory ?: return Outcome.Ok(null)
        val fileName = if (name.endsWith(".$ext", ignoreCase = true)) name else "$name.$ext"
        val target: Path = Path.of(dir, fileName)
        return try {
            Files.write(target, if (format == ExportFormat.Csv) csv(doc) else pdf(doc))
            Outcome.Ok(target.toString())
        } catch (e: Exception) {
            Outcome.Fail("Could not save the report: ${e.message}")
        }
    }
}