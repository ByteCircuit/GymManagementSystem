package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.services.InventoryService
import org.example.t3gymmanagementsystem.services.PaymentService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InventoryServiceTest {
    private lateinit var dir: Path
    private lateinit var inventory: InventoryService
    private lateinit var payments: PaymentService

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-inventory")
        val db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        inventory = InventoryService(db)
        payments = PaymentService(db)
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun add(name: String = "Training Gloves", sku: String = "GLO-001", price: Long = 85_000, low: Int = 5, stock: Int = 12): Long {
        val result = inventory.saveProduct(ProductInput(null, name, sku, "Gear", price, low, stock))
        return assertIs<Outcome.Ok<Long>>(result).value
    }

    private fun stockOf(id: Long): Int = inventory.products("", null, StockFilter.All).first { it.id == id }.stock

    @Test
    fun newProductRecordsItsOpeningStock() {
        add(stock = 12)
        val row = inventory.products("", null, StockFilter.All).single()
        assertEquals(12, row.stock)
        assertEquals(StockLevel.InStock, row.level)
        val movement = inventory.movements().single()
        assertEquals("Opening stock", movement.kindLabel)
        assertEquals(12, movement.change)
    }

    @Test
    fun duplicateSkuIsRefusedEvenInAnotherCase() {
        add(sku = "GLO-001")
        assertIs<Outcome.Fail>(inventory.saveProduct(ProductInput(null, "Other", "glo-001", "Gear", 10_000, 2, 1)))
    }

    @Test
    fun invalidInputIsRefused() {
        assertIs<Outcome.Fail>(inventory.saveProduct(ProductInput(null, "", "A", "Gear", 10_000, 2, 1)))
        assertIs<Outcome.Fail>(inventory.saveProduct(ProductInput(null, "A", "A", "Gear", 0, 2, 1)))
        assertIs<Outcome.Fail>(inventory.saveProduct(ProductInput(null, "A", "A", "Gear", 10_000, -1, 1)))
    }

    @Test
    fun stockLevelsFollowEachProductsOwnThreshold() {
        add("A", "A-1", low = 5, stock = 4)
        add("B", "B-1", low = 5, stock = 0)
        add("C", "C-1", low = 5, stock = 9)
        val levels = inventory.products("", null, StockFilter.All).associate { it.name to it.level }
        assertEquals(StockLevel.Low, levels["A"])
        assertEquals(StockLevel.Out, levels["B"])
        assertEquals(StockLevel.InStock, levels["C"])
        val s = inventory.summary()
        assertEquals(3, s.totalProducts)
        assertEquals(13, s.stockUnits)
        assertEquals(1, s.lowCount)
        assertEquals(1, s.outCount)
        assertEquals(1, inventory.lowStockCount())
    }

    @Test
    fun saleReducesStockAndShowsInPaymentFigures() {
        val id = add(price = 85_000, stock = 12)
        val sale = inventory.sell(null, listOf(CartLine(id, 2)), PaymentInfo())
        val ok = assertIs<Outcome.Ok<ProductSaleResult>>(sale).value
        assertEquals(170_000L, ok.totalCents)
        assertEquals(10, stockOf(id))
        val s = payments.summary()
        assertEquals(1, s.todayCount)
        assertEquals(170_000L, s.productCents)
        val sold = inventory.movements().first { it.kindLabel == "Sale" }
        assertEquals(-2, sold.change)
        assertEquals(10, sold.stockAfter)
        val recent = inventory.recentSales().single()
        assertEquals("Walk-in customer", recent.customer)
        assertTrue(recent.items.contains("Training Gloves"))
    }

    @Test
    fun sellingMoreThanInStockIsRefusedAndChangesNothing() {
        val id = add(stock = 3)
        assertIs<Outcome.Fail>(inventory.sell(null, listOf(CartLine(id, 4)), PaymentInfo()))
        assertEquals(3, stockOf(id))
        assertEquals(0, payments.summary().todayCount)
    }

    @Test
    fun oneBadLineStopsTheWholeSale() {
        val good = add("Good", "G-1", stock = 5)
        val empty = add("Empty", "E-1", stock = 0)
        assertIs<Outcome.Fail>(inventory.sell(null, listOf(CartLine(good, 1), CartLine(empty, 1)), PaymentInfo()))
        assertEquals(5, stockOf(good))
        assertEquals(0, payments.summary().todayCount)
    }

    @Test
    fun duplicateCartLinesAreMerged() {
        val id = add(stock = 10)
        assertIs<Outcome.Ok<ProductSaleResult>>(inventory.sell(null, listOf(CartLine(id, 1), CartLine(id, 2)), PaymentInfo()))
        assertEquals(7, stockOf(id))
    }

    @Test
    fun emptyCartIsRefused() {
        assertIs<Outcome.Fail>(inventory.sell(null, emptyList(), PaymentInfo()))
    }

    @Test
    fun restockAndAdjustAreRecorded() {
        val id = add(stock = 10)
        assertIs<Outcome.Ok<Unit>>(inventory.restock(id, 5, "supplier"))
        assertEquals(15, stockOf(id))
        assertIs<Outcome.Fail>(inventory.restock(id, 0, ""))
        assertIs<Outcome.Fail>(inventory.adjust(id, 12, " "))
        assertIs<Outcome.Fail>(inventory.adjust(id, 15, "same"))
        assertIs<Outcome.Ok<Unit>>(inventory.adjust(id, 12, "recount"))
        assertEquals(12, stockOf(id))
        val adjustment = inventory.movements().first()
        assertEquals("Adjustment", adjustment.kindLabel)
        assertEquals(-3, adjustment.change)
    }

    @Test
    fun archivedProductsCannotBeSoldAndAreListedSeparately() {
        val id = add(stock = 5)
        assertIs<Outcome.Ok<Unit>>(inventory.setActive(id, false))
        assertIs<Outcome.Fail>(inventory.sell(null, listOf(CartLine(id, 1)), PaymentInfo()))
        assertEquals(0, inventory.products("", null, StockFilter.All).size)
        assertEquals(1, inventory.products("", null, StockFilter.Archived).size)
        assertIs<Outcome.Ok<Unit>>(inventory.setActive(id, true))
        assertEquals(1, inventory.products("", null, StockFilter.All).size)
    }

    @Test
    fun searchMatchesNameOrSkuAndCategoryFilters() {
        add("Hand Wraps", "WRP-002", stock = 4)
        inventory.saveProduct(ProductInput(null, "Protein Shaker", "SHK-006", "Accessories", 25_000, 3, 15))
        assertEquals(1, inventory.products("wrap", null, StockFilter.All).size)
        assertEquals(1, inventory.products("shk", null, StockFilter.All).size)
        assertEquals(1, inventory.products("", "Accessories", StockFilter.All).size)
        assertEquals(listOf("Accessories", "Gear"), inventory.categories())
    }
}