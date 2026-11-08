package org.example.t3gymmanagementsystem.data.repositories

import org.example.t3gymmanagementsystem.data.database.DatabaseManager

object SettingKeys {
    const val GYM_NAME = "gym.name"
    const val GYM_CONTACT = "gym.contact"
    const val GYM_ADDRESS = "gym.address"
    const val CURRENCY = "locale.currency"
    const val TIMEZONE = "locale.timezone"
    const val DATE_FORMAT = "locale.date_format"
    const val TIME_FORMAT = "locale.time_format"
    const val PAYMENT_METHODS = "payment.methods"
    const val LOW_STOCK_DEFAULT = "inventory.low_stock_default"
    const val MEMBER_NUMBER_FORMAT = "member.number_format"
    const val RECEIPT_FOOTER = "receipt.footer"
    // No seeded value: absent until the staff member configures them.
    const val PRICE_MONTHLY_CENTS = "price.monthly_cents"
    const val PRICE_DAILY_CENTS = "price.daily_cents"
    const val PRICE_WALK_IN_CENTS = "price.walk_in_cents"
    const val STUDENT_DISCOUNT_PERCENT = "discount.student_percent"
}

class SettingsRepository(private val db: DatabaseManager) {

    fun get(key: String): String? = db.withConnection { c ->
        c.prepareStatement("SELECT value FROM settings WHERE key = ?").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun getOrDefault(key: String, default: String): String = get(key) ?: default

    fun set(key: String, value: String) {
        db.withConnection { c ->
            c.prepareStatement(
                """INSERT INTO settings(key, value, updated_at)
                   VALUES (?, ?, strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
                   ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at"""
            ).use { ps ->
                ps.setString(1, key)
                ps.setString(2, value)
                ps.executeUpdate()
            }
        }
    }

    /** Saves several settings in one transaction. */
    fun setAll(values: Map<String, String>) {
        db.transaction { c ->
            c.prepareStatement(
                """INSERT INTO settings(key, value, updated_at)
                   VALUES (?, ?, strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
                   ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at"""
            ).use { ps ->
                for ((k, v) in values) {
                    ps.setString(1, k)
                    ps.setString(2, v)
                    ps.executeUpdate()
                }
            }
        }
    }
}