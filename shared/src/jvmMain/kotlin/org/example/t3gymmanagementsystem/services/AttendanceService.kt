package org.example.t3gymmanagementsystem.services

import org.example.t3gymmanagementsystem.AttendanceRow
import org.example.t3gymmanagementsystem.CardInfo
import org.example.t3gymmanagementsystem.CheckInResult
import org.example.t3gymmanagementsystem.CheckInStatus
import org.example.t3gymmanagementsystem.DeniedRow
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.PaymentInfo
import org.example.t3gymmanagementsystem.WalkInResult
import org.example.t3gymmanagementsystem.WalkInRow
import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.AttendanceRepository
import org.example.t3gymmanagementsystem.data.repositories.DeniedScanRepository
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.PaymentRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingKeys
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Check-in rules (attendance is time-in only):
 *  - A member can check in only while their latest membership has not ended.
 *  - Only the first check-in of a day is recorded; later taps that day report the earlier time.
 *  - Cards are identified by their ID only. Nothing is read from or written to the card's memory.
 *  - A card tap that is refused (expired, no membership, card not assigned) is kept in the denied-cards log.
 *    Staff manual check-ins are not card taps and are not logged there.
 */
class AttendanceService(
    private val db: DatabaseManager,
    private val settings: SettingsRepository,
    private val members: MemberRepository,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {
    private val repo = AttendanceRepository(db)
    private val deniedRepo = DeniedScanRepository(db)
    private val payments = PaymentRepository(db)
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    private val dayFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)

    private fun timeLabel(iso: String): String =
        runCatching { LocalDateTime.parse(iso).format(timeFormat) }.getOrDefault(iso)

    // ---- cards ----

    fun getCard(memberId: Long): CardInfo? = repo.activeCard(memberId)?.let { card ->
        val day = runCatching { LocalDateTime.parse(card.assignedAt).format(dayFormat) }.getOrDefault(card.assignedAt)
        CardInfo(card.uid, "Assigned $day")
    }

    fun assignCard(memberId: Long, rawUid: String): Outcome<Unit> {
        val uid = normalizeUid(rawUid)
            ?: return Outcome.Fail("That is not a valid card ID. A card ID has 8, 14 or 20 letters and digits (0-9, A-F).")
        if (members.find(memberId) == null) return Outcome.Fail("Member not found.")
        val owner = repo.memberIdForCard(uid)
        if (owner == memberId) return Outcome.Ok(Unit)
        if (owner != null) {
            val other = members.find(owner)
            val who = if (other != null) "${other.name} (${other.number})" else "another member"
            return Outcome.Fail("This card is already assigned to $who.")
        }
        repo.assignCard(memberId, uid)
        return Outcome.Ok(Unit)
    }

    fun removeCard(memberId: Long): Outcome<Unit> {
        if (!repo.removeCard(memberId)) return Outcome.Fail("This member has no card assigned.")
        return Outcome.Ok(Unit)
    }

    // ---- check-in ----

    fun checkInByCard(rawUid: String): CheckInResult {
        val uid = normalizeUid(rawUid)
        val memberId = uid?.let { repo.memberIdForCard(it) }
        if (memberId == null) {
            val seen = uid ?: rawUid.uppercase().filter { it.isLetterOrDigit() }.take(20)
            deniedRepo.insert(null, seen.ifEmpty { null }, "UNKNOWN_CARD", now().format(stamp))
            return CheckInResult(
                CheckInStatus.UnknownCard, null, null,
                "This card is not assigned to a member. Assign it from the member's profile.", null,
            )
        }
        return checkIn(memberId, "NFC", uid)
    }

    fun checkInMember(memberId: Long): CheckInResult = checkIn(memberId, "MANUAL", null)

    private fun checkIn(memberId: Long, source: String, uid: String?): CheckInResult {
        val member = members.find(memberId)
            ?: return CheckInResult(CheckInStatus.UnknownCard, null, null, "Member not found.", null)
        val at = now()
        val date = at.toLocalDate()

        fun result(status: CheckInStatus, detail: String, time: String? = null) =
            CheckInResult(status, member.name, member.number, detail, time)

        fun deny(reason: String) {
            if (source == "NFC") deniedRepo.insert(memberId, uid, reason, at.format(stamp))
        }

        val end = members.latestEndDate(memberId)?.let { LocalDate.parse(it) }
        if (end == null) {
            deny("NO_MEMBERSHIP")
            return result(CheckInStatus.NoMembership, "No membership on record. Register a plan first.")
        }
        if (end.isBefore(date)) {
            deny("EXPIRED")
            return result(CheckInStatus.Expired, "Membership ended on ${end.format(dayFormat)}. Renew it to check in.")
        }
        val earlier = repo.firstCheckInOn(memberId, date.toString())
        if (earlier != null) {
            val time = timeLabel(earlier)
            return result(CheckInStatus.AlreadyIn, "Already checked in today at $time.", time)
        }
        repo.insertAttendance(memberId, source, uid, at.format(stamp))
        return result(CheckInStatus.Welcome, "Membership runs until ${end.format(dayFormat)}.", at.format(timeFormat))
    }

    fun todayAttendance(): List<AttendanceRow> = repo.attendanceOn(now().toLocalDate().toString()).map {
        AttendanceRow(timeLabel(it.timeIn), it.memberName, it.memberNumber, if (it.source == "NFC") "Card" else "Manual", "A-%04d".format(it.logId), it.cardUid ?: "\u2014")
    }

    fun todayDenied(): List<DeniedRow> = deniedRepo.on(now().toLocalDate().toString()).map {
        DeniedRow(
            timeLabel(it.scannedAt),
            it.memberName ?: "Unknown card",
            when (it.reason) {
                "EXPIRED" -> "Membership expired"
                "NO_MEMBERSHIP" -> "No membership"
                else -> "Unknown card"
            },
            it.cardUid ?: "\u2014",
        )
    }

    // ---- walk-ins ----

    fun recordWalkIn(visitor: String, payment: PaymentInfo): Outcome<WalkInResult> {
        val price = settings.get(SettingKeys.PRICE_WALK_IN_CENTS)?.toLongOrNull()
        if (price == null || price <= 0) return Outcome.Fail("Set the walk-in price on the Memberships page first.")
        val at = now()
        return db.transaction<Outcome<WalkInResult>> { c ->
            val reference = members.nextReference(c, at.toLocalDate())
            val transactionId = repo.insertWalkInTransaction(c, reference, price)
            payments.insertItem(c, transactionId, "Walk-in visit", 1, price, price)
            payments.insertPayment(c, transactionId, payment.method.code, price, payment.note.trim().ifEmpty { null })
            repo.insertWalkIn(c, transactionId, visitor.trim().ifEmpty { null }, at.format(stamp))
            Outcome.Ok(WalkInResult(reference, price))
        }
    }

    fun todayWalkIns(): List<WalkInRow> = repo.walkInsOn(now().toLocalDate().toString()).map {
        WalkInRow(timeLabel(it.visitedAt), it.visitor ?: "Walk-in", it.totalCents, it.reference)
    }

    companion object {
        private val UID_PATTERN = Regex("[0-9A-F]{8}|[0-9A-F]{14}|[0-9A-F]{20}")

        /** Upper-case hex without separators, or null when it is not a 4, 7 or 10 byte card ID. */
        fun normalizeUid(raw: String): String? {
            val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }
            return if (UID_PATTERN.matches(cleaned)) cleaned else null
        }
    }
}