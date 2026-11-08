package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import java.sql.Connection
import java.sql.Types

data class TxDbRow(
    val id: Long,
    val reference: String,
    val createdAt: String,
    val type: String,
    val memberName: String?,
    val method: String?,
    val totalCents: Long,
)

data class ReceiptHeadDb(
    val reference: String,
    val createdAt: String,
    val type: String,
    val subtotalCents: Long,
    val discountCents: Long,
    val totalCents: Long,
    val memberName: String?,
    val memberNumber: String?,
    val method: String?,
    val note: String?,
)

data class ReceiptItemDb(val description: String, val quantity: Int, val unitCents: Long, val totalCents: Long)

/** Line items, payments and the reads behind the Payments page and receipts. */
class PaymentRepository(private val db: DatabaseManager) {

    // ---- writes (always called inside an open transaction) ----

    fun insertItem(c: Connection, transactionId: Long, description: String, quantity: Int, unitPriceCents: Long, lineTotalCents: Long) {
        c.prepareStatement(
            "INSERT INTO transaction_items(transaction_id, description, quantity, unit_price_cents, line_total_cents) VALUES (?,?,?,?,?)"
        ).use { ps ->
            ps.setLong(1, transactionId)
            ps.setString(2, description)
            ps.setInt(3, quantity)
            ps.setLong(4, unitPriceCents)
            ps.setLong(5, lineTotalCents)
            ps.executeUpdate()
        }
    }

    fun insertPayment(c: Connection, transactionId: Long, method: String, amountCents: Long, note: String?) {
        c.prepareStatement(
            "INSERT INTO payments(transaction_id, method, amount_cents, reference_note) VALUES (?,?,?,?)"
        ).use { ps ->
            ps.setLong(1, transactionId)
            ps.setString(2, method)
            ps.setLong(3, amountCents)
            if (note == null) ps.setNull(4, Types.VARCHAR) else ps.setString(4, note)
            ps.executeUpdate()
        }
    }

    // ---- reads ----

    fun transactions(query: String): List<TxDbRow> {
        val q = query.trim()
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return db.withConnection { c ->
            c.prepareStatement(
                """
                SELECT t.id, t.reference, t.created_at, t.type, m.full_name,
                       (SELECT p.method FROM payments p WHERE p.transaction_id = t.id ORDER BY p.id LIMIT 1),
                       t.total_cents
                FROM transactions t
                LEFT JOIN members m ON m.id = t.member_id
                WHERE t.status = 'COMPLETED'
                  AND (? = '' OR t.reference LIKE ? ESCAPE '\' OR m.full_name LIKE ? ESCAPE '\' OR m.member_number LIKE ? ESCAPE '\')
                ORDER BY t.id DESC
                LIMIT 500
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, q)
                ps.setString(2, like)
                ps.setString(3, like)
                ps.setString(4, like)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<TxDbRow>()
                    while (rs.next()) {
                        list += TxDbRow(
                            rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6), rs.getLong(7),
                        )
                    }
                    list
                }
            }
        }
    }

    fun receiptHead(transactionId: Long): ReceiptHeadDb? = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT t.reference, t.created_at, t.type, t.subtotal_cents, t.discount_cents, t.total_cents,
                   m.full_name, m.member_number,
                   (SELECT p.method FROM payments p WHERE p.transaction_id = t.id ORDER BY p.id LIMIT 1),
                   (SELECT p.reference_note FROM payments p WHERE p.transaction_id = t.id ORDER BY p.id LIMIT 1)
            FROM transactions t
            LEFT JOIN members m ON m.id = t.member_id
            WHERE t.id = ?
            """.trimIndent()
        ).use { ps ->
            ps.setLong(1, transactionId)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    ReceiptHeadDb(
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getLong(4), rs.getLong(5), rs.getLong(6),
                        rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10),
                    )
                } else null
            }
        }
    }

    fun receiptItems(transactionId: Long): List<ReceiptItemDb> = db.withConnection { c ->
        c.prepareStatement(
            "SELECT description, quantity, unit_price_cents, line_total_cents FROM transaction_items WHERE transaction_id = ? ORDER BY id"
        ).use { ps ->
            ps.setLong(1, transactionId)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<ReceiptItemDb>()
                while (rs.next()) list += ReceiptItemDb(rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getLong(4))
                list
            }
        }
    }
}