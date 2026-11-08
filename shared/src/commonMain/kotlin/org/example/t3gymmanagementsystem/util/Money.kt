package org.example.t3gymmanagementsystem.util

import kotlin.math.abs

/** Money is handled as integer centavos everywhere; these helpers convert to and from text. */
object Money {
    private const val PESO = "\u20B1"

    /** "1500", "1,500.5", "1500.50", ".5" -> centavos. Null when invalid, negative or more than 2 decimals. */
    fun parsePesos(text: String): Long? {
        val t = text.trim().removePrefix(PESO).trim().replace(",", "")
        if (t.isEmpty()) return null
        val parts = t.split(".")
        if (parts.size > 2) return null
        val whole = parts[0]
        val frac = if (parts.size == 2) parts[1] else ""
        if (whole.isEmpty() && frac.isEmpty()) return null
        if (!whole.all { it.isDigit() } || !frac.all { it.isDigit() }) return null
        if (frac.length > 2 || whole.length > 9) return null
        val w = if (whole.isEmpty()) 0L else whole.toLong()
        val f = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 10
            else -> frac.toLong()
        }
        return w * 100 + f
    }

    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val absolute = abs(cents)
        val grouped = (absolute / 100).toString().reversed().chunked(3).joinToString(",").reversed()
        val fraction = (absolute % 100).toString().padStart(2, '0')
        return "$sign$PESO$grouped.$fraction"
    }

    /** Plain editable text such as "1500.00". */
    fun plainInput(cents: Long): String = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}