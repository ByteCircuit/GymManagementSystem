package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.util.ReceiptText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReceiptTextTest {
    private fun sample(discount: Long = 0, paymentMethod: String? = "Cash", description: String = "Monthly membership (Jan 15, 2026 to Feb 15, 2026)") =
        ReceiptData(
            gymName = "Sample Gym", gymAddress = "123 Sample Street, Sample City", gymContact = "09170000000",
            reference = "TX-20260115-0001", dateLabel = "Jan 15, 2026 09:30 AM", typeLabel = "Monthly membership",
            memberName = "Juan Dela Cruz", memberNumber = "0001A",
            items = listOf(ReceiptItem(description, 1, 100_000, 100_000)),
            subtotalCents = 100_000, discountCents = discount, totalCents = 100_000 - discount,
            paymentMethod = paymentMethod, paymentNote = null,
        )

    @Test
    fun pesoFormatsWithGroupingAndTwoDecimals() {
        assertEquals("PHP 0.05", ReceiptText.peso(5))
        assertEquals("PHP 1,500.00", ReceiptText.peso(150_000))
        assertEquals("PHP 1,234,567.89", ReceiptText.peso(123_456_789))
        assertEquals("PHP 999.99", ReceiptText.peso(99_999))
    }

    @Test
    fun noLineIsWiderThanTheReceipt() {
        val text = ReceiptText.render(sample(discount = 20_000, description = "A very long description " + "word ".repeat(30) + "x".repeat(60)))
        text.split("\n").forEach { assertTrue(it.length <= ReceiptText.WIDTH, "too wide: '$it'") }
    }

    @Test
    fun showsTotalsAndOnlyShowsDiscountWhenThereIsOne() {
        val withDiscount = ReceiptText.render(sample(discount = 20_000))
        assertTrue(withDiscount.contains("PHP 800.00"))
        assertTrue(withDiscount.contains("Discount"))
        assertTrue(withDiscount.contains("Sample Gym"))
        assertFalse(ReceiptText.render(sample()).contains("Discount"))
    }

    @Test
    fun zeroTotalSaysNoPaymentDue() {
        val text = ReceiptText.render(sample(discount = 100_000, paymentMethod = null))
        assertTrue(text.contains("No payment due."))
        assertFalse(text.contains("Paid by"))
    }
}