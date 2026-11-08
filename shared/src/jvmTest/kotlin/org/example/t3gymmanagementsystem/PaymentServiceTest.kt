package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import org.example.t3gymmanagementsystem.services.MembershipService
import org.example.t3gymmanagementsystem.services.PaymentService
import org.example.t3gymmanagementsystem.util.ReceiptText
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentServiceTest {
    private lateinit var dir: Path
    private lateinit var memberships: MembershipService
    private lateinit var payments: PaymentService
    private val gym = GymInfo("Test Gym", "1 Test Street", "09990000000")

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-payments")
        val db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        memberships = MembershipService(db, SettingsRepository(db), MemberRepository(db)) { LocalDate.of(2026, 1, 15) }
        payments = PaymentService(db)
        memberships.savePricing(Pricing(100_000, 15_000))
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun register(name: String, student: Boolean, payment: PaymentInfo = PaymentInfo()): Registration {
        val r = memberships.register(
            NewMember(name, "0917", "", "", "", MembershipKind.Monthly, isStudent = student, payment = payment)
        )
        assertIs<Outcome.Ok<Registration>>(r)
        return r.value
    }

    @Test
    fun transactionListAndReceiptReflectTheSale() {
        memberships.saveDiscount(StudentDiscount(DiscountType.Percentage, 20, true))
        val reg = register("Juan Dela Cruz", student = true, payment = PaymentInfo(PaymentMethod.Online, "GCash 123"))

        val rows = payments.transactions("")
        assertEquals(1, rows.size)
        assertEquals(reg.reference, rows[0].reference)
        assertEquals("Monthly membership", rows[0].typeLabel)
        assertEquals("Online QR", rows[0].methodLabel)
        assertEquals(80_000L, rows[0].totalCents)
        assertEquals("Juan Dela Cruz", rows[0].memberName)

        val receipt = payments.receipt(rows[0].id, gym)
        assertNotNull(receipt)
        assertEquals(1, receipt.items.size)
        assertEquals(100_000L, receipt.subtotalCents)
        assertEquals(20_000L, receipt.discountCents)
        assertEquals(80_000L, receipt.totalCents)
        assertEquals("GCash 123", receipt.paymentNote)
        assertEquals("0001A", receipt.memberNumber)

        val text = ReceiptText.render(receipt)
        assertTrue(text.contains("PHP 800.00"))
        assertTrue(text.contains("Test Gym"))
        text.split("\n").forEach { assertTrue(it.length <= ReceiptText.WIDTH) }
    }

    @Test
    fun searchFindsByReferenceNameAndNumber() {
        val reg = register("Juan Dela Cruz", student = false)
        assertEquals(1, payments.transactions("juan").size)
        assertEquals(1, payments.transactions("0001A").size)
        assertEquals(1, payments.transactions(reg.reference).size)
        assertEquals(0, payments.transactions("nobody").size)
    }

    @Test
    fun newestTransactionComesFirst() {
        register("First Person", student = false)
        register("Second Person", student = false)
        val rows = payments.transactions("")
        assertEquals(listOf("Second Person", "First Person"), rows.map { it.memberName })
    }

    @Test
    fun zeroTotalSaleHasNoPaymentMethod() {
        memberships.saveDiscount(StudentDiscount(DiscountType.Fixed, 150_000, true))
        register("Free Student", student = true)
        val row = payments.transactions("").first()
        assertEquals("None", row.methodLabel)
        val receipt = payments.receipt(row.id, gym)
        assertNotNull(receipt)
        assertNull(receipt.paymentMethod)
    }

    @Test
    fun receiptForUnknownTransactionIsNull() {
        assertNull(payments.receipt(999, gym))
    }
}