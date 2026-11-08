package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.services.ReportExporter
import kotlin.test.Test
import kotlin.test.assertTrue

class ReportExportTest {
    private val doc = ReportDocument(
        "Test report", "Period: 2026-01-01 to 2026-01-31", "T3-Test",
        listOf(
            ReportSection(
                "Sales", listOf("Name", "Amount"),
                listOf(
                    listOf("Gloves, large", "\u20B11,250.50"),
                    listOf("He said \"hi\"", "\u20B1100.00"),
                    listOf("=SUM(A1)", "\u20B10.00"),
                ),
            ),
        ),
    )

    @Test
    fun csvQuotesCellsAndWritesAmountsAsNumbers() {
        val text = ReportExporter.csv(doc).toString(Charsets.UTF_8)
        assertTrue(text.startsWith("\uFEFF"))
        assertTrue(text.contains("\"Gloves, large\",1250.50"))
        assertTrue(text.contains("\"He said \"\"hi\"\"\",100.00"))
        assertTrue(text.contains("'=SUM(A1)"))
        assertTrue(!text.contains("\u20B1"))
    }

    @Test
    fun pdfIsWellFormedAndSplitsAcrossPages() {
        val rows = (1..200).map { listOf("Row $it", "\u20B1$it.00") }
        val big = doc.copy(sections = listOf(ReportSection("Many rows", listOf("Name", "Amount"), rows)))
        val text = ReportExporter.pdf(big).toString(Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-"))
        assertTrue(text.trimEnd().endsWith("%%EOF"))
        assertTrue(Regex("/Type /Page ").findAll(text).count() > 1)
        assertTrue(text.contains("PHP 200.00"))
    }
}