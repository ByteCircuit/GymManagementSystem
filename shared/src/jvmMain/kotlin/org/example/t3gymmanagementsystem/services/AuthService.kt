package org.example.t3gymmanagementsystem.services

import org.example.t3gymmanagementsystem.AuthResult
import org.example.t3gymmanagementsystem.data.repositories.AdminRecord
import org.example.t3gymmanagementsystem.data.repositories.AdminRepository

class AuthService(private val admins: AdminRepository) {

    fun isConfigured(): Boolean = admins.exists()

    fun createAdmin(username: String, password: String): AuthResult {
        val name = username.trim()
        if (name.isEmpty() || name.length > 40) return AuthResult.Failure("Username must be 1 to 40 characters.")
        if (password.length < MIN_PASSWORD) return AuthResult.Failure("Password must be at least $MIN_PASSWORD characters.")
        if (admins.exists()) return AuthResult.Failure("A superadmin account already exists.")
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(password.toCharArray(), salt)
        admins.create(
            AdminRecord(name, PasswordHasher.encode(hash), PasswordHasher.encode(salt), PasswordHasher.ITERATIONS)
        )
        return AuthResult.Success(name)
    }

    fun login(username: String, password: String): AuthResult {
        val record = admins.get() ?: return AuthResult.Failure("No account exists yet.")
        val passwordOk = PasswordHasher.matches(password.toCharArray(), record.salt, record.passwordHash, record.iterations)
        val usernameOk = record.username.equals(username.trim(), ignoreCase = true)
        return if (passwordOk && usernameOk) AuthResult.Success(record.username)
        else AuthResult.Failure("Incorrect username or password.")
    }

    companion object {
        const val MIN_PASSWORD = 8
    }
}