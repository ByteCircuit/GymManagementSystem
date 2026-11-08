package org.example.t3gymmanagementsystem.services

import org.example.t3gymmanagementsystem.DayPoint
import org.example.t3gymmanagementsystem.MethodLine
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.PaymentMethod
import org.example.t3gymmanagementsystem.PeriodFigures
import org.example.t3gymmanagementsystem.PlanLine
import org.example.t3gymmanagementsystem.ReportBundle
import org.example.t3gymmanagementsystem.ReportPreset
import org.example.t3gymmanagementsystem.ReportRange
import org.example.t3gymmanagementsystem.StatusCounts
import org.example.t3gymmanagementsystem.StockAlert
import org.example.t3gymmanagementsystem.TopProduct
import org.example.t3gymmanagementsystem.TypeLine
import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Read-only figures for the Reports page, always computed from the saved records. */
class ReportService(
    private val db: DatabaseManager,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    fun rangeFor(preset: ReportPreset): ReportRange {
        val t = today()
        val start = when (preset) {
            ReportPreset.Today -> t
            ReportPreset.Last7 -> t.minusDays(6)
            ReportPreset.Last30 -> t.minusDays(29)
            ReportPreset.Last90 -> t.minusDays(89)
            ReportPreset.ThisMonth -> t.withDayOfMonth(1)
            ReportPreset.ThisYear -> t.withDayOfYear(1)
        }
        return ReportRange(start.toString(), t.toString())
    }

    fun build(from: String, to: String): Outcome<ReportBundle> {
        val start = runCatching { LocalDate.parse(from.trim()) }.getOrNull() ?: return Outcome.Fail("Enter the From date as YYYY-MM-DD.")
        val end = runCatching { LocalDate.parse(to.trim()) }.getOrNull() ?: return Outcome.Fail("Enter the To date as YYYY-MM-DD.")
        if (end.isBefore(start)) return Outcome.Fail("The To date must not be before the From date.")
        val days = ChronoUnit.DAYS.between(start, end).toInt() + 1
        if (days > 366) return Outcome.Fail("Choose a range of at most one year.")
        val previousEnd = start.minusDays(1)
        val previousStart = previousEnd.minusDays(days - 1L)
        return Outcome.Ok(db.withConnection { c -> assemble(c, start, end, previousStart, previousEnd, days) })
    }

    // ---- helpers ----

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

    private fun typeLabel(code: String) = when (code) {
        "MONTHLY_MEMBERSHIP" -> "Monthly membership"
        "DAILY_MEMBERSHIP" -> "Daily membership"
        "MEMBERSHIP_RENEWAL" -> "Membership renewal"
        "WALK_IN" -> "Walk-in"
        "PRODUCT_SALE" -> "Product sale"
        else -> code
    }

    private fun hourLabel(hour: Int): String {
        val suffix = if (hour < 12) "AM" else "PM"
        val h = if (hour % 12 == 0) 12 else hour % 12
        return "$h $suffix"
    }

    private fun figures(c: Connection, from: String, to: String): PeriodFigures {
        var membership = 0L
        var product = 0L
        var walkIn = 0L
        var discount = 0L
        var transactions = 0
        var newMembers = 0
        var renewals = 0
        query(
            c,
            """
            SELECT type, count(*), coalesce(sum(total_cents), 0), coalesce(sum(discount_cents), 0)
            FROM transactions
            WHERE status = 'COMPLETED' AND substr(created_at, 1, 10) BETWEEN ? AND ?
            GROUP BY type
            """.trimIndent(),
            from, to,
        ) { rs ->
            val n = rs.getInt(2)
            val cents = rs.getLong(3)
            transactions += n
            discount += rs.getLong(4)
            when (rs.getString(1)) {
                "MONTHLY_MEMBERSHIP", "DAILY_MEMBERSHIP" -> { membership += cents; newMembers += n }
                "MEMBERSHIP_RENEWAL" -> { membership += cents; renewals += n }
                "PRODUCT_SALE" -> product += cents
                "WALK_IN" -> walkIn += cents
            }
        }
        val checkIns = scalar(c, "SELECT count(*) FROM attendance WHERE substr(time_in, 1, 10) BETWEEN ? AND ?", from, to).toInt()
        val walkIns = scalar(c, "SELECT count(*) FROM walk_in_visits WHERE substr(visited_at, 1, 10) BETWEEN ? AND ?", from, to).toInt()
        val units = scalar(
            c,
            """
            SELECT coalesce(sum(i.quantity), 0)
            FROM transaction_items i JOIN transactions t ON t.id = i.transaction_id
            WHERE i.product_id IS NOT NULL AND t.status = 'COMPLETED' AND substr(t.created_at, 1, 10) BETWEEN ? AND ?
            """.trimIndent(),
            from, to,
        ).toInt()
        return PeriodFigures(membership, product, walkIn, discount, transactions, newMembers, renewals, checkIns, walkIns, units)
    }

    private fun assemble(c: Connection, start: LocalDate, end: LocalDate, previousStart: LocalDate, previousEnd: LocalDate, days: Int): ReportBundle {
        val s = start.toString()
        val e = end.toString()
        val current = figures(c, s, e)
        val previous = figures(c, previousStart.toString(), previousEnd.toString())

        // one point per day, including days with nothing
        val money = HashMap<String, LongArray>()
        query(
            c,
            """
            SELECT substr(created_at, 1, 10), type, coalesce(sum(total_cents), 0)
            FROM transactions
            WHERE status = 'COMPLETED' AND substr(created_at, 1, 10) BETWEEN ? AND ?
            GROUP BY 1, 2
            """.trimIndent(),
            s, e,
        ) { rs ->
            val row = money.getOrPut(rs.getString(1)) { LongArray(3) }
            when (rs.getString(2)) {
                "MONTHLY_MEMBERSHIP", "DAILY_MEMBERSHIP", "MEMBERSHIP_RENEWAL" -> row[0] += rs.getLong(3)
                "PRODUCT_SALE" -> row[1] += rs.getLong(3)
                "WALK_IN" -> row[2] += rs.getLong(3)
            }
        }
        val checks = HashMap<String, Int>()
        query(c, "SELECT substr(time_in, 1, 10), count(*) FROM attendance WHERE substr(time_in, 1, 10) BETWEEN ? AND ? GROUP BY 1", s, e) { rs ->
            checks[rs.getString(1)] = rs.getInt(2)
        }
        val walks = HashMap<String, Int>()
        query(c, "SELECT substr(visited_at, 1, 10), count(*) FROM walk_in_visits WHERE substr(visited_at, 1, 10) BETWEEN ? AND ? GROUP BY 1", s, e) { rs ->
            walks[rs.getString(1)] = rs.getInt(2)
        }
        val daily = (0 until days).map { i ->
            val d = start.plusDays(i.toLong()).toString()
            val m = money[d]
            DayPoint(d, m?.get(0) ?: 0L, m?.get(1) ?: 0L, m?.get(2) ?: 0L, checks[d] ?: 0, walks[d] ?: 0)
        }

        val plans = mutableListOf<PlanLine>()
        query(
            c,
            """
            SELECT coalesce(p.name, r.type),
                   coalesce(sum(t.type <> 'MEMBERSHIP_RENEWAL'), 0),
                   coalesce(sum(t.type = 'MEMBERSHIP_RENEWAL'), 0),
                   coalesce(sum(t.total_cents), 0)
            FROM transactions t
            JOIN membership_records r ON r.transaction_id = t.id
            LEFT JOIN membership_plans p ON p.id = r.plan_id
            WHERE t.status = 'COMPLETED' AND substr(t.created_at, 1, 10) BETWEEN ? AND ?
            GROUP BY 1
            ORDER BY 4 DESC
            """.trimIndent(),
            s, e,
        ) { rs -> plans += PlanLine(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getLong(4)) }

        val methods = mutableListOf<MethodLine>()
        query(
            c,
            """
            SELECT p.method, count(*), coalesce(sum(p.amount_cents), 0)
            FROM payments p JOIN transactions t ON t.id = p.transaction_id
            WHERE t.status = 'COMPLETED' AND substr(t.created_at, 1, 10) BETWEEN ? AND ?
            GROUP BY p.method
            ORDER BY 3 DESC
            """.trimIndent(),
            s, e,
        ) { rs ->
            val code = rs.getString(1)
            methods += MethodLine(PaymentMethod.entries.firstOrNull { it.code == code }?.label ?: code, rs.getInt(2), rs.getLong(3))
        }

        val types = mutableListOf<TypeLine>()
        query(
            c,
            """
            SELECT type, count(*), coalesce(sum(total_cents), 0)
            FROM transactions
            WHERE status = 'COMPLETED' AND substr(created_at, 1, 10) BETWEEN ? AND ?
            GROUP BY type
            ORDER BY 3 DESC
            """.trimIndent(),
            s, e,
        ) { rs -> types += TypeLine(typeLabel(rs.getString(1)), rs.getInt(2), rs.getLong(3)) }

        val top = mutableListOf<TopProduct>()
        query(
            c,
            """
            SELECT i.description, coalesce(pr.sku, ''), sum(i.quantity), sum(i.line_total_cents)
            FROM transaction_items i
            JOIN transactions t ON t.id = i.transaction_id
            LEFT JOIN products pr ON pr.id = i.product_id
            WHERE i.product_id IS NOT NULL AND t.status = 'COMPLETED' AND substr(t.created_at, 1, 10) BETWEEN ? AND ?
            GROUP BY i.product_id
            ORDER BY 3 DESC, 4 DESC
            LIMIT 10
            """.trimIndent(),
            s, e,
        ) { rs -> top += TopProduct(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getLong(4)) }

        var members = StatusCounts(0, 0, 0, 0)
        val now = today().toString()
        query(
            c,
            """
            SELECT count(*),
                   coalesce(sum(e.max_end >= ?), 0),
                   coalesce(sum(e.max_end < ?), 0),
                   coalesce(sum(e.max_end IS NULL), 0)
            FROM members m
            LEFT JOIN (SELECT member_id, MAX(end_date) AS max_end FROM membership_records GROUP BY member_id) e ON e.member_id = m.id
            """.trimIndent(),
            now, now,
        ) { rs -> members = StatusCounts(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)) }

        val distinct = scalar(c, "SELECT count(DISTINCT member_id) FROM attendance WHERE substr(time_in, 1, 10) BETWEEN ? AND ?", s, e).toInt()
        val denied = scalar(c, "SELECT count(*) FROM denied_scans WHERE substr(scanned_at, 1, 10) BETWEEN ? AND ?", s, e).toInt()

        var peak: String? = null
        query(
            c,
            "SELECT substr(time_in, 12, 2) AS h, count(*) AS n FROM attendance WHERE substr(time_in, 1, 10) BETWEEN ? AND ? GROUP BY h ORDER BY n DESC, h LIMIT 1",
            s, e,
        ) { rs -> peak = rs.getString(1)?.toIntOrNull()?.let { hourLabel(it) } }

        val alerts = mutableListOf<StockAlert>()
        query(
            c,
            "SELECT name, sku, stock, low_stock_level FROM products WHERE active = 1 AND stock <= low_stock_level ORDER BY stock, name COLLATE NOCASE LIMIT 50",
        ) { rs -> alerts += StockAlert(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4)) }

        return ReportBundle(
            s, e, previousStart.toString(), previousEnd.toString(), days, current, previous,
            daily, plans, methods, types, top, members, distinct, denied, peak, alerts,
        )
    }
}