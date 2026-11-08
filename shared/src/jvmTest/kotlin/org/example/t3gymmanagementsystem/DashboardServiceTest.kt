package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.services.DashboardService
import org.example.t3gymmanagementsystem.services.InventoryService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DashboardServiceTest {
    private lateinit var dir: Path
    private lateinit var inventory: InventoryService
    private lateinit var dashboard: DashboardService

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-dashboard")
        val db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        inventory = InventoryService(db)
        dashboard = DashboardService(db)
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun emptyDatabaseIsAllZeros() {
        val d = dashboard.load()
        assertEquals(0, d.totalMembers)
        assertEquals(0, d.checkInsToday)
        assertEquals(0L, d.revenueTodayCents)
        assertEquals(0, d.lowStock + d.outOfStock)
        assertTrue(d.recent.isEmpty())
        assertTrue(d.attention.isEmpty())
    }

    @Test
    fun salesAndStockShowUp() {
        val gloves = assertIs<Outcome.Ok<Long>>(inventory.saveProduct(ProductInput(null, "Gloves", "GLO-001", "Gear", 85_000, 5, 10))).value
        inventory.saveProduct(ProductInput(null, "Water", "BTL-004", "Accessories", 32_000, 5, 3))
        inventory.saveProduct(ProductInput(null, "Bag", "BAG-005", "Accessories", 120_000, 5, 0))
        assertIs<Outcome.Ok<ProductSaleResult>>(inventory.sell(null, listOf(CartLine(gloves, 2)), PaymentInfo()))

        val d = dashboard.load()
        assertEquals(170_000L, d.revenueTodayCents)
        assertEquals(1, d.lowStock)
        assertEquals(1, d.outOfStock)
        val stockItem = d.attention.single { it.target == "INVENTORY" }
        assertTrue(stockItem.title.startsWith("2 products are"))
    }
}