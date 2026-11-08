package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import java.sql.Connection
import java.sql.Statement
import java.sql.Types

data class CardRow(val memberId: Long, val uid: String, val assignedAt: String)
data class AttendanceDbRow(val timeIn: String, val memberName: String, val memberNumber: String, val source: String, val logId: Long = 0, val cardUid: String? = null)
data class WalkInDbRow(val visitedAt: String, val visitor: String?, val totalCents: Long, val reference: String)

/** NFC cards, attendance and walk-in visits. */
class AttendanceRepository(private val db: DatabaseManager) {

    // ---- cards ----

    fun memberIdForCard(uid: String): Long? = db.withConnection { c ->
        c.prepareStatement("SELECT member_id FROM nfc_cards WHERE uid = ? AND active = 1").use { ps ->
            ps.setString(1, uid)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }
    }

    fun activeCard(memberId: Long): CardRow? = db.withConnection { c ->
        c.prepareStatement(
            "SELECT member_id, uid, assigned_at FROM nfc_cards WHERE member_id = ? AND active = 1 ORDER BY id DESC LIMIT 1"
        ).use { ps ->
            ps.setLong(1, memberId)
            ps.executeQuery().use { rs -> if (rs.next()) CardRow(rs.getLong(1), rs.getString(2), rs.getString(3)) else null }
        }
    }

    /** Replaces the member's current card, if any, with this one. */
    fun assignCard(memberId: Long, uid: String) {
        db.transaction<Unit> { c ->
            c.prepareStatement(
                "UPDATE nfc_cards SET active = 0, deactivated_at = strftime('%Y-%m-%dT%H:%M:%S','now','localtime') WHERE member_id = ? AND active = 1"
            ).use { ps ->
                ps.setLong(1, memberId)
                ps.executeUpdate()
            }
            c.prepareStatement("INSERT INTO nfc_cards(member_id, uid) VALUES (?,?)").use { ps ->
                ps.setLong(1, memberId)
                ps.setString(2, uid)
                ps.executeUpdate()
            }
        }
    }

    fun removeCard(memberId: Long): Boolean = db.transaction<Boolean> { c ->
        c.prepareStatement(
            "UPDATE nfc_cards SET active = 0, deactivated_at = strftime('%Y-%m-%dT%H:%M:%S','now','localtime') WHERE member_id = ? AND active = 1"
        ).use { ps ->
            ps.setLong(1, memberId)
            ps.executeUpdate() > 0
        }
    }

    // ---- attendance ----

    fun firstCheckInOn(memberId: Long, date: String): String? = db.withConnection { c ->
        c.prepareStatement("SELECT time_in FROM attendance WHERE member_id = ? AND substr(time_in, 1, 10) = ? ORDER BY id LIMIT 1").use { ps ->
            ps.setLong(1, memberId)
            ps.setString(2, date)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun insertAttendance(memberId: Long, source: String, uid: String?, timeIn: String) {
        db.transaction<Unit> { c ->
            c.prepareStatement("INSERT INTO attendance(member_id, time_in, source, card_uid) VALUES (?,?,?,?)").use { ps ->
                ps.setLong(1, memberId)
                ps.setString(2, timeIn)
                ps.setString(3, source)
                if (uid == null) ps.setNull(4, Types.VARCHAR) else ps.setString(4, uid)
                ps.executeUpdate()
            }
        }
    }

    fun attendanceOn(date: String): List<AttendanceDbRow> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT a.time_in, m.full_name, m.member_number, a.source, a.id, a.card_uid
            FROM attendance a
            JOIN members m ON m.id = a.member_id
            WHERE substr(a.time_in, 1, 10) = ?
            ORDER BY a.id DESC
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, date)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<AttendanceDbRow>()
                while (rs.next()) list += AttendanceDbRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6))
                list
            }
        }
    }

    // ---- walk-ins (called inside an open transaction) ----

    fun insertWalkInTransaction(c: Connection, reference: String, totalCents: Long): Long =
        c.prepareStatement(
            "INSERT INTO transactions(reference, type, member_id, subtotal_cents, discount_cents, total_cents) VALUES (?, 'WALK_IN', NULL, ?, 0, ?)",
            Statement.RETURN_GENERATED_KEYS,
        ).use { ps ->
            ps.setString(1, reference)
            ps.setLong(2, totalCents)
            ps.setLong(3, totalCents)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> rs.next(); rs.getLong(1) }
        }

    fun insertWalkIn(c: Connection, transactionId: Long, visitor: String?, at: String) {
        c.prepareStatement("INSERT INTO walk_in_visits(transaction_id, visitor_name, visited_at) VALUES (?,?,?)").use { ps ->
            ps.setLong(1, transactionId)
            if (visitor == null) ps.setNull(2, Types.VARCHAR) else ps.setString(2, visitor)
            ps.setString(3, at)
            ps.executeUpdate()
        }
    }

    fun walkInsOn(date: String): List<WalkInDbRow> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT w.visited_at, w.visitor_name, t.total_cents, t.reference
            FROM walk_in_visits w
            JOIN transactions t ON t.id = w.transaction_id
            WHERE substr(w.visited_at, 1, 10) = ?
            ORDER BY w.id DESC
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, date)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<WalkInDbRow>()
                while (rs.next()) list += WalkInDbRow(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getString(4))
                list
            }
        }
    }
}