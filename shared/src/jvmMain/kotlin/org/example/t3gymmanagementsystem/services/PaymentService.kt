package org.example.t3gymmanagementsystem.services

import org.example.t3gymmanagementsystem.GymInfo
import org.example.t3gymmanagementsystem.PaymentMethod
import org.example.t3gymmanagementsystem.ReceiptData
import org.example.t3gymmanagementsystem.ReceiptItem
import org.example.t3gymmanagementsystem.TransactionRow
import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.PaymentRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reads for the Payments page and receipts. Sales themselves are recorded by MembershipService. */
class PaymentService(db: DatabaseManager) {
    private val repo = PaymentRepository(db)
    private val stats = org.example.t3gymmanagementsystem.data.repositories.PaymentStatsRepository(db)
    private val dateTime = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a", Locale.ENGLISH)

    fun transactions(query: String): List<TransactionRow> = repo.transactions(query).map { r ->
        TransactionRow(
            id = r.id,
            reference = r.reference,
            dateLabel = dateLabel(r.createdAt),
            typeLabel = typeLabel(r.type),
            memberName = r.memberName ?: "\u2014",
            methodLabel = r.method?.let { methodLabel(it) } ?: "None",
            totalCents = r.totalCents,
        )
    }

    fun summary(today: java.time.LocalDate = java.time.LocalDate.now()): org.example.t3gymmanagementsystem.PaymentSummary {
        val s = stats.summary(today.toString())
        return org.example.t3gymmanagementsystem.PaymentSummary(s.count, s.membershipCents, s.productCents, s.walkInCents, s.pendingCents)
    }

    fun receipt(transactionId: Long, gym: GymInfo): ReceiptData? {
        val head = repo.receiptHead(transactionId) ?: return null
        return ReceiptData(
            gymName = gym.name,
            gymAddress = gym.address,
            gymContact = gym.contact,
            reference = head.reference,
            dateLabel = dateLabel(head.createdAt),
            typeLabel = typeLabel(head.type),
            memberName = head.memberName,
            memberNumber = head.memberNumber,
            items = repo.receiptItems(transactionId).map { ReceiptItem(it.description, it.quantity, it.unitCents, it.totalCents) },
            subtotalCents = head.subtotalCents,
            discountCents = head.discountCents,
            totalCents = head.totalCents,
            paymentMethod = head.method?.let { methodLabel(it) },
            paymentNote = head.note,
        )
    }

    private fun dateLabel(iso: String): String =
        runCatching { LocalDateTime.parse(iso).format(dateTime) }.getOrDefault(iso)

    private fun methodLabel(code: String): String = PaymentMethod.entries.firstOrNull { it.code == code }?.label ?: code

    private fun typeLabel(type: String): String = when (type) {
        "MONTHLY_MEMBERSHIP" -> "Monthly membership"
        "DAILY_MEMBERSHIP" -> "Daily membership"
        "MEMBERSHIP_RENEWAL" -> "Membership renewal"
        "WALK_IN" -> "Walk-in visit"
        "PRODUCT_SALE" -> "Product sale"
        else -> type
    }
}