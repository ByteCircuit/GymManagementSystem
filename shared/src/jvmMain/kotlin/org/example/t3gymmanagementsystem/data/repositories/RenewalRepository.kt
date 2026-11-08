package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import java.sql.PreparedStatement
import java.sql.Types

data class RenewalDbRow(
    val reference: String,
    val memberName: String,
    val memberNumber: String,
    val previousType: String?,
    val previousPlanName: String?,
    val newType: String,
    val newPlanName: String?,
    val previousEnd: String?,
    val newEnd: String,
    val createdAt: String,
)

private fun PreparedStatement.setNullable(index: Int, value: String?) {
    if (value == null) setNull(index, Types.VARCHAR) else setString(index, value)
}

/** Read-only queries for the Memberships page. */
class RenewalRepository(private val db: DatabaseManager) {

    fun renewals(query: String, type: String?): List<RenewalDbRow> {
        val q = query.trim()
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return db.withConnection { c ->
            c.prepareStatement(
                """
                SELECT t.reference, m.full_name, m.member_number,
                       prev.type, prev.plan_name, cur.type, cur.plan_name,
                       prev.end_date, cur.end_date, t.created_at
                FROM membership_records cur
                JOIN transactions t ON t.id = cur.transaction_id
                JOIN members m ON m.id = cur.member_id
                LEFT JOIN membership_records prev ON prev.id = cur.renewal_of_id
                WHERE cur.renewal_of_id IS NOT NULL
                  AND (? IS NULL OR cur.type = ?)
                  AND (? = '' OR t.reference LIKE ? ESCAPE '\' OR m.full_name LIKE ? ESCAPE '\' OR m.member_number LIKE ? ESCAPE '\')
                ORDER BY cur.id DESC
                LIMIT 500
                """.trimIndent()
            ).use { ps ->
                ps.setNullable(1, type)
                ps.setNullable(2, type)
                ps.setString(3, q)
                ps.setString(4, like)
                ps.setString(5, like)
                ps.setString(6, like)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<RenewalDbRow>()
                    while (rs.next()) {
                        list += RenewalDbRow(
                            rs.getString(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                            rs.getString(8), rs.getString(9), rs.getString(10),
                        )
                    }
                    list
                }
            }
        }
    }

    /** One entry per member: the end date of the latest membership, or null if none. */
    fun latestEndDates(): List<String?> = db.withConnection { c ->
        c.createStatement().use { s ->
            s.executeQuery(
                "SELECT (SELECT MAX(mr.end_date) FROM membership_records mr WHERE mr.member_id = m.id) FROM members m"
            ).use { rs ->
                val list = mutableListOf<String?>()
                while (rs.next()) list += rs.getString(1)
                list
            }
        }
    }
}