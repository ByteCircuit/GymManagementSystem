package org.example.t3gymmanagementsystem.data.database

import org.example.t3gymmanagementsystem.data.migrations.Migration
import org.example.t3gymmanagementsystem.data.migrations.Migrations
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DatabaseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Owns the SQLite file: connections, migrations, backup and restore. Never recreates existing data. */
class DatabaseManager(val dbPath: Path) {

    init {
        dbPath.toAbsolutePath().parent?.let { Files.createDirectories(it) }
    }

    fun openConnection(): Connection {
        val connection = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
        connection.createStatement().use {
            it.execute("PRAGMA foreign_keys = ON")
            it.execute("PRAGMA busy_timeout = 5000")
        }
        return connection
    }

    fun <T> withConnection(block: (Connection) -> T): T = openConnection().use(block)

    /** Atomic: commit on success, rollback on any exception. */
    fun <T> transaction(block: (Connection) -> T): T = openConnection().use { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (e: Throwable) {
            runCatching { connection.rollback() }
            throw e
        }
    }

    fun schemaVersion(): Int = withConnection { c ->
        c.createStatement().use { s ->
            s.executeQuery("PRAGMA user_version").use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    /**
     * Applies each migration newer than PRAGMA user_version, one transaction per migration together
     * with the version bump. A copy of an existing database is made before any change.
     */
    fun migrate(migrations: List<Migration> = Migrations.all()) {
        val current = schemaVersion()
        val pending = migrations.sortedBy { it.version }.filter { it.version > current }
        if (pending.isEmpty()) return
        if (current > 0 && Files.exists(dbPath)) {
            Files.copy(dbPath, premigrationBackupPath(current), StandardCopyOption.COPY_ATTRIBUTES)
        }
        withConnection { c -> c.createStatement().use { it.execute("PRAGMA journal_mode = WAL") } }
        for (migration in pending) {
            try {
                transaction { c ->
                    c.createStatement().use { s ->
                        for (statement in migration.statements()) s.execute(statement)
                        s.execute("PRAGMA user_version = ${migration.version}")
                    }
                }
            } catch (e: Exception) {
                throw DatabaseException("Migration V${migration.version} failed; database left at its previous version.", e)
            }
        }
    }

    /** Consistent snapshot using VACUUM INTO; safe while the app is running. */
    fun backupTo(target: Path): Path {
        target.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        if (Files.exists(target)) throw DatabaseException("Backup file already exists: ${target.fileName}")
        val escaped = target.toAbsolutePath().toString().replace("'", "''")
        try {
            withConnection { c -> c.createStatement().use { it.execute("VACUUM INTO '$escaped'") } }
        } catch (e: Exception) {
            throw DatabaseException("Backup failed: ${e.message}", e)
        }
        return target
    }

    fun defaultBackupName(now: LocalDateTime = LocalDateTime.now()): String =
        "gym_${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))}.db"

    /** Replaces the live database with [source] after validating it; saves the current one first. */
    fun restoreFrom(source: Path): Path {
        if (!Files.isRegularFile(source)) throw DatabaseException("Backup file not found.")
        validateBackup(source)
        val safety = dbPath.resolveSibling("pre-restore_${defaultBackupName()}")
        if (Files.exists(dbPath)) backupTo(safety)
        Files.deleteIfExists(dbPath.resolveSibling(dbPath.fileName.toString() + "-wal"))
        Files.deleteIfExists(dbPath.resolveSibling(dbPath.fileName.toString() + "-shm"))
        Files.copy(source, dbPath, StandardCopyOption.REPLACE_EXISTING)
        return safety
    }

    private fun validateBackup(source: Path) {
        try {
            DriverManager.getConnection("jdbc:sqlite:${source.toAbsolutePath()}").use { c ->
                c.createStatement().use { s ->
                    s.executeQuery("PRAGMA integrity_check").use { rs ->
                        if (!rs.next() || rs.getString(1) != "ok") throw DatabaseException("Backup file failed integrity check.")
                    }
                    s.executeQuery("SELECT count(*) FROM sqlite_master WHERE name = 'members'").use { rs ->
                        if (!rs.next() || rs.getInt(1) == 0) throw DatabaseException("File is not a gym database backup.")
                    }
                }
            }
        } catch (e: DatabaseException) {
            throw e
        } catch (e: Exception) {
            throw DatabaseException("File is not a valid SQLite database.", e)
        }
    }

    private fun premigrationBackupPath(version: Int): Path =
        dbPath.resolveSibling("pre-migration_v${version}_${defaultBackupName()}")
}