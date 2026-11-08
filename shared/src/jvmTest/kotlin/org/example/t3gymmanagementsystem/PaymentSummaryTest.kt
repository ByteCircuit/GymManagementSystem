package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import org.example.t3gymmanagementsystem.services.AttendanceService
import org.example.t3gymmanagementsystem.services.MembershipService
import org.example.t3gymmanagementsystem.services.PaymentService
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PaymentSummaryTest {
    private lateinit var dir: Path
    private lateinit var memberships: MembershipService
    private lateinit var attendance: AttendanceService
    private lateinit var payments: PaymentService

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-summary")
        val db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        val settings = SettingsRepository(db)
        val members = MemberRepository(db)
        memberships = MembershipService(db, settings, members) { LocalDate.now() }
        attendance = AttendanceService(db, settings, members) { LocalDateTime.now() }
        payments = PaymentService(db)
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    @Test
    fun emptyDatabaseHasZeroFigures() {
        val s = payments.summary()
        assertEquals(PaymentSummary(0, 0, 0, 0, 0), s)
    }

    @Test
    fun todaysSalesAreSplitByKind() {
        memberships.savePricing(Pricing(100_000, 15_000, 5_000))
        val reg = memberships.register(NewMember("Juan Dela Cruz", "0917", "", "", "", MembershipKind.Monthly, isStudent = false))
        assertIs<Outcome.Ok<Registration>>(reg)
        assertIs<Outcome.Ok<WalkInResult>>(attendance.recordWalkIn("Maria", PaymentInfo()))

        val s = payments.summary()
        assertEquals(2, s.todayCount)
        assertEquals(100_000L, s.membershipCents)
        assertEquals(5_000L, s.walkInCents)
        assertEquals(0L, s.productCents)
        assertEquals(0L, s.pendingCents)

        // another day has nothing
        assertEquals(0, payments.summary(LocalDate.now().minusDays(1)).todayCount)
    }
}