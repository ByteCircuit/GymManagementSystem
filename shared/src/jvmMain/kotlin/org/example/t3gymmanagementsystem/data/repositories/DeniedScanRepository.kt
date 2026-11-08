package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import java.sql.Types

data class DeniedDbRow(val scannedAt: String, val memberName: String?, val cardUid: String?, val reason: String)

/** Card taps that were refused. */
class DeniedScanRepository(private val db: DatabaseManager) {

    fun insert(memberId: Long?, uid: String?, reason: String, at: String) {
        db.transaction<Unit> { c ->
            c.prepareStatement("INSERT INTO denied_scans(scanned_at, card_uid, member_id, reason) VALUES (?,?,?,?)").use { ps ->
                ps.setString(1, at)
                if (uid == null) ps.setNull(2, Types.VARCHAR) else ps.setString(2, uid)
                if (memberId == null) ps.setNull(3, Types.INTEGER) else ps.setLong(3, memberId)
                ps.setString(4, reason)
                ps.executeUpdate()
            }
        }
    }

    fun on(date: String): List<DeniedDbRow> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT d.scanned_at, m.full_name, d.card_uid, d.reason
            FROM denied_scans d
            LEFT JOIN members m ON m.id = d.member_id
            WHERE substr(d.scanned_at, 1, 10) = ?
            ORDER BY d.id DESC
            """.trimIndent()
        ).use { ps ->
            ps.setString(1, date)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<DeniedDbRow>()
                while (rs.next()) list += DeniedDbRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4))
                list
            }
        }
    }
}