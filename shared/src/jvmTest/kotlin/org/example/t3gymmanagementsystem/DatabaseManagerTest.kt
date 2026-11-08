package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseException
import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.migrations.Migration
import org.example.t3gymmanagementsystem.data.migrations.Migrations
import org.example.t3gymmanagementsystem.data.repositories.SettingKeys
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DatabaseManagerTest {
    private lateinit var dir: Path

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-test")
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun newDb(name: String = "gym.db"): DatabaseManager =
        DatabaseManager(dir.resolve(name)).also { it.migrate() }

    private fun count(db: DatabaseManager, table: String): Int = db.withConnection { c ->
        c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM $table").use { it.next(); it.getInt(1) } }
    }

    @Test
    fun migrateCreatesSchemaAndSetsVersion() {
        val db = newDb()
        assertEquals(Migrations.all().maxOf { it.version }, db.schemaVersion())
    }

    @Test
    fun migrateTwiceKeepsData() {
        val db = newDb()
        SettingsRepository(db).set(SettingKeys.GYM_NAME, "Keep Me")
        db.migrate()
        assertEquals("Keep Me", SettingsRepository(db).get(SettingKeys.GYM_NAME))
    }

    @Test
    fun pricesAndDiscountAreNotSeeded() {
        val settings = SettingsRepository(newDb())
        assertNull(settings.get(SettingKeys.PRICE_MONTHLY_CENTS))
        assertNull(settings.get(SettingKeys.PRICE_DAILY_CENTS))
        assertNull(settings.get(SettingKeys.PRICE_WALK_IN_CENTS))
        assertNull(settings.get(SettingKeys.STUDENT_DISCOUNT_PERCENT))
        assertEquals("PHP", settings.get(SettingKeys.CURRENCY))
        assertEquals("Asia/Manila", settings.get(SettingKeys.TIMEZONE))
    }

    @Test
    fun dataPersistsAcrossManagerInstances() {
        val first = newDb()
        SettingsRepository(first).set(SettingKeys.GYM_NAME, "Persisted")
        val second = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        assertEquals("Persisted", SettingsRepository(second).get(SettingKeys.GYM_NAME))
    }

    @Test
    fun foreignKeysAreEnforced() {
        val db = newDb()
        assertFailsWith<SQLException> {
            db.withConnection { c -> c.createStatement().use { it.execute("INSERT INTO attendance(member_id) VALUES (9999)") } }
        }
    }

    @Test
    fun checkConstraintRejectsInconsistentTotals() {
        val db = newDb()
        assertFailsWith<SQLException> {
            db.withConnection { c ->
                c.createStatement().use {
                    it.execute(
                        "INSERT INTO transactions(reference,type,subtotal_cents,discount_cents,total_cents) " +
                            "VALUES ('T1','WALK_IN',1000,0,900)"
                    )
                }
            }
        }
    }

    @Test
    fun onlyOneActiveCardPerUid() {
        val db = newDb()
        db.withConnection { c ->
            c.createStatement().use {
                it.execute("INSERT INTO members(member_number,full_name,contact_number) VALUES ('0001A','A','1')")
                it.execute("INSERT INTO members(member_number,full_name,contact_number) VALUES ('0002A','B','2')")
                it.execute("INSERT INTO nfc_cards(uid,member_id) VALUES ('04AABB',1)")
            }
        }
        assertFailsWith<SQLException> {
            db.withConnection { c -> c.createStatement().use { it.execute("INSERT INTO nfc_cards(uid,member_id) VALUES ('04AABB',2)") } }
        }
    }

    @Test
    fun transactionRollsBackOnFailure() {
        val db = newDb()
        assertFailsWith<IllegalStateException> {
            db.transaction { c ->
                c.createStatement().use { it.execute("INSERT INTO members(member_number,full_name,contact_number) VALUES ('0001A','A','1')") }
                error("boom")
            }
        }
        assertEquals(0, count(db, "members"))
    }

    @Test
    fun failedMigrationLeavesPreviousVersion() {
        val db = DatabaseManager(dir.resolve("bad.db"))
        val good = Migration(1, "good", "CREATE TABLE members(id INTEGER);")
        val bad = Migration(2, "bad", "CREATE TABLE ok_table(id INTEGER);\nINSERT INTO missing_table VALUES (1);")
        assertFailsWith<DatabaseException> { db.migrate(listOf(good, bad)) }
        assertEquals(1, db.schemaVersion())
    }

    @Test
    fun backupAndRestoreRoundTrip() {
        val db = newDb()
        val settings = SettingsRepository(db)
        settings.set(SettingKeys.GYM_NAME, "Before")
        val backup = db.backupTo(dir.resolve("backup.db"))
        settings.set(SettingKeys.GYM_NAME, "After")
        db.restoreFrom(backup)
        assertEquals("Before", SettingsRepository(db).get(SettingKeys.GYM_NAME))
    }

    @Test
    fun restoreRejectsNonDatabaseFile() {
        val db = newDb()
        val junk = dir.resolve("junk.db").also { Files.writeString(it, "not a database") }
        assertFailsWith<DatabaseException> { db.restoreFrom(junk) }
        assertTrue(db.schemaVersion() >= 1)
    }
}