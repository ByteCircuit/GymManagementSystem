package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.services.InventoryService
import org.example.t3gymmanagementsystem.services.ReportService
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReportServiceTest {
    private lateinit var dir: Path
    private lateinit var inventory: InventoryService
    private lateinit var reports: ReportService

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-reports")
        val db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        inventory = InventoryService(db)
        reports = ReportService(db)
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun build(from: LocalDate, to: LocalDate): ReportBundle =
        assertIs<Outcome.Ok<ReportBundle>>(reports.build(from.toString(), to.toString())).value

    @Test
    fun emptyDatabaseGivesZerosAndOneDayPointPerDay() {
        val today = LocalDate.now()
        val b = build(today.minusDays(6), today)
        assertEquals(0L, b.current.revenueCents)
        assertEquals(7, b.daily.size)
        assertEquals(0, b.members.total)
        assertEquals(0, b.topProducts.size)
    }

    @Test
    fun productSaleShowsUpInTheFigures() {
        val product = assertIs<Outcome.Ok<Long>>(inventory.saveProduct(ProductInput(null, "Training Gloves", "GLO-001", "Gear", 85_000, 5, 10))).value
        assertIs<Outcome.Ok<ProductSaleResult>>(inventory.sell(null, listOf(CartLine(product, 2)), PaymentInfo()))
        val today = LocalDate.now()
        val b = build(today, today)
        assertEquals(170_000L, b.current.productCents)
        assertEquals(170_000L, b.current.revenueCents)
        assertEquals(2, b.current.unitsSold)
        assertEquals(1, b.current.transactions)
        assertEquals(170_000L, b.daily.single().productCents)
        assertEquals("Training Gloves", b.topProducts.single().name)
        assertEquals(2, b.topProducts.single().units)
        assertEquals(1, b.methods.single().count)
        assertEquals(0L, b.previous.revenueCents)
    }

    @Test
    fun lowStockProductsAreListed() {
        inventory.saveProduct(ProductInput(null, "Water", "BTL-004", "Accessories", 32_000, 5, 4))
        inventory.saveProduct(ProductInput(null, "Bag", "BAG-005", "Accessories", 120_000, 5, 0))
        inventory.saveProduct(ProductInput(null, "Tee", "TSH-003", "Apparel", 65_000, 5, 20))
        val today = LocalDate.now()
        val b = build(today, today)
        assertEquals(listOf("Bag", "Water"), b.stockAlerts.map { it.name })
    }

    @Test
    fun badRangesAreRefused() {
        assertIs<Outcome.Fail>(reports.build("nope", "2026-01-01"))
        assertIs<Outcome.Fail>(reports.build("2026-01-01", "nope"))
        assertIs<Outcome.Fail>(reports.build("2026-02-01", "2026-01-01"))
        assertIs<Outcome.Fail>(reports.build("2024-01-01", "2026-01-01"))
    }

    @Test
    fun previousPeriodHasTheSameLengthAndEndsTheDayBefore() {
        val b = build(LocalDate.parse("2026-01-10"), LocalDate.parse("2026-01-16"))
        assertEquals(7, b.days)
        assertEquals("2026-01-03", b.previousFrom)
        assertEquals("2026-01-09", b.previousTo)
    }

    @Test
    fun presetsEndToday() {
        val today = LocalDate.now()
        assertEquals(today.minusDays(29).toString(), reports.rangeFor(ReportPreset.Last30).from)
        assertEquals(today.toString(), reports.rangeFor(ReportPreset.ThisMonth).to)
        assertEquals(today.withDayOfMonth(1).toString(), reports.rangeFor(ReportPreset.ThisMonth).from)
    }

    @Test
    fun everyTabBuildsADocument() {
        val today = LocalDate.now()
        val b = build(today.minusDays(6), today)
        REPORT_TABS.indices.forEach { tab ->
            val doc = b.document(tab)
            assertTrue(doc.sections.isNotEmpty())
            assertTrue(doc.title.contains(REPORT_TABS[tab]))
        }
    }

    @Test
    fun changeTextHandlesZeroAndSigns() {
        assertEquals("+12.5%", changeText(1125, 1000))
        assertEquals("-50.0%", changeText(50, 100))
        assertEquals("0.0%", changeText(100, 100))
        assertEquals("New", changeText(5, 0))
        assertEquals("\u2014", changeText(0, 0))
        assertEquals("72.8", perDay(2186, 30))
    }
}