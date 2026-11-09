package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class MemberRecord(
    val id: Long,
    val number: String,
    val name: String,
    val contact: String,
    val email: String?,
    val address: String?,
    val emergencyContact: String?,
    val registeredAt: String,
)

data class MemberListRow(
    val id: Long,
    val number: String,
    val name: String,
    val contact: String,
    val lastType: String?,
    val lastEnd: String?,
)

data class MembershipHistoryRow(
    val type: String,
    val startDate: String,
    val endDate: String,
    val totalCents: Long?,
    val reference: String?,
)

data class StudentRecord(val school: String?, val idNumber: String?)

private fun PreparedStatement.setNullable(index: Int, value: String?) {
    if (value == null) setNull(index, Types.VARCHAR) else setString(index, value)
}

/** SQL for members, student verification, memberships and the transactions that pay for them. */
class MemberRepository(private val db: DatabaseManager) {

    // ---- reads (own connection) ----

    fun search(query: String): List<MemberListRow> {
        val q = query.trim()
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return db.withConnection { c ->
            c.prepareStatement(
                """
                SELECT m.id, m.member_number, m.full_name, m.contact_number,
                       (SELECT mr.type FROM membership_records mr WHERE mr.member_id = m.id
                        ORDER BY mr.end_date DESC, mr.id DESC LIMIT 1),
                       (SELECT MAX(mr.end_date) FROM membership_records mr WHERE mr.member_id = m.id)
                FROM members m
                WHERE ? = '' OR m.full_name LIKE ? ESCAPE '\' OR m.member_number LIKE ? ESCAPE '\'
                      OR m.contact_number LIKE ? ESCAPE '\'
                ORDER BY m.full_name COLLATE NOCASE
                LIMIT 200
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, q)
                ps.setString(2, like)
                ps.setString(3, like)
                ps.setString(4, like)
                ps.executeQuery().use { rs ->
                    val list = mutableListOf<MemberListRow>()
                    while (rs.next()) {
                        list += MemberListRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6))
                    }
                    list
                }
            }
        }
    }

    fun find(id: Long): MemberRecord? = db.withConnection { c ->
        c.prepareStatement(
            "SELECT id, member_number, full_name, contact_number, email, address, emergency_contact, registered_at FROM members WHERE id = ?"
        ).use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    MemberRecord(
                        rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                    )
                } else null
            }
        }
    }

    /** Newest first. */
    fun history(memberId: Long): List<MembershipHistoryRow> = db.withConnection { c ->
        c.prepareStatement(
            """
            SELECT mr.type, mr.start_date, mr.end_date, t.total_cents, t.reference
            FROM membership_records mr LEFT JOIN transactions t ON t.id = mr.transaction_id
            WHERE mr.member_id = ?
            ORDER BY mr.end_date DESC, mr.id DESC
            """.trimIndent()
        ).use { ps ->
            ps.setLong(1, memberId)
            ps.executeQuery().use { rs ->
                val list = mutableListOf<MembershipHistoryRow>()
                while (rs.next()) {
                    val total = rs.getLong(4)
                    val totalOrNull = if (rs.wasNull()) null else total
                    list += MembershipHistoryRow(rs.getString(1), rs.getString(2), rs.getString(3), totalOrNull, rs.getString(5))
                }
                list
            }
        }
    }

    fun student(memberId: Long): StudentRecord? = db.withConnection { c ->
        c.prepareStatement(
            "SELECT school_name, id_number FROM student_verifications WHERE member_id = ? AND verified = 1 ORDER BY id DESC LIMIT 1"
        ).use { ps ->
            ps.setLong(1, memberId)
            ps.executeQuery().use { rs -> if (rs.next()) StudentRecord(rs.getString(1), rs.getString(2)) else null }
        }
    }

    fun isVerifiedStudent(memberId: Long): Boolean = student(memberId) != null

    /** The eligibility type the member was last verified for, or null when not verified (or an old record has no type). */
    fun verifiedTypeId(memberId: Long): Long? = db.withConnection { c ->
        c.prepareStatement(
            "SELECT eligibility_type_id FROM student_verifications WHERE member_id = ? AND verified = 1 ORDER BY id DESC LIMIT 1"
        ).use { ps ->
            ps.setLong(1, memberId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1).takeIf { !rs.wasNull() } else null }
        }
    }

    fun insertVerification(c: Connection, memberId: Long, typeId: Long?, idNumber: String?, school: String?, document: ByteArray?) {
        c.prepareStatement(
            "INSERT INTO student_verifications(member_id, id_number, school_name, verified, eligibility_type_id, document) VALUES (?,?,?,1,?,?)"
        ).use { ps ->
            ps.setLong(1, memberId)
            ps.setNullable(2, idNumber)
            ps.setNullable(3, school)
            if (typeId == null) ps.setNull(4, Types.INTEGER) else ps.setLong(4, typeId)
            if (document == null) ps.setNull(5, Types.BLOB) else ps.setBytes(5, document)
            ps.executeUpdate()
        }
    }

    fun latestEndDate(memberId: Long): String? = db.withConnection { c ->
        c.prepareStatement("SELECT MAX(end_date) FROM membership_records WHERE member_id = ?").use { ps ->
            ps.setLong(1, memberId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun latestMembershipId(memberId: Long): Long? = db.withConnection { c ->
        c.prepareStatement("SELECT id FROM membership_records WHERE member_id = ? ORDER BY end_date DESC, id DESC LIMIT 1").use { ps ->
            ps.setLong(1, memberId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
        }
    }

    // ---- writes (caller supplies the transaction connection) ----

    fun nextNumber(c: Connection, pattern: String): String {
        val next = c.createStatement().use { s ->
            s.executeQuery("SELECT COALESCE(MAX(id), 0) + 1 FROM members").use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
        return runCatching { pattern.format(next) }.getOrElse { "%04dA".format(next) }
    }

    fun nextReference(c: Connection, date: LocalDate): String {
        val prefix = "TX-" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
        val count = c.prepareStatement("SELECT COUNT(*) FROM transactions WHERE reference LIKE ?").use { ps ->
            ps.setString(1, "$prefix%")
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
        return prefix + (count + 1).toString().padStart(4, '0')
    }

    private fun lastInsertId(c: Connection): Long = c.createStatement().use { s ->
        s.executeQuery("SELECT last_insert_rowid()").use { rs ->
            rs.next()
            rs.getLong(1)
        }
    }

    fun insertMember(
        c: Connection, number: String, name: String, contact: String,
        email: String?, address: String?, emergencyContact: String?,
    ): Long {
        c.prepareStatement(
            "INSERT INTO members(member_number, full_name, contact_number, email, address, emergency_contact) VALUES (?,?,?,?,?,?)"
        ).use { ps ->
            ps.setString(1, number)
            ps.setString(2, name)
            ps.setString(3, contact)
            ps.setNullable(4, email)
            ps.setNullable(5, address)
            ps.setNullable(6, emergencyContact)
            ps.executeUpdate()
        }
        return lastInsertId(c)
    }

    fun insertStudentVerification(c: Connection, memberId: Long, idNumber: String?, school: String?) {
        c.prepareStatement(
            "INSERT INTO student_verifications(member_id, id_number, school_name, verified) VALUES (?,?,?,1)"
        ).use { ps ->
            ps.setLong(1, memberId)
            ps.setNullable(2, idNumber)
            ps.setNullable(3, school)
            ps.executeUpdate()
        }
    }

    fun insertTransaction(
        c: Connection, reference: String, type: String, memberId: Long,
        subtotalCents: Long, discountCents: Long, totalCents: Long,
    ): Long {
        c.prepareStatement(
            "INSERT INTO transactions(reference, type, member_id, subtotal_cents, discount_cents, total_cents) VALUES (?,?,?,?,?,?)"
        ).use { ps ->
            ps.setString(1, reference)
            ps.setString(2, type)
            ps.setLong(3, memberId)
            ps.setLong(4, subtotalCents)
            ps.setLong(5, discountCents)
            ps.setLong(6, totalCents)
            ps.executeUpdate()
        }
        return lastInsertId(c)
    }

    fun insertPayment(c: Connection, transactionId: Long, method: String, amountCents: Long) {
        c.prepareStatement("INSERT INTO payments(transaction_id, method, amount_cents) VALUES (?,?,?)").use { ps ->
            ps.setLong(1, transactionId)
            ps.setString(2, method)
            ps.setLong(3, amountCents)
            ps.executeUpdate()
        }
    }

    fun insertMembership(
        c: Connection, memberId: Long, type: String, startDate: String, endDate: String,
        transactionId: Long, renewalOfId: Long?,
    ) {
        c.prepareStatement(
            "INSERT INTO membership_records(member_id, type, start_date, end_date, transaction_id, renewal_of_id) VALUES (?,?,?,?,?,?)"
        ).use { ps ->
            ps.setLong(1, memberId)
            ps.setString(2, type)
            ps.setString(3, startDate)
            ps.setString(4, endDate)
            ps.setLong(5, transactionId)
            if (renewalOfId == null) ps.setNull(6, Types.INTEGER) else ps.setLong(6, renewalOfId)
            ps.executeUpdate()
        }
    }
}