package org.example.t3gymmanagementsystem

/** Cash, or Online (the gym shows its own QR; there is no payment integration). */
enum class PaymentMethod(val code: String, val label: String) {
    Cash("Cash", "Cash"),
    Online("Online", "Online QR"),
}

data class PaymentInfo(val method: PaymentMethod = PaymentMethod.Cash, val note: String = "")

data class TransactionRow(
    val id: Long,
    val reference: String,
    val dateLabel: String,
    val typeLabel: String,
    val memberName: String,
    val methodLabel: String,
    val totalCents: Long,
)

data class ReceiptItem(val description: String, val quantity: Int, val unitCents: Long, val totalCents: Long)

/** Figures for the Payments page. Today's numbers count completed sales only; pending is the total still awaiting payment. */
data class PaymentSummary(
    val todayCount: Int,
    val membershipCents: Long,
    val productCents: Long,
    val walkInCents: Long,
    val pendingCents: Long,
)

data class ReceiptData(
    val gymName: String,
    val gymAddress: String,
    val gymContact: String,
    val reference: String,
    val dateLabel: String,
    val typeLabel: String,
    val memberName: String?,
    val memberNumber: String?,
    val items: List<ReceiptItem>,
    val subtotalCents: Long,
    val discountCents: Long,
    val totalCents: Long,
    /** Null when nothing was paid (a zero total). */
    val paymentMethod: String?,
    val paymentNote: String?,
)