package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.data.database.DatabaseManager

data class PaymentStatsDb(
    val count: Int,
    val membershipCents: Long,
    val productCents: Long,
    val walkInCents: Long,
    val pendingCents: Long,
)

/** Totals for the Payments page. */
class PaymentStatsRepository(private val db: DatabaseManager) {

    fun summary(date: String): PaymentStatsDb = db.withConnection { c ->
        var count = 0
        var membership = 0L
        var product = 0L
        var walkIn = 0L
        c.prepareStatement(
            """
            SELECT type, count(*), coalesce(sum(total_cents), 0)
            FROM transactions
            WHERE status = 'COMPLETED' AND substr(created_at, 1, 10) = ?
            GROUP BY type
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, date)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    val n = rs.getInt(2)
                    val cents = rs.getLong(3)
                    count += n
                    when (rs.getString(1)) {
                        "MONTHLY_MEMBERSHIP", "DAILY_MEMBERSHIP", "MEMBERSHIP_RENEWAL" -> membership += cents
                        "PRODUCT_SALE" -> product += cents
                        "WALK_IN" -> walkIn += cents
                    }
                }
            }
        }
        val pending = c.createStatement().use { s ->
            s.executeQuery("SELECT coalesce(sum(total_cents), 0) FROM transactions WHERE status = 'PENDING'").use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
        PaymentStatsDb(count, membership, product, walkIn, pending)
    }
}