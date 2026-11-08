package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.services.ReceiptPdf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReceiptPdfTest {
    private fun pdfText(text: String) = String(ReceiptPdf.build(text), Charsets.ISO_8859_1)

    @Test
    fun hasPdfHeaderAndEndMarker() {
        val s = pdfText("Line one\nLine two")
        assertTrue(s.startsWith("%PDF-1.4"))
        assertTrue(s.endsWith("%%EOF\n"))
    }

    @Test
    fun crossReferenceOffsetsPointAtTheirObjects() {
        val s = pdfText("Line one\nLine two\nPHP 1,500.00")
        val startxref = Regex("startxref\n(\\d+)").find(s)
        assertNotNull(startxref)
        val xrefOffset = startxref.groupValues[1].toInt()
        assertTrue(s.substring(xrefOffset).startsWith("xref"))
        val xrefLines = s.substring(xrefOffset).split("\n")
        // xrefLines: "xref", "0 6", free entry, then objects 1..5
        for (n in 1..5) {
            val entry = xrefLines[2 + n]
            assertEquals(19, entry.length)
            val offset = entry.substring(0, 10).toInt()
            assertTrue(s.substring(offset).startsWith("$n 0 obj"), "object $n offset wrong")
        }
    }

    @Test
    fun escapesParenthesesAndBackslashesAndReplacesNonAscii() {
        val s = pdfText("(a) \\ b \u20B1")
        assertTrue(s.contains("\\(a\\) \\\\ b ?"))
    }
}