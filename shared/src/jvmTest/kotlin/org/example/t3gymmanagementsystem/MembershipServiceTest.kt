package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingKeys
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import org.example.t3gymmanagementsystem.services.MembershipService
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MembershipServiceTest {
    private lateinit var dir: Path
    private lateinit var db: DatabaseManager
    private lateinit var settings: SettingsRepository
    private lateinit var service: MembershipService
    private var today: LocalDate = LocalDate.of(2026, 1, 15)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-members")
        db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        settings = SettingsRepository(db)
        service = MembershipService(db, settings, MemberRepository(db)) { today }
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun <T> Outcome<T>.ok(): T {
        assertIs<Outcome.Ok<T>>(this)
        return value
    }

    private fun setPrices(monthly: Long = 100_000, daily: Long = 15_000) {
        service.savePricing(Pricing(monthly, daily)).ok()
    }

    private fun setDiscount(type: DiscountType, value: Long, enabled: Boolean = true) {
        service.saveDiscount(StudentDiscount(type, value, enabled)).ok()
    }

    private fun newMember(kind: MembershipKind = MembershipKind.Monthly, student: Boolean = false) = NewMember(
        fullName = "Juan Dela Cruz", contact = "09171234567", email = "", address = "", emergencyContact = "",
        kind = kind, isStudent = student,
    )

    private fun scalar(sql: String): String = db.withConnection { c ->
        c.createStatement().use { s -> s.executeQuery(sql).use { rs -> rs.next(); rs.getString(1) } }
    }

    @Test
    fun registerMonthlyRecordsMemberTransactionPaymentAndPeriod() {
        setPrices()
        val reg = service.register(newMember()).ok()
        assertEquals("0001A", reg.memberNumber)
        assertEquals(100_000L, reg.totalCents)
        assertEquals("2026-01-15,2026-02-15", scalar("SELECT start_date || ',' || end_date FROM membership_records"))
        assertEquals("MONTHLY_MEMBERSHIP", scalar("SELECT type FROM transactions"))
        assertEquals("Cash,100000", scalar("SELECT method || ',' || amount_cents FROM payments"))
        assertEquals(MemberStatus.Active, service.getMember(reg.memberId)!!.status)
    }

    @Test
    fun dailyCoversOnlyToday() {
        setPrices()
        service.register(newMember(MembershipKind.Daily)).ok()
        assertEquals("2026-01-15,2026-01-15", scalar("SELECT start_date || ',' || end_date FROM membership_records"))
    }

    @Test
    fun studentPercentageDiscountAppliesToMonthly() {
        setPrices()
        setDiscount(DiscountType.Percentage, 20)
        val reg = service.register(newMember(student = true)).ok()
        assertEquals(80_000L, reg.totalCents)
        assertEquals("100000,20000,80000", scalar("SELECT subtotal_cents || ',' || discount_cents || ',' || total_cents FROM transactions"))
    }

    @Test
    fun studentFixedAmountDiscountAppliesToMonthly() {
        setPrices()
        setDiscount(DiscountType.Fixed, 25_000)
        assertEquals(75_000L, service.register(newMember(student = true)).ok().totalCents)
    }

    @Test
    fun fixedDiscountNeverExceedsThePrice() {
        setPrices()
        setDiscount(DiscountType.Fixed, 150_000)
        val reg = service.register(newMember(student = true)).ok()
        assertEquals(0L, reg.totalCents)
        assertEquals("0", scalar("SELECT count(*) FROM payments"))
        assertEquals("1", scalar("SELECT count(*) FROM membership_records"))
    }

    @Test
    fun disabledOrMissingDiscountChargesFullPrice() {
        setPrices()
        assertEquals(100_000L, service.register(newMember(student = true)).ok().totalCents)
        setDiscount(DiscountType.Percentage, 20, enabled = false)
        assertEquals(100_000L, service.register(newMember(student = true)).ok().totalCents)
    }

    @Test
    fun discountDoesNotApplyToDaily() {
        setPrices()
        setDiscount(DiscountType.Percentage, 20)
        assertEquals(15_000L, service.register(newMember(MembershipKind.Daily, student = true)).ok().totalCents)
    }

    @Test
    fun percentageRoundsHalfUpToTheCentavo() {
        setPrices(monthly = 1_010)
        setDiscount(DiscountType.Percentage, 15)
        assertEquals(1_010L - 152L, service.register(newMember(student = true)).ok().totalCents)
    }

    @Test
    fun studentSchoolAndIdAreOptional() {
        setPrices()
        setDiscount(DiscountType.Percentage, 20)
        service.register(newMember(student = true)).ok()
        assertEquals("1", scalar("SELECT count(*) FROM student_verifications"))
        assertEquals("1", scalar("SELECT verified FROM student_verifications"))
        assertNull(scalar("SELECT id_number FROM student_verifications"))
        val detail = service.getMember(1)!!
        assertEquals("Verified student", detail.studentLabel)
        service.register(newMember(student = true).copy(schoolName = "Sample School", studentIdNumber = "2024-001")).ok()
        assertEquals("Sample School \u00B7 ID 2024-001", service.getMember(2)!!.studentLabel)
    }

    @Test
    fun missingPriceFailsAndCreatesNothing() {
        assertIs<Outcome.Fail>(service.register(newMember()))
        assertEquals("0", scalar("SELECT count(*) FROM members"))
        assertEquals("0", scalar("SELECT count(*) FROM transactions"))
    }

    @Test
    fun pricesSaveIndependentlyAndRejectInvalidValues() {
        service.savePricing(Pricing(100_000, null)).ok()
        service.savePricing(Pricing(null, 15_000)).ok()
        assertEquals(Pricing(100_000, 15_000, null), service.getPricing())
        assertIs<Outcome.Fail>(service.savePricing(Pricing(0, null)))
        assertIs<Outcome.Fail>(service.savePricing(Pricing(null, null)))
        assertEquals(100_000L, service.getPricing().monthlyCents)
    }

    @Test
    fun invalidDiscountsAreRejected() {
        assertIs<Outcome.Fail>(service.saveDiscount(StudentDiscount(DiscountType.Percentage, 101, true)))
        assertIs<Outcome.Fail>(service.saveDiscount(StudentDiscount(DiscountType.Fixed, 0, true)))
        assertNull(service.getDiscount())
    }

    @Test
    fun discountRoundTripsAndLegacyPercentIsHonoured() {
        assertNull(service.getDiscount())
        settings.set(SettingKeys.STUDENT_DISCOUNT_PERCENT, "20")
        assertEquals(StudentDiscount(DiscountType.Percentage, 20, true), service.getDiscount())
        setDiscount(DiscountType.Fixed, 25_000, enabled = false)
        assertEquals(StudentDiscount(DiscountType.Fixed, 25_000, false), service.getDiscount())
    }

    @Test
    fun renewingWhileActiveStartsDayAfterCurrentEnd() {
        setPrices()
        val reg = service.register(newMember()).ok()
        today = LocalDate.of(2026, 2, 1)
        service.renew(reg.memberId, MembershipKind.Monthly).ok()
        assertEquals(
            "2026-02-16,2026-03-16",
            scalar("SELECT start_date || ',' || end_date FROM membership_records ORDER BY id DESC LIMIT 1"),
        )
        assertEquals("MEMBERSHIP_RENEWAL", scalar("SELECT type FROM transactions ORDER BY id DESC LIMIT 1"))
    }

    @Test
    fun renewingWhenExpiredStartsToday() {
        setPrices()
        val reg = service.register(newMember()).ok()
        today = LocalDate.of(2026, 6, 1)
        assertEquals(MemberStatus.Expired, service.getMember(reg.memberId)!!.status)
        service.renew(reg.memberId, MembershipKind.Monthly).ok()
        assertEquals(
            "2026-06-01,2026-07-01",
            scalar("SELECT start_date || ',' || end_date FROM membership_records ORDER BY id DESC LIMIT 1"),
        )
    }

    @Test
    fun renewalKeepsTheStudentDiscount() {
        setPrices()
        setDiscount(DiscountType.Fixed, 25_000)
        val reg = service.register(newMember(student = true)).ok()
        today = LocalDate.of(2026, 6, 1)
        assertEquals(75_000L, service.renew(reg.memberId, MembershipKind.Monthly).ok().totalCents)
    }

    @Test
    fun searchFindsByNameNumberAndContact() {
        setPrices()
        service.register(newMember()).ok()
        assertEquals(1, service.search("juan").size)
        assertEquals(1, service.search("0001A").size)
        assertEquals(1, service.search("0917").size)
        assertEquals(0, service.search("nobody").size)
        assertEquals(1, service.search("").size)
    }

    @Test
    fun statsCountActiveExpiringAndExpired() {
        setPrices()
        service.register(newMember()).ok()
        assertEquals(MembershipStats(1, 1, 0, 0), service.stats())
        today = LocalDate.of(2026, 2, 10)
        assertEquals(MembershipStats(1, 1, 1, 0), service.stats())
        today = LocalDate.of(2026, 3, 1)
        assertEquals(MembershipStats(1, 0, 0, 1), service.stats())
    }

    @Test
    fun renewalHistoryListsRenewalsAndFilters() {
        setPrices()
        val reg = service.register(newMember()).ok()
        assertEquals(0, service.renewals("", null).size)
        today = LocalDate.of(2026, 2, 1)
        service.renew(reg.memberId, MembershipKind.Monthly).ok()
        val rows = service.renewals("", null)
        assertEquals(1, rows.size)
        assertEquals("Monthly", rows[0].previousPlan)
        assertEquals("Monthly", rows[0].newPlan)
        assertEquals("Feb 15, 2026", rows[0].previousExpiry)
        assertEquals("Mar 16, 2026", rows[0].newExpiry)
        assertEquals(1, service.renewals("juan", MembershipKind.Monthly).size)
        assertEquals(0, service.renewals("", MembershipKind.Daily).size)
        assertEquals(0, service.renewals("nobody", null).size)
    }
}