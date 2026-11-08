package org.example.t3gymmanagementsystem.data.database

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolution order: system property gym.db.path, env GYM_DB_PATH,
 * then %APPDATA%\T3GymManagementSystem\gym.db (or ~/.t3-gym/gym.db off Windows).
 */
object DatabaseLocation {
    fun resolve(): Path {
        System.getProperty("gym.db.path")?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
        System.getenv("GYM_DB_PATH")?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
        val appData = System.getenv("APPDATA")
        val base = if (!appData.isNullOrBlank()) Paths.get(appData, "T3GymManagementSystem")
        else Paths.get(System.getProperty("user.home"), ".t3-gym")
        return base.resolve("gym.db")
    }

    fun backupDir(dbPath: Path): Path = dbPath.toAbsolutePath().parent.resolve("backups")
}