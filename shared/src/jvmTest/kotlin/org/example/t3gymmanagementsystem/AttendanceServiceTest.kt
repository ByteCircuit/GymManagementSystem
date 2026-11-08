package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import org.example.t3gymmanagementsystem.services.AttendanceService
import org.example.t3gymmanagementsystem.services.MembershipService
import org.example.t3gymmanagementsystem.services.PaymentService
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttendanceServiceTest {
    private lateinit var dir: Path
    private lateinit var memberships: MembershipService
    private lateinit var attendance: AttendanceService
    private lateinit var paymentService: PaymentService
    private var now: LocalDateTime = LocalDateTime.of(2026, 1, 15, 9, 30)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-attendance")
        val db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        val settings = SettingsRepository(db)
        val members = MemberRepository(db)
        memberships = MembershipService(db, settings, members) { now.toLocalDate() }
        attendance = AttendanceService(db, settings, members) { now }
        paymentService = PaymentService(db)
        memberships.savePricing(Pricing(100_000, 15_000))
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun <T> Outcome<T>.ok(): T {
        assertIs<Outcome.Ok<T>>(this)
        return value
    }

    private fun register(name: String, kind: MembershipKind = MembershipKind.Monthly): Long {
        val r = memberships.register(NewMember(name, "0917", "", "", "", kind, isStudent = false))
        assertIs<Outcome.Ok<Registration>>(r)
        return r.value.memberId
    }

    @Test
    fun cardCheckInWelcomesThenReportsAlreadyIn() {
        val id = register("Juan Dela Cruz")
        attendance.assignCard(id, "04:a1:b2:c3").ok()

        val first = attendance.checkInByCard("04A1B2C3")
        assertEquals(CheckInStatus.Welcome, first.status)
        assertEquals("Juan Dela Cruz", first.memberName)

        now = now.plusMinutes(45)
        val second = attendance.checkInByCard("04a1b2c3")
        assertEquals(CheckInStatus.AlreadyIn, second.status)
        assertTrue(second.detail.contains("09:30 AM"))

        val rows = attendance.todayAttendance()
        assertEquals(1, rows.size)
        assertEquals("Card", rows[0].source)
        assertEquals("09:30 AM", rows[0].timeLabel)
    }

    @Test
    fun expiredMembershipIsNotCheckedIn() {
        val id = register("Juan Dela Cruz")
        attendance.assignCard(id, "04A1B2C3").ok()
        now = LocalDateTime.of(2026, 3, 1, 9, 0)
        val result = attendance.checkInByCard("04A1B2C3")
        assertEquals(CheckInStatus.Expired, result.status)
        assertTrue(result.detail.contains("Feb 15, 2026"))
        assertEquals(0, attendance.todayAttendance().size)
    }

    @Test
    fun dailyMembershipOnlyWorksOnItsDay() {
        val id = register("Day Visitor", MembershipKind.Daily)
        assertEquals(CheckInStatus.Welcome, attendance.checkInMember(id).status)
        now = now.plusDays(1)
        assertEquals(CheckInStatus.Expired, attendance.checkInMember(id).status)
    }

    @Test
    fun manualCheckInIsRecordedAsManual() {
        val id = register("Juan Dela Cruz")
        assertEquals(CheckInStatus.Welcome, attendance.checkInMember(id).status)
        assertEquals(CheckInStatus.AlreadyIn, attendance.checkInMember(id).status)
        assertEquals("Manual", attendance.todayAttendance().single().source)
    }

    @Test
    fun unknownCardIsReported() {
        assertEquals(CheckInStatus.UnknownCard, attendance.checkInByCard("04A1B2C3").status)
        assertEquals(CheckInStatus.UnknownCard, attendance.checkInByCard("not a card").status)
        assertEquals(0, attendance.todayAttendance().size)
    }

    @Test
    fun aCardCannotBelongToTwoMembers() {
        val a = register("Juan Dela Cruz")
        val b = register("Maria Santos")
        attendance.assignCard(a, "04A1B2C3").ok()
        val result = attendance.assignCard(b, "04a1b2c3")
        assertIs<Outcome.Fail>(result)
        assertTrue(result.message.contains("Juan Dela Cruz"))
        attendance.assignCard(a, "04A1B2C3").ok()
    }

    @Test
    fun replacingAndRemovingACard() {
        val id = register("Juan Dela Cruz")
        assertNull(attendance.getCard(id))
        attendance.assignCard(id, "04A1B2C3").ok()
        attendance.assignCard(id, "04A1B2C3D4E5F6").ok()
        assertEquals("04A1B2C3D4E5F6", attendance.getCard(id)!!.uid)
        assertEquals(CheckInStatus.UnknownCard, attendance.checkInByCard("04A1B2C3").status)
        attendance.removeCard(id).ok()
        assertNull(attendance.getCard(id))
        assertIs<Outcome.Fail>(attendance.removeCard(id))
        assertEquals(CheckInStatus.UnknownCard, attendance.checkInByCard("04A1B2C3D4E5F6").status)
    }

    @Test
    fun invalidCardIdsAreRejected() {
        val id = register("Juan Dela Cruz")
        assertIs<Outcome.Fail>(attendance.assignCard(id, "XYZ"))
        assertIs<Outcome.Fail>(attendance.assignCard(id, "04A1B2"))
        assertIs<Outcome.Fail>(attendance.assignCard(999, "04A1B2C3"))
    }

    @Test
    fun normalizeUidCleansAndValidates() {
        assertEquals("04A1B2C3", AttendanceService.normalizeUid(" 04-a1-b2-c3 "))
        assertEquals("04A1B2C3D4E5F6", AttendanceService.normalizeUid("04:A1:B2:C3:D4:E5:F6"))
        assertEquals(20, AttendanceService.normalizeUid("0123456789ABCDEF0123")!!.length)
        assertNull(AttendanceService.normalizeUid("04A1B2"))
        assertNull(AttendanceService.normalizeUid("GG11AA22"))
        assertNull(AttendanceService.normalizeUid(""))
    }

    @Test
    fun walkInNeedsAPriceThenRecordsASaleWithAReceipt() {
        assertIs<Outcome.Fail>(attendance.recordWalkIn("Maria", PaymentInfo()))
        memberships.savePricing(Pricing(null, null, 5_000)).ok()

        val result = attendance.recordWalkIn("  Maria  ", PaymentInfo(PaymentMethod.Online, "ref 99")).ok()
        assertEquals(5_000L, result.totalCents)

        val walkIn = attendance.todayWalkIns().single()
        assertEquals("Maria", walkIn.visitor)
        assertEquals(result.reference, walkIn.reference)

        val row = paymentService.transactions("").first()
        assertEquals("Walk-in visit", row.typeLabel)
        assertEquals("Online QR", row.methodLabel)
        assertEquals("\u2014", row.memberName)
        val receipt = paymentService.receipt(row.id, GymInfo("Test Gym", "1 Test Street", "0999"))
        assertNotNull(receipt)
        assertNull(receipt.memberName)
        assertEquals(5_000L, receipt.totalCents)
    }

    @Test
    fun anonymousWalkInIsLabelled() {
        memberships.savePricing(Pricing(null, null, 5_000)).ok()
        attendance.recordWalkIn("", PaymentInfo()).ok()
        assertEquals("Walk-in", attendance.todayWalkIns().single().visitor)
    }
}