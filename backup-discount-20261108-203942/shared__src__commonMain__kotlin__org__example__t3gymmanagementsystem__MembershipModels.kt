package org.example.t3gymmanagementsystem

sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Fail(val message: String) : Outcome<Nothing>
}

enum class MembershipKind(val label: String) { Monthly("Monthly"), Daily("Daily") }
enum class MemberStatus(val label: String) { Active("Active"), Expired("Expired"), NoMembership("No membership") }

/** No longer required: staff verify students themselves. Removed from the screens in the next part. */
enum class StudentDocument(val label: String) { StudentId("Student ID"), Cor("COR") }

enum class DiscountType(val label: String) { Percentage("Percentage"), Fixed("Fixed amount") }

/** Percentage: value is a whole percent (0 to 100). Fixed: value is centavos taken off the Monthly price. */
data class StudentDiscount(val type: DiscountType, val value: Long, val enabled: Boolean)

/** A null price means "leave unchanged" when saving and "not set" when reading. */
data class Pricing(val monthlyCents: Long?, val dailyCents: Long?, val walkInCents: Long? = null)

data class Quote(val subtotalCents: Long, val discountCents: Long, val totalCents: Long, val periodLabel: String)

data class NewMember(
    val fullName: String,
    val contact: String,
    val email: String,
    val address: String,
    val emergencyContact: String,
    val kind: MembershipKind,
    /** True when staff checked and confirmed the person is an eligible student. */
    val isStudent: Boolean,
    val studentDocument: StudentDocument? = null,
    val schoolName: String = "",
    val payment: PaymentInfo = PaymentInfo(),
    val studentIdNumber: String = "",
    /** The chosen plan. When null, the first plan of [kind] is used. */
    val planId: Long? = null,
)

data class Registration(
    val memberId: Long,
    val memberNumber: String,
    val reference: String,
    val totalCents: Long,
    val periodLabel: String,
)

data class MemberRow(
    val id: Long,
    val number: String,
    val name: String,
    val contact: String,
    val membershipLabel: String,
    val endLabel: String,
    val isStudent: Boolean = false,
    val expiringSoon: Boolean = false,
    val status: MemberStatus,
)

data class MembershipEntry(val kindLabel: String, val periodLabel: String, val totalCents: Long?, val reference: String?)

data class MemberDetail(
    val id: Long,
    val number: String,
    val name: String,
    val contact: String,
    val email: String,
    val address: String,
    val emergencyContact: String,
    val registeredLabel: String,
    val studentLabel: String?,
    val status: MemberStatus,
    val lastKind: MembershipKind?,
    val history: List<MembershipEntry>,
)

/** Active includes members whose membership ends within 7 days (expiringSoon). */
data class MembershipStats(val total: Int, val active: Int, val expiringSoon: Int, val expired: Int)

data class RenewalRow(
    val reference: String,
    val memberName: String,
    val memberNumber: String,
    val previousPlan: String,
    val newPlan: String,
    val previousExpiry: String,
    val newExpiry: String,
    val renewedLabel: String,
)