package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.data.database.DatabaseManager

data class AdminRecord(val username: String, val passwordHash: String, val salt: String, val iterations: Int)

class AdminRepository(private val db: DatabaseManager) {

    fun exists(): Boolean = db.withConnection { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT count(*) FROM admin_account").use { rs -> rs.next() && rs.getInt(1) > 0 }
        }
    }

    fun get(): AdminRecord? = db.withConnection { c ->
        c.createStatement().use { s ->
            s.executeQuery("SELECT username, password_hash, salt, iterations FROM admin_account WHERE id = 1").use { rs ->
                if (rs.next()) AdminRecord(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)) else null
            }
        }
    }

    /** Fails with an SQL error if an account already exists (id is fixed to 1). */
    fun create(record: AdminRecord) {
        db.withConnection { c ->
            c.prepareStatement(
                "INSERT INTO admin_account(id, username, password_hash, salt, iterations) VALUES (1, ?, ?, ?, ?)"
            ).use { ps ->
                ps.setString(1, record.username)
                ps.setString(2, record.passwordHash)
                ps.setString(3, record.salt)
                ps.setInt(4, record.iterations)
                ps.executeUpdate()
            }
        }
    }
}