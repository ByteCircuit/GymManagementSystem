package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.util.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyTest {
    private val peso = "\u20B1"

    @Test
    fun parsesCommonInputs() {
        assertEquals(150_000L, Money.parsePesos("1500"))
        assertEquals(150_050L, Money.parsePesos("1,500.5"))
        assertEquals(150_050L, Money.parsePesos("1500.50"))
        assertEquals(50L, Money.parsePesos(".5"))
        assertEquals(2_000L, Money.parsePesos(peso + "20"))
    }

    @Test
    fun rejectsInvalidInputs() {
        assertNull(Money.parsePesos(""))
        assertNull(Money.parsePesos("abc"))
        assertNull(Money.parsePesos("-5"))
        assertNull(Money.parsePesos("1.234"))
        assertNull(Money.parsePesos("1.2.3"))
        assertNull(Money.parsePesos("."))
    }

    @Test
    fun formatsWithGroupingAndTwoDecimals() {
        assertEquals(peso + "1,500.00", Money.format(150_000))
        assertEquals(peso + "0.05", Money.format(5))
        assertEquals(peso + "1,234,567.89", Money.format(123_456_789))
    }

    @Test
    fun plainInputRoundTrips() {
        assertEquals("1500.00", Money.plainInput(150_000))
        assertEquals(150_000L, Money.parsePesos(Money.plainInput(150_000)))
    }
}