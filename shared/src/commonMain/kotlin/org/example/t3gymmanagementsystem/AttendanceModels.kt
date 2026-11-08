package org.example.t3gymmanagementsystem

/** What the card reader reports: its state, and what happens to cards on it. */
sealed interface ReaderEvent {
    data class Status(val message: String, val connected: Boolean) : ReaderEvent

    /** A card was detected and its ID is being read. */
    object Reading : ReaderEvent

    /** One card tap. Sent once per card, never repeated while the same card stays on the reader. */
    data class Scan(val uid: String) : ReaderEvent

    /** A card was detected but its ID could not be read. */
    data class ReadFailed(val message: String) : ReaderEvent

    /** The card left the reader. */
    object Removed : ReaderEvent
}

enum class CheckInStatus { Welcome, AlreadyIn, Expired, NoMembership, UnknownCard }

data class CheckInResult(
    val status: CheckInStatus,
    val memberName: String? = null,
    val memberNumber: String? = null,
    val detail: String = "",
    val timeLabel: String? = null,
)

data class AttendanceRow(val timeLabel: String, val memberName: String, val memberNumber: String, val source: String, val logId: String = "", val cardUid: String = "\u2014")

data class CardInfo(val uid: String, val assignedLabel: String)

/** A card tap that was refused. [who] is the member's name, or "Unknown card". */
data class DeniedRow(val timeLabel: String, val who: String, val reason: String, val cardUid: String)

data class WalkInRow(val timeLabel: String, val visitor: String, val totalCents: Long, val reference: String)

data class WalkInResult(val reference: String, val totalCents: Long = 0L)