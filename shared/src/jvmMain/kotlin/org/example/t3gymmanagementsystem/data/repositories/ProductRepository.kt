package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types

data class ProductDb(
    val id: Long,
    val name: String,
    val sku: String,
    val category: String,
    val priceCents: Long,
    val stock: Int,
    val lowLevel: Int,
    val active: Boolean,
)

data class CountsDb(val total: Int, val active: Int, val units: Int, val low: Int, val out: Int)

data class MovementDb(
    val at: String,
    val productName: String,
    val sku: String,
    val kind: String,
    val change: Int,
    val after: Int,
    val note: String?,
)

data class ProductSaleDb(
    val reference: String,
    val at: String,
    val memberName: String?,
    val memberNumber: String?,
    val items: String?,
    val totalCents: Long,
)

class ProductRepository(private val db: DatabaseManager) {

    private fun product(rs: ResultSet) = ProductDb(
        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
        rs.getLong(5), rs.getInt(6), rs.getInt(7), rs.getInt(8) == 1,
    )

    // ---- reads ----

    fun list(query: String, category: String?): List<ProductDb> {
        val q = query.trim()
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val sql = StringBuilder("SELECT $COLS FROM products WHERE (? = '' OR name LIKE ? ESCAPE '\\' OR sku LIKE ? ESCAPE '\\')")
        if (category != null) sql.append(" AND category = ?")
        sql.append(" ORDER BY name COLLATE NOCASE LIMIT 1000")
        return db.withConnection { c ->
            c.prepareStatement(sql.toString()).use { ps ->
                ps.setString(1, q)
                ps.setString(2, like)
                ps.setString(3, like)
                if (category != null) ps.setString(4, category)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<ProductDb>()
                    while (rs.next()) list += product(rs)
                    list
                }
            }
        }
    }

    fun get(c: Connection, id: Long): ProductDb? =
        c.prepareStatement("SELECT $COLS FROM products WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) product(rs) else null }
        }

    fun skuTaken(c: Connection, sku: String, exceptId: Long?): Boolean =
        c.prepareStatement("SELECT 1 FROM products WHERE sku = ? AND id <> ?").use { ps ->
            ps.setString(1, sku)
            ps.setLong(2, exceptId ?: -1L)
            ps.executeQuery().use { rs -> rs.next() }
        }

    fun memberNumberByCard(uid: String): String? = db.withConnection { c ->
        c.prepareStatement(
            "SELECT m.member_number FROM nfc_cards n JOIN members m ON m.id = n.member_id WHERE n.uid = ? AND n.active = 1 LIMIT 1"
        ).use { ps ->
            ps.setString(1, uid)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun categories(): List<String> = db.withConnection { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT DISTINCT category FROM products WHERE active = 1 ORDER BY category COLLATE NOCASE").use { rs ->
                val list = mutableListOf<String>()
                while (rs.next()) list += rs.getString(1)
                list
            }
        }
    }

    fun counts(): CountsDb = db.withConnection { c ->
        c.createStatement().use { s ->
            s.executeQuery(
                """
                SELECT count(*),
                       coalesce(sum(active = 1), 0),
                       coalesce(sum(CASE WHEN active = 1 THEN stock ELSE 0 END), 0),
                       coalesce(sum(active = 1 AND stock > 0 AND stock <= low_stock_level), 0),
                       coalesce(sum(active = 1 AND stock = 0), 0)
                FROM products
                """.trimIndent()
            ).use { rs ->
                rs.next()
                CountsDb(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5))
            }
        }
    }

    fun movements(limit: Int): List<MovementDb> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT m.created_at, p.name, p.sku, m.kind, m.change_qty, m.stock_after, m.note
            FROM inventory_movements m
            JOIN products p ON p.id = m.product_id
            ORDER BY m.id DESC
            LIMIT ?
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<MovementDb>()
                while (rs.next()) {
                    list += MovementDb(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6), rs.getString(7))
                }
                list
            }
        }
    }

    fun recentSales(limit: Int): List<ProductSaleDb> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT t.reference, t.created_at, m.full_name, m.member_number,
                   (SELECT group_concat(i.description || ' x' || i.quantity, ', ') FROM transaction_items i WHERE i.transaction_id = t.id),
                   t.total_cents
            FROM transactions t
            LEFT JOIN members m ON m.id = t.member_id
            WHERE t.type = 'PRODUCT_SALE' AND t.status = 'COMPLETED'
            ORDER BY t.id DESC
            LIMIT ?
            """.trimIndent()
        ).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<ProductSaleDb>()
                while (rs.next()) {
                    list += ProductSaleDb(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getLong(6))
                }
                list
            }
        }
    }

    // ---- writes (called inside an open transaction) ----

    fun insert(c: Connection, name: String, sku: String, category: String, priceCents: Long, stock: Int, lowLevel: Int): Long =
        c.prepareStatement(
            "INSERT INTO products(name, sku, category, price_cents, stock, low_stock_level) VALUES (?,?,?,?,?,?)",
            Statement.RETURN_GENERATED_KEYS,
        ).use { ps ->
            ps.setString(1, name)
            ps.setString(2, sku)
            ps.setString(3, category)
            ps.setLong(4, priceCents)
            ps.setInt(5, stock)
            ps.setInt(6, lowLevel)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> rs.next(); rs.getLong(1) }
        }

    fun update(c: Connection, id: Long, name: String, sku: String, category: String, priceCents: Long, lowLevel: Int): Boolean =
        c.prepareStatement("UPDATE products SET name = ?, sku = ?, category = ?, price_cents = ?, low_stock_level = ? WHERE id = ?").use { ps ->
            ps.setString(1, name)
            ps.setString(2, sku)
            ps.setString(3, category)
            ps.setLong(4, priceCents)
            ps.setInt(5, lowLevel)
            ps.setLong(6, id)
            ps.executeUpdate() == 1
        }

    fun setActive(c: Connection, id: Long, active: Boolean): Boolean =
        c.prepareStatement("UPDATE products SET active = ? WHERE id = ?").use { ps ->
            ps.setInt(1, if (active) 1 else 0)
            ps.setLong(2, id)
            ps.executeUpdate() == 1
        }

    /** Adds delta to the stock. Returns false (and changes nothing) when the stock would go below zero. */
    fun changeStock(c: Connection, id: Long, delta: Int): Boolean =
        c.prepareStatement("UPDATE products SET stock = stock + ? WHERE id = ? AND stock + ? >= 0").use { ps ->
            ps.setInt(1, delta)
            ps.setLong(2, id)
            ps.setInt(3, delta)
            ps.executeUpdate() == 1
        }

    fun insertMovement(c: Connection, productId: Long, kind: String, change: Int, after: Int, note: String?, transactionId: Long?) {
        c.prepareStatement(
            "INSERT INTO inventory_movements(product_id, kind, change_qty, stock_after, note, transaction_id) VALUES (?,?,?,?,?,?)"
        ).use { ps ->
            ps.setLong(1, productId)
            ps.setString(2, kind)
            ps.setInt(3, change)
            ps.setInt(4, after)
            if (note == null) ps.setNull(5, Types.VARCHAR) else ps.setString(5, note)
            if (transactionId == null) ps.setNull(6, Types.INTEGER) else ps.setLong(6, transactionId)
            ps.executeUpdate()
        }
    }

    fun insertSaleTransaction(c: Connection, reference: String, memberId: Long?, totalCents: Long): Long =
        c.prepareStatement(
            "INSERT INTO transactions(reference, type, member_id, subtotal_cents, discount_cents, total_cents) VALUES (?, 'PRODUCT_SALE', ?, ?, 0, ?)",
            Statement.RETURN_GENERATED_KEYS,
        ).use { ps ->
            ps.setString(1, reference)
            if (memberId == null) ps.setNull(2, Types.INTEGER) else ps.setLong(2, memberId)
            ps.setLong(3, totalCents)
            ps.setLong(4, totalCents)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> rs.next(); rs.getLong(1) }
        }

    fun insertSaleItem(c: Connection, transactionId: Long, productId: Long, description: String, quantity: Int, unitCents: Long, lineCents: Long) {
        c.prepareStatement(
            "INSERT INTO transaction_items(transaction_id, product_id, description, quantity, unit_price_cents, line_total_cents) VALUES (?,?,?,?,?,?)"
        ).use { ps ->
            ps.setLong(1, transactionId)
            ps.setLong(2, productId)
            ps.setString(3, description)
            ps.setInt(4, quantity)
            ps.setLong(5, unitCents)
            ps.setLong(6, lineCents)
            ps.executeUpdate()
        }
    }

    private companion object {
        const val COLS = "id, name, sku, category, price_cents, stock, low_stock_level, active"
    }
}