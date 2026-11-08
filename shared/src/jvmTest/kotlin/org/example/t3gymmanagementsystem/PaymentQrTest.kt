package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import org.example.t3gymmanagementsystem.services.MembershipService
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

class PaymentQrTest {
    private lateinit var dir: Path
    private lateinit var service: MembershipService

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-qr")
        val db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        service = MembershipService(db, SettingsRepository(db), MemberRepository(db)) { LocalDate.of(2026, 1, 15) }
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun qrImageRoundTripsAndCanBeRemoved() {
        assertNull(service.getQr())
        val bytes = ByteArray(5000) { (it % 251).toByte() }
        service.saveQr(bytes)
        assertContentEquals(bytes, service.getQr())
        service.removeQr()
        assertNull(service.getQr())
    }
}