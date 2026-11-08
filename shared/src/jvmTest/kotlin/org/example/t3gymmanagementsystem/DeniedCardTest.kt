package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import org.example.t3gymmanagementsystem.services.AttendanceService
import org.example.t3gymmanagementsystem.services.MembershipService
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeniedCardTest {
    private lateinit var dir: Path
    private lateinit var memberships: MembershipService
    private lateinit var attendance: AttendanceService
    private var now: LocalDateTime = LocalDateTime.of(2026, 1, 15, 9, 30)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-denied")
        val db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        val settings = SettingsRepository(db)
        val members = MemberRepository(db)
        memberships = MembershipService(db, settings, members) { now.toLocalDate() }
        attendance = AttendanceService(db, settings, members) { now }
        memberships.savePricing(Pricing(100_000, 15_000))
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun register(name: String): Long {
        val r = memberships.register(NewMember(name, "0917", "", "", "", MembershipKind.Monthly, isStudent = false))
        assertIs<Outcome.Ok<Registration>>(r)
        return r.value.memberId
    }

    @Test
    fun anExpiredCardTapIsLogged() {
        val id = register("Juan Dela Cruz")
        attendance.assignCard(id, "04A1B2C3")
        now = LocalDateTime.of(2026, 3, 1, 8, 15)

        assertEquals(CheckInStatus.Expired, attendance.checkInByCard("04A1B2C3").status)

        val row = attendance.todayDenied().single()
        assertEquals("Juan Dela Cruz", row.who)
        assertEquals("Membership expired", row.reason)
        assertEquals("04A1B2C3", row.cardUid)
        assertEquals("08:15 AM", row.timeLabel)
    }

    @Test
    fun anUnknownCardTapIsLogged() {
        assertEquals(CheckInStatus.UnknownCard, attendance.checkInByCard("04-a1-b2-c3").status)
        val row = attendance.todayDenied().single()
        assertEquals("Unknown card", row.who)
        assertEquals("Unknown card", row.reason)
        assertEquals("04A1B2C3", row.cardUid)
    }

    @Test
    fun approvedAndRepeatTapsAndManualCheckInsAreNotLogged() {
        val id = register("Juan Dela Cruz")
        attendance.assignCard(id, "04A1B2C3")
        assertEquals(CheckInStatus.Welcome, attendance.checkInByCard("04A1B2C3").status)
        assertEquals(CheckInStatus.AlreadyIn, attendance.checkInByCard("04A1B2C3").status)

        now = LocalDateTime.of(2026, 3, 1, 8, 15)
        assertEquals(CheckInStatus.Expired, attendance.checkInMember(id).status) // staff check-in, not a card tap
        assertEquals(0, attendance.todayDenied().size)
    }

    @Test
    fun deniedCardsAreListedForTodayOnly() {
        attendance.checkInByCard("04A1B2C3")
        now = now.plusDays(1)
        assertEquals(0, attendance.todayDenied().size)
        attendance.checkInByCard("04A1B2C3")
        assertEquals(1, attendance.todayDenied().size)
    }
}