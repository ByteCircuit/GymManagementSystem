package org.example.t3gymmanagementsystem.services

import org.example.t3gymmanagementsystem.AttentionItem
import org.example.t3gymmanagementsystem.DashboardData
import org.example.t3gymmanagementsystem.RecentCheckIn
import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The figures on the dashboard. "Expiring soon" means a membership that ends within 7 days. */
class DashboardService(
    private val db: DatabaseManager,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dateTimeFormat = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)

    private fun query(c: Connection, sql: String, vararg args: String, read: (ResultSet) -> Unit) {
        c.prepareStatement(sql).use { ps ->
            args.forEachIndexed { i, a -> ps.setString(i + 1, a) }
            ps.executeQuery().use { rs -> while (rs.next()) read(rs) }
        }
    }

    private fun scalar(c: Connection, sql: String, vararg args: String): Long {
        var value = 0L
        query(c, sql, *args) { rs -> value = rs.getLong(1) }
        return value
    }

    private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

    private fun revenueOn(c: Connection, date: String): Long = scalar(
        c, "SELECT coalesce(sum(total_cents), 0) FROM transactions WHERE status = 'COMPLETED' AND substr(created_at, 1, 10) = ?", date,
    )

    private fun checkInsOn(c: Connection, date: String): Int =
        scalar(c, "SELECT count(*) FROM attendance WHERE substr(time_in, 1, 10) = ?", date).toInt()

    fun load(): DashboardData = db.withConnection { c ->
        val t = today()
        val now = t.toString()
        val soon = t.plusDays(7).toString()

        var total = 0
        var active = 0
        var expiring = 0
        var expired = 0
        var none = 0
        query(
            c,
            """
            SELECT count(*),
                   coalesce(sum(e.max_end >= ?), 0),
                   coalesce(sum(e.max_end >= ? AND e.max_end <= ?), 0),
                   coalesce(sum(e.max_end < ?), 0),
                   coalesce(sum(e.max_end IS NULL), 0)
            FROM members m
            LEFT JOIN (SELECT member_id, MAX(end_date) AS max_end FROM membership_records GROUP BY member_id) e ON e.member_id = m.id
            """.trimIndent(),
            now, now, soon, now,
        ) { rs ->
            total = rs.getInt(1)
            active = rs.getInt(2)
            expiring = rs.getInt(3)
            expired = rs.getInt(4)
            none = rs.getInt(5)
        }

        val expiredThisWeek = scalar(
            c,
            "SELECT count(*) FROM (SELECT MAX(end_date) AS me FROM membership_records GROUP BY member_id) WHERE me < ? AND me >= ?",
            now, t.minusDays(7).toString(),
        ).toInt()

        val newThisMonth = scalar(
            c,
            """
            SELECT count(*) FROM transactions
            WHERE status = 'COMPLETED' AND type IN ('MONTHLY_MEMBERSHIP', 'DAILY_MEMBERSHIP')
              AND substr(created_at, 1, 10) BETWEEN ? AND ?
            """.trimIndent(),
            t.withDayOfMonth(1).toString(), now,
        ).toInt()

        val lastWeek = t.minusDays(7)
        val low = scalar(c, "SELECT count(*) FROM products WHERE active = 1 AND stock > 0 AND stock <= low_stock_level").toInt()
        val out = scalar(c, "SELECT count(*) FROM products WHERE active = 1 AND stock = 0").toInt()

        val recent = mutableListOf<RecentCheckIn>()
        query(
            c,
            """
            SELECT m.full_name, m.member_number, a.time_in, a.source
            FROM attendance a JOIN members m ON m.id = a.member_id
            ORDER BY a.id DESC
            LIMIT 5
            """.trimIndent(),
        ) { rs ->
            val at = runCatching { LocalDateTime.parse(rs.getString(3)) }.getOrNull()
            val label = when {
                at == null -> rs.getString(3)
                at.toLocalDate() == t -> at.format(timeFormat)
                else -> at.format(dateTimeFormat)
            }
            recent += RecentCheckIn(rs.getString(1), rs.getString(2), label, if (rs.getString(4) == "NFC") "Card" else "Manual")
        }

        val attention = mutableListOf<AttentionItem>()
        if (expiredThisWeek > 0) {
            attention += AttentionItem("MEMBERS", "${plural(expiredThisWeek, "membership", "memberships")} expired in the last 7 days", "Review and contact members")
        }
        if (expiring > 0) {
            attention += AttentionItem("MEMBERS", "${plural(expiring, "membership", "memberships")} ending within 7 days", "Remind them to renew")
        }
        if (low + out > 0) {
            attention += AttentionItem("INVENTORY", "${plural(low + out, "product is", "products are")} low or out of stock", "Review inventory levels")
        }

        DashboardData(
            totalMembers = total,
            activeMembers = active,
            expiringSoon = expiring,
            expiredMembers = expired,
            noMembership = none,
            newThisMonth = newThisMonth,
            checkInsToday = checkInsOn(c, now),
            checkInsLastWeek = checkInsOn(c, lastWeek.toString()),
            lastWeekDay = lastWeek.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            revenueTodayCents = revenueOn(c, now),
            revenueYesterdayCents = revenueOn(c, t.minusDays(1).toString()),
            lowStock = low,
            outOfStock = out,
            recent = recent,
            attention = attention,
        )
    }
}