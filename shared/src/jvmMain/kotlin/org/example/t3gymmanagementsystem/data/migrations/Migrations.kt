package org.example.t3gymmanagementsystem.data.migrations

import org.example.t3gymmanagementsystem.data.database.DatabaseException

data class Migration(val version: Int, val name: String, val sql: String) {

    /** Splits on a semicolon that ends a line; comment-only lines are dropped. Triggers are not supported. */
    fun statements(): List<String> {
        val cleaned = sql.lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
        return cleaned.split(Regex(";\\s*(\\r?\\n|$)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}

object Migrations {
    private val files = listOf(
        1 to "db/migrations/V001__initial_schema.sql",
        2 to "db/migrations/V002__student_staff_verification.sql",
        3 to "db/migrations/V003__member_photo.sql",
        4 to "db/migrations/V004__membership_plans.sql",
        5 to "db/migrations/V005__nfc_attendance_walkins.sql",
        6 to "db/migrations/V006__denied_scans.sql",
        7 to "db/migrations/V007__products_and_stock.sql",
    )

    fun all(): List<Migration> = files.map { (version, path) ->
        val stream = Migrations::class.java.classLoader.getResourceAsStream(path)
            ?: throw DatabaseException("Missing migration resource: $path")
        Migration(version, path.substringAfterLast('/'), stream.bufferedReader().use { it.readText() })
    }
}