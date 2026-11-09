package org.example.t3gymmanagementsystem.services

import org.example.t3gymmanagementsystem.CartLine
import org.example.t3gymmanagementsystem.InventorySummary
import org.example.t3gymmanagementsystem.MovementRow
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.PaymentInfo
import org.example.t3gymmanagementsystem.ProductInput
import org.example.t3gymmanagementsystem.ProductRow
import org.example.t3gymmanagementsystem.ProductSaleResult
import org.example.t3gymmanagementsystem.ProductSaleRow
import org.example.t3gymmanagementsystem.StockFilter
import org.example.t3gymmanagementsystem.StockLevel
import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.PaymentRepository
import org.example.t3gymmanagementsystem.data.repositories.ProductDb
import org.example.t3gymmanagementsystem.data.repositories.ProductRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Products, stock and product sales. A sale is one transaction: the items, the payment and the stock movements are written together. */
class InventoryService(
    private val db: DatabaseManager,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val repo = ProductRepository(db)
    private val members = MemberRepository(db)
    private val payments = PaymentRepository(db)

    private val labelFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)

    private fun label(iso: String): String =
        runCatching { LocalDateTime.parse(iso).format(labelFormat) }.getOrDefault(iso)

    private fun toRow(p: ProductDb) = ProductRow(
        p.id, p.name, p.sku, p.category, p.priceCents, p.stock, p.lowLevel, p.active,
        when {
            p.stock == 0 -> StockLevel.Out
            p.stock <= p.lowLevel -> StockLevel.Low
            else -> StockLevel.InStock
        },
    )

    // ---- reads ----

    fun summary(): InventorySummary {
        val c = repo.counts()
        return InventorySummary(c.total, c.active, c.units, c.low, c.out)
    }

    fun lowStockCount(): Int = repo.counts().low

    /** The member number of the active card with this ID, or null when the card is not assigned to anyone. */
    fun memberNumberByCard(uid: String): String? {
        val clean = AttendanceService.normalizeUid(uid) ?: uid.trim().uppercase()
        return repo.memberNumberByCard(clean)
    }

    fun categories(): List<String> = repo.categories()

    fun products(query: String, category: String?, filter: StockFilter): List<ProductRow> =
        repo.list(query, category).map(::toRow).filter {
            when (filter) {
                StockFilter.All -> it.active
                StockFilter.InStock -> it.active && it.level == StockLevel.InStock
                StockFilter.Low -> it.active && it.level == StockLevel.Low
                StockFilter.Out -> it.active && it.level == StockLevel.Out
                StockFilter.Archived -> !it.active
            }
        }

    fun movements(): List<MovementRow> = repo.movements(1000).map {
        MovementRow(
            label(it.at), it.productName, it.sku,
            when (it.kind) {
                "INITIAL" -> "Opening stock"
                "RESTOCK" -> "Restock"
                "SALE" -> "Sale"
                else -> if (it.note.orEmpty().startsWith("Void ")) "Voided sale" else "Adjustment"
            },
            it.change, it.after, it.note.orEmpty(),
        )
    }

    fun recentSales(): List<ProductSaleRow> = repo.recentSales(10).map {
        ProductSaleRow(it.reference, it.memberName ?: "Walk-in customer", it.memberNumber, it.items.orEmpty(), it.totalCents, label(it.at))
    }

    // ---- products ----

    fun saveProduct(input: ProductInput): Outcome<Long> {
        val name = input.name.trim()
        val sku = input.sku.trim().uppercase()
        val category = input.category.trim()
        if (name.isEmpty()) return Outcome.Fail("Enter the product name.")
        if (sku.isEmpty()) return Outcome.Fail("Enter the SKU.")
        if (category.isEmpty()) return Outcome.Fail("Enter the category.")
        if (input.priceCents <= 0) return Outcome.Fail("Enter a selling price above zero.")
        if (input.lowLevel < 0) return Outcome.Fail("The low-stock level cannot be negative.")
        if (input.openingStock < 0) return Outcome.Fail("Opening stock cannot be negative.")
        val id = input.id
        return db.transaction<Outcome<Long>> { c ->
            if (repo.skuTaken(c, sku, id)) {
                Outcome.Fail("SKU $sku is already used by another product.")
            } else if (id == null) {
                val newId = repo.insert(c, name, sku, category, input.priceCents, input.openingStock, input.lowLevel)
                if (input.openingStock > 0) repo.insertMovement(c, newId, "INITIAL", input.openingStock, input.openingStock, "Opening stock", null)
                Outcome.Ok(newId)
            } else if (repo.update(c, id, name, sku, category, input.priceCents, input.lowLevel)) {
                Outcome.Ok(id)
            } else {
                Outcome.Fail("That product no longer exists.")
            }
        }
    }

    fun setActive(productId: Long, active: Boolean): Outcome<Unit> = db.transaction<Outcome<Unit>> { c ->
        if (repo.setActive(c, productId, active)) Outcome.Ok(Unit) else Outcome.Fail("That product no longer exists.")
    }

    // ---- stock ----

    fun restock(productId: Long, quantity: Int, note: String): Outcome<Unit> {
        if (quantity <= 0) return Outcome.Fail("Enter how many units arrived.")
        return db.transaction<Outcome<Unit>> { c ->
            val p = repo.get(c, productId)
            if (p == null) {
                Outcome.Fail("That product no longer exists.")
            } else {
                repo.changeStock(c, productId, quantity)
                repo.insertMovement(c, productId, "RESTOCK", quantity, p.stock + quantity, note.trim().ifEmpty { null }, null)
                Outcome.Ok(Unit)
            }
        }
    }

    /** Sets the stock to a counted number. A reason is required so the movement log explains the difference. */
    fun adjust(productId: Long, counted: Int, reason: String): Outcome<Unit> {
        if (counted < 0) return Outcome.Fail("The counted stock cannot be negative.")
        if (reason.isBlank()) return Outcome.Fail("Enter a reason for the adjustment.")
        return db.transaction<Outcome<Unit>> { c ->
            val p = repo.get(c, productId)
            if (p == null) {
                Outcome.Fail("That product no longer exists.")
            } else if (counted == p.stock) {
                Outcome.Fail("The count matches the current stock. Nothing to adjust.")
            } else {
                val delta = counted - p.stock
                repo.changeStock(c, productId, delta)
                repo.insertMovement(c, productId, "ADJUSTMENT", delta, counted, reason.trim(), null)
                Outcome.Ok(Unit)
            }
        }
    }

    // ---- point of sale ----

    /** Cancels a completed product sale: stock goes back and the sale stops counting in payments and reports. */
    fun voidSale(reference: String, reason: String): Outcome<Unit> {
        val why = reason.trim()
        if (why.length < 3) return Outcome.Fail("Enter a reason for voiding this sale.")
        return db.transaction<Outcome<Unit>> { c ->
            val sale = repo.findSale(c, reference.trim())
            if (sale == null) {
                Outcome.Fail("That sale was not found.")
            } else if (sale.status != "COMPLETED") {
                Outcome.Fail("That sale is already ${sale.status.lowercase()}.")
            } else {
                for (line in repo.saleLines(c, sale.id)) {
                    if (!repo.changeStock(c, line.productId, line.quantity)) throw IllegalStateException("Could not return stock for a product in this sale.")
                    val after = repo.get(c, line.productId)?.stock ?: 0
                    repo.insertMovement(c, line.productId, "ADJUSTMENT", line.quantity, after, "Void $reference: $why", sale.id)
                }
                if (!repo.markCancelled(c, sale.id)) throw IllegalStateException("The sale could not be cancelled.")
                Outcome.Ok(Unit)
            }
        }
    }

    fun sell(memberId: Long?, lines: List<CartLine>, payment: PaymentInfo): Outcome<ProductSaleResult> {
        val merged = lines.filter { it.quantity > 0 }
            .groupBy { it.productId }
            .map { (id, group) -> CartLine(id, group.sumOf { it.quantity }) }
        if (merged.isEmpty()) return Outcome.Fail("Add at least one product to the sale.")

        return db.transaction<Outcome<ProductSaleResult>> { c ->
            // read and check everything first, so a refusal writes nothing
            val resolved = ArrayList<Pair<ProductDb, Int>>()
            var problem: String? = null
            for (line in merged) {
                val p = repo.get(c, line.productId)
                val issue = when {
                    p == null -> "A product in the cart no longer exists."
                    !p.active -> "${p.name} is archived and cannot be sold."
                    p.stock == 0 -> "${p.name} is out of stock."
                    line.quantity > p.stock -> "Only ${p.stock} left of ${p.name}."
                    else -> null
                }
                if (issue != null || p == null) {
                    problem = issue ?: "A product in the cart no longer exists."
                    break
                }
                resolved += p to line.quantity
            }
            val failure = problem
            if (failure != null) {
                Outcome.Fail(failure)
            } else {
                val total = resolved.sumOf { (p, q) -> p.priceCents * q }
                val reference = members.nextReference(c, today())
                val transactionId = repo.insertSaleTransaction(c, reference, memberId, total)
                for ((p, q) in resolved) {
                    repo.insertSaleItem(c, transactionId, p.id, p.name, q, p.priceCents, p.priceCents * q)
                    if (!repo.changeStock(c, p.id, -q)) throw IllegalStateException("${p.name} just ran out of stock.")
                    repo.insertMovement(c, p.id, "SALE", -q, p.stock - q, reference, transactionId)
                }
                payments.insertPayment(c, transactionId, payment.method.code, total, payment.note.trim().ifEmpty { null })
                Outcome.Ok(ProductSaleResult(reference, total))
            }
        }
    }
}