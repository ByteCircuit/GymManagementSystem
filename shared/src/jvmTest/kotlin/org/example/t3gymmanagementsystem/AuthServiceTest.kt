package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.AdminRepository
import org.example.t3gymmanagementsystem.services.AuthService
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthServiceTest {
    private lateinit var dir: Path
    private lateinit var db: DatabaseManager
    private lateinit var auth: AuthService

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-auth")
        db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        auth = AuthService(AdminRepository(db))
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun notConfiguredUntilAccountCreated() {
        assertFalse(auth.isConfigured())
        assertIs<AuthResult.Success>(auth.createAdmin("owner", "longenough1"))
        assertTrue(auth.isConfigured())
    }

    @Test
    fun loginSucceedsWithCorrectCredentials() {
        auth.createAdmin("owner", "longenough1")
        val result = auth.login("OWNER", "longenough1")
        assertEquals(AuthResult.Success("owner"), result)
    }

    @Test
    fun loginFailsWithWrongPasswordOrUsername() {
        auth.createAdmin("owner", "longenough1")
        assertIs<AuthResult.Failure>(auth.login("owner", "wrongpassword"))
        assertIs<AuthResult.Failure>(auth.login("someone", "longenough1"))
    }

    @Test
    fun shortPasswordIsRejected() {
        assertIs<AuthResult.Failure>(auth.createAdmin("owner", "short"))
        assertFalse(auth.isConfigured())
    }

    @Test
    fun onlyOneAccountCanBeCreated() {
        auth.createAdmin("owner", "longenough1")
        assertIs<AuthResult.Failure>(auth.createAdmin("second", "anotherlong1"))
    }

    @Test
    fun passwordIsNotStoredInPlainText() {
        auth.createAdmin("owner", "longenough1")
        val stored = AdminRepository(db).get()!!
        assertNotEquals("longenough1", stored.passwordHash)
        assertFalse(stored.passwordHash.contains("longenough1"))
    }
}