package org.example.t3gymmanagementsystem.services

import java.io.ByteArrayOutputStream

/** Writes a one-page, 80 mm wide PDF from receipt text, using the built-in Courier font. No library needed. */
object ReceiptPdf {
    private const val FONT_SIZE = 9
    private const val LEADING = 11
    private const val MARGIN = 6
    private const val PAGE_WIDTH = 227 // 80 mm in points

    fun build(text: String): ByteArray {
        val lines = text.split("\n").map { sanitize(it) }
        val height = lines.size * LEADING + MARGIN * 2

        val content = StringBuilder()
        content.append("BT\n/F1 $FONT_SIZE Tf\n$LEADING TL\n$MARGIN ${height - MARGIN - FONT_SIZE} Td\n")
        lines.forEach { content.append("(${escape(it)}) Tj\nT*\n") }
        content.append("ET")
        val stream = content.toString().toByteArray(Charsets.ISO_8859_1)

        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()
        fun write(s: String) {
            out.write(s.toByteArray(Charsets.ISO_8859_1))
        }
        fun obj(number: Int, body: String) {
            offsets += out.size()
            write("$number 0 obj\n$body\nendobj\n")
        }

        write("%PDF-1.4\n")
        obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
        obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
        obj(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $height] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>")
        offsets += out.size()
        write("4 0 obj\n<< /Length ${stream.size} >>\nstream\n")
        out.write(stream)
        write("\nendstream\nendobj\n")
        obj(5, "<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")

        val xrefStart = out.size()
        write("xref\n0 6\n0000000000 65535 f \n")
        offsets.forEach { write(it.toString().padStart(10, '0') + " 00000 n \n") }
        write("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
        return out.toByteArray()
    }

    private fun sanitize(s: String): String = buildString {
        s.forEach { ch -> append(if (ch.code in 32..126) ch else '?') }
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
}