package org.example.t3gymmanagementsystem

enum class StockLevel(val label: String) { InStock("In stock"), Low("Low stock"), Out("Out of stock") }

/** Filter for the product directory. All and the stock filters show active products only. */
enum class StockFilter(val label: String) {
    All("All stock levels"),
    InStock("In stock"),
    Low("Low stock"),
    Out("Out of stock"),
    Archived("Archived"),
}

data class ProductRow(
    val id: Long,
    val name: String,
    val sku: String,
    val category: String,
    val priceCents: Long,
    val stock: Int,
    val lowLevel: Int,
    val active: Boolean,
    val level: StockLevel,
)

/** id == null creates a product. Opening stock is only used when creating. */
data class ProductInput(
    val id: Long?,
    val name: String,
    val sku: String,
    val category: String,
    val priceCents: Long,
    val lowLevel: Int,
    val openingStock: Int = 0,
)

data class InventorySummary(
    val totalProducts: Int,
    val activeProducts: Int,
    val stockUnits: Int,
    val lowCount: Int,
    val outCount: Int,
)

data class MovementRow(
    val timeLabel: String,
    val productName: String,
    val sku: String,
    val kindLabel: String,
    val change: Int,
    val stockAfter: Int,
    val note: String,
)

data class CartLine(val productId: Long, val quantity: Int)

data class ProductSaleResult(val reference: String, val totalCents: Long)

data class ProductSaleRow(
    val reference: String,
    val customer: String,
    val customerNumber: String?,
    val items: String,
    val totalCents: Long,
    val timeLabel: String,
)