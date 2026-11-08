package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.MembershipKind
import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

data class PlanRow(val id: Long, val name: String, val kind: MembershipKind, val priceCents: Long, val active: Boolean)

class PlanRepository(private val db: DatabaseManager) {

    private fun code(kind: MembershipKind) = if (kind == MembershipKind.Monthly) "MONTHLY" else "DAILY"

    private fun ResultSet.toPlan() = PlanRow(
        getLong(1), getString(2),
        if (getString(3) == "MONTHLY") MembershipKind.Monthly else MembershipKind.Daily,
        getLong(4), getInt(5) == 1,
    )

    private val columns = "id, name, duration, price_cents, active"

    fun count(): Int = db.withConnection { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM membership_plans").use { rs -> rs.next(); rs.getInt(1) } }
    }

    /** Active plans only, oldest first. */
    fun list(): List<PlanRow> = db.withConnection { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT $columns FROM membership_plans WHERE active = 1 ORDER BY id").use { rs ->
                val list = mutableListOf<PlanRow>()
                while (rs.next()) list += rs.toPlan()
                list
            }
        }
    }

    fun find(id: Long): PlanRow? = db.withConnection { c ->
        c.prepareStatement("SELECT $columns FROM membership_plans WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) rs.toPlan() else null }
        }
    }

    fun firstActive(kind: MembershipKind): PlanRow? = db.withConnection { c ->
        c.prepareStatement("SELECT $columns FROM membership_plans WHERE active = 1 AND duration = ? ORDER BY id LIMIT 1").use { ps ->
            ps.setString(1, code(kind))
            ps.executeQuery().use { rs -> if (rs.next()) rs.toPlan() else null }
        }
    }

    fun activeNameExists(name: String, exceptId: Long?): Boolean = db.withConnection { c ->
        c.prepareStatement("SELECT 1 FROM membership_plans WHERE active = 1 AND lower(name) = lower(?) AND id <> ? LIMIT 1").use { ps ->
            ps.setString(1, name)
            ps.setLong(2, exceptId ?: -1L)
            ps.executeQuery().use { rs -> rs.next() }
        }
    }

    fun usage(id: Long): Int = db.withConnection { c ->
        c.prepareStatement("SELECT count(*) FROM membership_records WHERE plan_id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    fun insert(name: String, kind: MembershipKind, priceCents: Long): Long = db.transaction<Long> { c ->
        c.prepareStatement("INSERT INTO membership_plans(name, duration, price_cents) VALUES (?,?,?)", Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, name)
            ps.setString(2, code(kind))
            ps.setLong(3, priceCents)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    fun update(id: Long, name: String, kind: MembershipKind, priceCents: Long) {
        db.transaction<Unit> { c ->
            c.prepareStatement("UPDATE membership_plans SET name = ?, duration = ?, price_cents = ? WHERE id = ?").use { ps ->
                ps.setString(1, name)
                ps.setString(2, code(kind))
                ps.setLong(3, priceCents)
                ps.setLong(4, id)
                ps.executeUpdate()
            }
        }
    }

    fun delete(id: Long) {
        db.transaction<Unit> { c ->
            c.prepareStatement("DELETE FROM membership_plans WHERE id = ?").use { ps ->
                ps.setLong(1, id)
                ps.executeUpdate()
            }
        }
    }

    fun archive(id: Long) {
        db.transaction<Unit> { c ->
            c.prepareStatement("UPDATE membership_plans SET active = 0 WHERE id = ?").use { ps ->
                ps.setLong(1, id)
                ps.executeUpdate()
            }
        }
    }

    /** Records which plan a sale was for, so history keeps the plan name even after the plan is archived. */
    fun tagMembership(c: Connection, transactionId: Long, planId: Long, planName: String) {
        c.prepareStatement("UPDATE membership_records SET plan_id = ?, plan_name = ? WHERE transaction_id = ?").use { ps ->
            ps.setLong(1, planId)
            ps.setString(2, planName)
            ps.setLong(3, transactionId)
            ps.executeUpdate()
        }
    }

    /** Transaction reference to plan name, for one member's memberships. */
    fun planNamesByReference(memberId: Long): Map<String, String> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT t.reference, mr.plan_name
            FROM membership_records mr
            JOIN transactions t ON t.id = mr.transaction_id
            WHERE mr.member_id = ? AND mr.plan_name IS NOT NULL
            """.trimIndent()
        ).use { ps ->
            ps.setLong(1, memberId)
            ps.executeQuery().use { rs ->
                val map = mutableMapOf<String, String>()
                while (rs.next()) map[rs.getString(1)] = rs.getString(2)
                map
            }
        }
    }

    /** Member id to the plan name of that member's latest membership. */
    fun lastPlanNames(): Map<Long, String> = db.withConnection { c ->
        c.createStatement().use { s ->
            s.executeQuery(
                """
                SELECT member_id, plan_name FROM membership_records
                WHERE id IN (SELECT MAX(id) FROM membership_records GROUP BY member_id) AND plan_name IS NOT NULL
                """.trimIndent()
            ).use { rs ->
                val map = mutableMapOf<Long, String>()
                while (rs.next()) map[rs.getLong(1)] = rs.getString(2)
                map
            }
        }
    }
}