package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import java.sql.Statement

data class EligibilityRow(val id: Long, val name: String, val active: Boolean)

class EligibilityRepository(private val db: DatabaseManager) {

    fun listActive(): List<EligibilityRow> = db.withConnection { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT id, name, active FROM eligibility_types WHERE active = 1 ORDER BY id").use { rs ->
                val list = mutableListOf<EligibilityRow>()
                while (rs.next()) list += EligibilityRow(rs.getLong(1), rs.getString(2), rs.getInt(3) == 1)
                list
            }
        }
    }

    fun find(id: Long): EligibilityRow? = db.withConnection { c ->
        c.prepareStatement("SELECT id, name, active FROM eligibility_types WHERE id = ?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) EligibilityRow(rs.getLong(1), rs.getString(2), rs.getInt(3) == 1) else null }
        }
    }

    /** Every type, archived ones too, so plan labels keep their name. */
    fun names(): Map<Long, String> = db.withConnection { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT id, name FROM eligibility_types").use { rs ->
                val map = mutableMapOf<Long, String>()
                while (rs.next()) map[rs.getLong(1)] = rs.getString(2)
                map
            }
        }
    }

    fun activeNameExists(name: String): Boolean = db.withConnection { c ->
        c.prepareStatement("SELECT 1 FROM eligibility_types WHERE active = 1 AND lower(name) = lower(?) LIMIT 1").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs -> rs.next() }
        }
    }

    fun insert(name: String): Long = db.transaction<Long> { c ->
        c.prepareStatement("INSERT INTO eligibility_types(name) VALUES (?)", Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, name)
            ps.executeUpdate()
            ps.generatedKeys.use { rs -> rs.next(); rs.getLong(1) }
        }
    }

    /** How many verifications and plans point at this type. */
    fun usage(id: Long): Int = db.withConnection { c ->
        c.prepareStatement(
            "SELECT (SELECT count(*) FROM student_verifications WHERE eligibility_type_id = ?) + (SELECT count(*) FROM membership_plans WHERE eligibility_type_id = ?)"
        ).use { ps ->
            ps.setLong(1, id)
            ps.setLong(2, id)
            ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    fun delete(id: Long) {
        db.transaction<Unit> { c ->
            c.prepareStatement("DELETE FROM eligibility_types WHERE id = ?").use { ps -> ps.setLong(1, id); ps.executeUpdate() }
        }
    }

    fun archive(id: Long) {
        db.transaction<Unit> { c ->
            c.prepareStatement("UPDATE eligibility_types SET active = 0 WHERE id = ?").use { ps -> ps.setLong(1, id); ps.executeUpdate() }
        }
    }

    /** Name of the type a member was last verified for, or null. */
    fun typeNameOf(memberId: Long): String? = db.withConnection { c ->
        c.prepareStatement(
            "SELECT t.name FROM student_verifications v JOIN eligibility_types t ON t.id = v.eligibility_type_id WHERE v.member_id = ? AND v.verified = 1 ORDER BY v.id DESC LIMIT 1"
        ).use { ps ->
            ps.setLong(1, memberId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }
}