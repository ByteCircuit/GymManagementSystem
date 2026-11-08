package org.example.t3gymmanagementsystem.util

import org.example.t3gymmanagementsystem.ReceiptData

/**
 * Fixed-width receipt text (40 characters per line). The same text is used for the on-screen preview,
 * the printer and the PDF. Amounts print as "PHP 1,500.00" because the PDF's built-in font has no peso sign.
 */
object ReceiptText {
    const val WIDTH = 40

    fun peso(cents: Long): String {
        val whole = (cents / 100).toString()
        val frac = (cents % 100).toString().padStart(2, '0')
        val grouped = buildString {
            whole.forEachIndexed { i, ch ->
                if (i > 0 && (whole.length - i) % 3 == 0) append(',')
                append(ch)
            }
        }
        return "PHP $grouped.$frac"
    }

    fun render(r: ReceiptData): String {
        val out = mutableListOf<String>()

        fun center(s: String) {
            wrap(s.trim(), WIDTH).forEach { out += it.padStart((WIDTH + it.length) / 2) }
        }
        fun rule() { out += "-".repeat(WIDTH) }
        fun pair(left: String, right: String) {
            val gap = WIDTH - left.length - right.length
            if (gap >= 1) {
                out += left + " ".repeat(gap) + right
            } else {
                wrap(left, WIDTH).forEach { out += it }
                out += right.padStart(WIDTH)
            }
        }

        center(r.gymName)
        center(r.gymAddress)
        center(r.gymContact)
        rule()
        center("RECEIPT")
        rule()
        pair("Ref", r.reference)
        pair("Date", r.dateLabel)
        pair("Type", r.typeLabel)
        r.memberName?.let { pair("Member", it) }
        r.memberNumber?.let { pair("Member no.", it) }
        rule()
        r.items.forEach { item ->
            wrap(item.description, WIDTH).forEach { out += it }
            pair("  ${item.quantity} x ${peso(item.unitCents)}", peso(item.totalCents))
        }
        rule()
        pair("Subtotal", peso(r.subtotalCents))
        if (r.discountCents > 0) pair("Discount", "-" + peso(r.discountCents))
        pair("TOTAL", peso(r.totalCents))
        rule()
        if (r.paymentMethod != null) {
            pair("Paid by", r.paymentMethod)
            r.paymentNote?.takeIf { it.isNotBlank() }?.let { wrap("Note: $it", WIDTH).forEach { line -> out += line } }
        } else {
            out += "No payment due."
        }
        rule()
        center("Thank you!")
        return out.joinToString("\n")
    }

    private fun wrap(text: String, width: Int): List<String> {
        val lines = mutableListOf<String>()
        var cur = ""
        for (raw in text.split(" ").filter { it.isNotEmpty() }) {
            var word = raw
            while (word.length > width) {
                if (cur.isNotEmpty()) {
                    lines += cur
                    cur = ""
                }
                lines += word.take(width)
                word = word.drop(width)
            }
            cur = when {
                cur.isEmpty() -> word
                cur.length + 1 + word.length <= width -> "$cur $word"
                else -> {
                    lines += cur
                    word
                }
            }
        }
        if (cur.isNotEmpty()) lines += cur
        return lines
    }
}