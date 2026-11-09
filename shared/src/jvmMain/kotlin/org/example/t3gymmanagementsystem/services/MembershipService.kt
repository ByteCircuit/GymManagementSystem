package org.example.t3gymmanagementsystem.services

import org.example.t3gymmanagementsystem.AttendanceRow
import org.example.t3gymmanagementsystem.CardInfo
import org.example.t3gymmanagementsystem.CheckInResult
import org.example.t3gymmanagementsystem.CheckInStatus
import org.example.t3gymmanagementsystem.WalkInRow
import org.example.t3gymmanagementsystem.WalkInResult
import org.example.t3gymmanagementsystem.DiscountType
import org.example.t3gymmanagementsystem.EligibilityType
import org.example.t3gymmanagementsystem.MemberDetail
import org.example.t3gymmanagementsystem.MemberRow
import org.example.t3gymmanagementsystem.MemberStatus
import org.example.t3gymmanagementsystem.MembershipEntry
import org.example.t3gymmanagementsystem.MembershipKind
import org.example.t3gymmanagementsystem.MembershipStats
import org.example.t3gymmanagementsystem.NewMember
import org.example.t3gymmanagementsystem.Outcome
import org.example.t3gymmanagementsystem.PaymentInfo
import org.example.t3gymmanagementsystem.Plan
import org.example.t3gymmanagementsystem.PlanInput
import org.example.t3gymmanagementsystem.Pricing
import org.example.t3gymmanagementsystem.Quote
import org.example.t3gymmanagementsystem.Registration
import org.example.t3gymmanagementsystem.RenewalRow
import org.example.t3gymmanagementsystem.StudentDiscount
import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.EligibilityRepository
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.PaymentRepository
import org.example.t3gymmanagementsystem.data.repositories.PlanRepository
import org.example.t3gymmanagementsystem.data.repositories.PlanRow
import org.example.t3gymmanagementsystem.data.repositories.RenewalRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingKeys
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import java.sql.Connection
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Membership rules confirmed by the owner:
 *  - A plan is Monthly (one calendar month from its start date) or Daily (one day).
 *  - Renewing while active starts the day after the current end date; if expired it starts today.
 *  - The student discount applies to Monthly plans only, is either a percentage or a fixed amount, and is
 *    granted when staff confirm eligibility. No certificate is required and the student ID is optional.
 * Prices and the discount come from the database; nothing is hardcoded. A percentage rounds half up to the
 * centavo and a fixed amount never exceeds the price.
 */
class MembershipService(
    private val db: DatabaseManager,
    private val settings: SettingsRepository,
    private val members: MemberRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private data class Price(val subtotal: Long, val discount: Long) {
        val total: Long get() = subtotal - discount
    }

    private val payments = PaymentRepository(db)
    private val renewalRepo = RenewalRepository(db)
    private val plansRepo = PlanRepository(db)
    private val eligibilityRepo = EligibilityRepository(db)
    /** Plans carry their own discounted price, so the old global percentage/fixed discount is switched off. */
    private val globalDiscountActive = false
    private val dateFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH)

    // ---- plans ----

    /** The first time plans are used, Monthly and Daily prices saved by earlier versions become plans. Runs once. */
    private fun ensurePlans() {
        if (settings.get(PLANS_MIGRATED_KEY) == "1") return
        if (plansRepo.count() == 0) {
            settings.get(SettingKeys.PRICE_MONTHLY_CENTS)?.toLongOrNull()?.takeIf { it > 0 }
                ?.let { plansRepo.insert("Monthly", MembershipKind.Monthly, it) }
            settings.get(SettingKeys.PRICE_DAILY_CENTS)?.toLongOrNull()?.takeIf { it > 0 }
                ?.let { plansRepo.insert("Daily", MembershipKind.Daily, it) }
        }
        settings.setAll(mapOf(PLANS_MIGRATED_KEY to "1"))
    }

    fun listPlans(): List<Plan> {
        ensurePlans()
        val typeNames = eligibilityRepo.names()
        return plansRepo.list().map { Plan(it.id, it.name, it.kind, it.priceCents, plansRepo.usage(it.id) > 0, it.eligibilityTypeId, it.eligibilityTypeId?.let { tid -> typeNames[tid] }, durationCount = it.durationCount) }
    }

    fun savePlan(input: PlanInput): Outcome<Long> {
        if (input.durationCount < 1 || input.durationCount > (if (input.kind == MembershipKind.Monthly) 120 else 3650)) return Outcome.Fail("Enter a duration of 1 to 120 months or 1 to 3650 days.")
        ensurePlans()
        val name = input.name.trim()
        if (name.isEmpty()) return Outcome.Fail("Enter a plan name.")
        if (name.length > 40) return Outcome.Fail("Keep the plan name to 40 characters or fewer.")
        if (input.priceCents <= 0) return Outcome.Fail("Price must be greater than 0.")
        if (plansRepo.activeNameExists(name, input.id)) return Outcome.Fail("A plan named \"$name\" already exists.")
        if (input.eligibilityTypeId != null && eligibilityRepo.find(input.eligibilityTypeId)?.active != true) return Outcome.Fail("That eligibility type is no longer available.")
        val id = input.id ?: return Outcome.Ok(plansRepo.insertWith(name, input.kind, input.priceCents, input.eligibilityTypeId, input.durationCount))
        val existing = plansRepo.find(id)
        if (existing == null || !existing.active) return Outcome.Fail("That plan is no longer available.")
        if (existing.kind != input.kind && plansRepo.usage(id) > 0) {
            return Outcome.Fail("The duration can't change after the plan has been used. Create a new plan instead.")
        }
        plansRepo.updateWith(id, name, input.kind, input.priceCents, input.eligibilityTypeId, input.durationCount)
        return Outcome.Ok(id)
    }

    /** Unused plans are deleted; plans members have used are archived so history keeps their name. */
    fun deletePlan(id: Long): Outcome<String> {
        val plan = plansRepo.find(id)
        if (plan == null || !plan.active) return Outcome.Fail("That plan is no longer available.")
        return if (plansRepo.usage(id) == 0) {
            plansRepo.delete(id)
            Outcome.Ok("Plan deleted.")
        } else {
            plansRepo.archive(id)
            Outcome.Ok("Plan archived. Members who used it keep their history, but it can't be chosen for new memberships.")
        }
    }

    private fun planFor(kind: MembershipKind, planId: Long?): Outcome<PlanRow> {
        ensurePlans()
        if (planId != null) {
            val plan = plansRepo.find(planId)
            return if (plan != null && plan.active) Outcome.Ok(plan) else Outcome.Fail("That plan is no longer available.")
        }
        val first = plansRepo.firstActive(kind)
        return if (first != null) Outcome.Ok(first) else Outcome.Fail("Set the ${kind.label} price on the Memberships page first.")
    }

    // ---- pricing (Monthly and Daily map to the first plan of that duration; walk-in is a setting) ----

    fun getPricing(): Pricing {
        ensurePlans()
        return Pricing(
            monthlyCents = plansRepo.firstActive(MembershipKind.Monthly)?.priceCents,
            dailyCents = plansRepo.firstActive(MembershipKind.Daily)?.priceCents,
            walkInCents = settings.get(SettingKeys.PRICE_WALK_IN_CENTS)?.toLongOrNull(),
        )
    }

    /** Saves only the prices that are not null. */
    fun savePricing(pricing: Pricing): Outcome<Unit> {
        ensurePlans()
        val entries = listOf("Monthly" to pricing.monthlyCents, "Daily" to pricing.dailyCents, "Walk-in" to pricing.walkInCents)
        var any = false
        for ((name, cents) in entries) {
            if (cents == null) continue
            any = true
            if (cents <= 0) return Outcome.Fail("$name price must be greater than 0.")
        }
        if (!any) return Outcome.Fail("Enter a price to save.")
        pricing.monthlyCents?.let { setDefaultPlanPrice(MembershipKind.Monthly, it) }
        pricing.dailyCents?.let { setDefaultPlanPrice(MembershipKind.Daily, it) }
        pricing.walkInCents?.let { settings.setAll(mapOf(SettingKeys.PRICE_WALK_IN_CENTS to it.toString())) }
        return Outcome.Ok(Unit)
    }

    private fun setDefaultPlanPrice(kind: MembershipKind, cents: Long) {
        val existing = plansRepo.firstActive(kind)
        if (existing != null) plansRepo.update(existing.id, existing.name, kind, cents)
        else plansRepo.insert(kind.label, kind, cents)
    }

    // ---- eligibility types ----

    fun listEligibilityTypes(): List<EligibilityType> =
        eligibilityRepo.listActive().map { EligibilityType(it.id, it.name, eligibilityRepo.usage(it.id) > 0) }

    fun saveEligibilityType(name: String): Outcome<Long> {
        val n = name.trim()
        if (n.isEmpty()) return Outcome.Fail("Enter a type name.")
        if (n.length > 30) return Outcome.Fail("Keep the name to 30 characters or fewer.")
        if (eligibilityRepo.activeNameExists(n)) return Outcome.Fail("\"$n\" already exists.")
        return Outcome.Ok(eligibilityRepo.insert(n))
    }

    /** Unused types are deleted; types in use are archived so members and plans keep them. */
    fun deleteEligibilityType(id: Long): Outcome<String> {
        val type = eligibilityRepo.find(id)
        if (type == null || !type.active) return Outcome.Fail("That type is no longer available.")
        return if (eligibilityRepo.usage(id) == 0) {
            eligibilityRepo.delete(id)
            Outcome.Ok("Type deleted.")
        } else {
            eligibilityRepo.archive(id)
            Outcome.Ok("Type archived. Members and plans that use it keep it, but it can't be chosen for new ones.")
        }
    }

    // ---- student discount ----

    fun getDiscount(): StudentDiscount? {
        val type = settings.get(DISCOUNT_TYPE_KEY)
        val value = settings.get(DISCOUNT_VALUE_KEY)?.toLongOrNull()
        if (type == null || value == null) {
            // A percentage saved by the earlier version of the app is still honoured.
            val legacy = settings.get(SettingKeys.STUDENT_DISCOUNT_PERCENT)?.toLongOrNull()
            return legacy?.let { StudentDiscount(DiscountType.Percentage, it, true) }
        }
        return StudentDiscount(
            type = if (type == "FIXED") DiscountType.Fixed else DiscountType.Percentage,
            value = value,
            enabled = settings.get(DISCOUNT_ENABLED_KEY) != "0",
        )
    }

    fun saveDiscount(discount: StudentDiscount): Outcome<Unit> {
        when (discount.type) {
            DiscountType.Percentage ->
                if (discount.value < 0 || discount.value > 100) return Outcome.Fail("Percentage must be between 0 and 100.")
            DiscountType.Fixed ->
                if (discount.value <= 0) return Outcome.Fail("Enter a discount amount greater than 0.")
        }
        settings.setAll(
            mapOf(
                DISCOUNT_TYPE_KEY to if (discount.type == DiscountType.Fixed) "FIXED" else "PERCENT",
                DISCOUNT_VALUE_KEY to discount.value.toString(),
                DISCOUNT_ENABLED_KEY to if (discount.enabled) "1" else "0",
            )
        )
        return Outcome.Ok(Unit)
    }

    private fun priceOf(plan: PlanRow, student: Boolean): Price {
        var discount = 0L
        if (globalDiscountActive && plan.kind == MembershipKind.Monthly && student && plan.eligibilityTypeId == null) {
            val rule = getDiscount()
            if (rule != null && rule.enabled) {
                discount = when (rule.type) {
                    DiscountType.Percentage -> (plan.priceCents * rule.value + 50) / 100
                    DiscountType.Fixed -> minOf(rule.value, plan.priceCents)
                }
            }
        }
        return Price(plan.priceCents, discount)
    }

    // ---- dates ----

    /** A plan lasts count months (Monthly) or count days (Daily). One day ends the day it starts. */
    private fun periodEnd(kind: MembershipKind, count: Int, start: LocalDate): LocalDate =
        if (kind == MembershipKind.Monthly) start.plusMonths(count.toLong()) else start.plusDays((count - 1).toLong())

    private fun periodEnd(kind: MembershipKind, start: LocalDate): LocalDate =
        if (kind == MembershipKind.Monthly) start.plusMonths(1) else start

    private fun label(iso: String): String = LocalDate.parse(iso).format(dateFormat)

    private fun periodLabel(start: LocalDate, end: LocalDate): String =
        if (start == end) start.format(dateFormat) else "${start.format(dateFormat)} to ${end.format(dateFormat)}"

    private fun renewalStart(memberId: Long): LocalDate {
        val lastEnd = members.latestEndDate(memberId)?.let { LocalDate.parse(it) }
        val now = today()
        return if (lastEnd != null && !lastEnd.isBefore(now)) lastEnd.plusDays(1) else now
    }

    private fun statusOf(endIso: String?): MemberStatus = when {
        endIso == null -> MemberStatus.NoMembership
        !LocalDate.parse(endIso).isBefore(today()) -> MemberStatus.Active
        else -> MemberStatus.Expired
    }

    private fun kindOf(type: String): MembershipKind =
        if (type == "MONTHLY") MembershipKind.Monthly else MembershipKind.Daily

    // ---- quotes ----

    fun quote(kind: MembershipKind, isStudent: Boolean): Outcome<Quote> =
        quoteWith(planFor(kind, null), isStudent, today())

    fun quotePlan(planId: Long, isStudent: Boolean): Outcome<Quote> {
        if (planId <= 0) return Outcome.Fail("Choose a plan.")
        return quoteWith(planFor(MembershipKind.Monthly, planId), isStudent, today())
    }

    fun quoteRenewal(memberId: Long, kind: MembershipKind): Outcome<Quote> {
        if (members.find(memberId) == null) return Outcome.Fail("Member not found.")
        return quoteWith(planFor(kind, null), members.isVerifiedStudent(memberId), renewalStart(memberId))
    }

    fun quoteRenewalPlan(memberId: Long, planId: Long): Outcome<Quote> {
        if (members.find(memberId) == null) return Outcome.Fail("Member not found.")
        if (planId <= 0) return Outcome.Fail("Choose a plan.")
        return quoteWith(planFor(MembershipKind.Monthly, planId), members.isVerifiedStudent(memberId), renewalStart(memberId))
    }

    private fun quoteWith(planResult: Outcome<PlanRow>, student: Boolean, start: LocalDate): Outcome<Quote> {
        val plan = when (planResult) {
            is Outcome.Fail -> return planResult
            is Outcome.Ok -> planResult.value
        }
        val price = priceOf(plan, student)
        val end = periodEnd(plan.kind, plan.durationCount, start)
        return Outcome.Ok(Quote(price.subtotal, price.discount, price.total, periodLabel(start, end)))
    }

    // ---- registration and renewal ----

    fun register(input: NewMember): Outcome<Registration> {
        val name = input.fullName.trim()
        val contact = input.contact.trim()
        if (name.isEmpty()) return Outcome.Fail("Enter the member's full name.")
        if (contact.isEmpty()) return Outcome.Fail("Enter a contact number.")
        val plan = when (val r = planFor(input.kind, input.planId)) {
            is Outcome.Fail -> return r
            is Outcome.Ok -> r.value
        }
        val typeId = if (input.isStudent) input.eligibilityTypeId else null
        if (input.isStudent && (typeId == null || eligibilityRepo.find(typeId)?.active != true)) return Outcome.Fail("Choose the eligibility type.")
        plan.eligibilityTypeId?.let { need ->
            if (typeId != need) return Outcome.Fail("This plan is only for ${eligibilityRepo.find(need)?.name ?: "eligible"} members. Verify the member first.")
        }
        val price = priceOf(plan, input.isStudent)
        val start = today()
        val end = periodEnd(plan.kind, plan.durationCount, start)
        val pattern = settings.getOrDefault(SettingKeys.MEMBER_NUMBER_FORMAT, "%04dA")

        return db.transaction<Outcome<Registration>> { c ->
            val number = members.nextNumber(c, pattern)
            val memberId = members.insertMember(
                c, number, name, contact,
                input.email.trim().ifEmpty { null },
                input.address.trim().ifEmpty { null },
                input.emergencyContact.trim().ifEmpty { null },
            )
            if (input.isStudent) {
                members.insertVerification(c, memberId, typeId, input.studentIdNumber.trim().ifEmpty { null }, input.schoolName.trim().ifEmpty { null }, input.eligibilityDocument)
            }
            val txType = if (plan.kind == MembershipKind.Monthly) "MONTHLY_MEMBERSHIP" else "DAILY_MEMBERSHIP"
            val description = "${plan.name} membership (${periodLabel(start, end)})"
            val reference = recordSale(c, memberId, txType, plan, price, start, end, null, input.payment, description)
            Outcome.Ok(Registration(memberId, number, reference, price.total, periodLabel(start, end)))
        }
    }

    fun renew(memberId: Long, kind: MembershipKind, payment: PaymentInfo = PaymentInfo()): Outcome<Registration> =
        renewWith(memberId, planFor(kind, null), payment)

    fun renewPlan(memberId: Long, planId: Long, payment: PaymentInfo = PaymentInfo()): Outcome<Registration> {
        if (planId <= 0) return Outcome.Fail("Choose a plan.")
        return renewWith(memberId, planFor(MembershipKind.Monthly, planId), payment)
    }

    private fun renewWith(memberId: Long, planResult: Outcome<PlanRow>, payment: PaymentInfo): Outcome<Registration> {
        val member = members.find(memberId) ?: return Outcome.Fail("Member not found.")
        val plan = when (planResult) {
            is Outcome.Fail -> return planResult
            is Outcome.Ok -> planResult.value
        }
        plan.eligibilityTypeId?.let { need ->
            if (members.verifiedTypeId(memberId) != need) return Outcome.Fail("This plan is only for ${eligibilityRepo.find(need)?.name ?: "eligible"} members. This member is not verified for it.")
        }
        val price = priceOf(plan, members.isVerifiedStudent(memberId))
        val start = renewalStart(memberId)
        val end = periodEnd(plan.kind, plan.durationCount, start)
        val previousId = members.latestMembershipId(memberId)

        return db.transaction<Outcome<Registration>> { c ->
            val description = "Renewal: ${plan.name} membership (${periodLabel(start, end)})"
            val reference = recordSale(c, memberId, "MEMBERSHIP_RENEWAL", plan, price, start, end, previousId, payment, description)
            Outcome.Ok(Registration(memberId, member.number, reference, price.total, periodLabel(start, end)))
        }
    }

    private fun recordSale(
        c: Connection, memberId: Long, txType: String, plan: PlanRow,
        price: Price, start: LocalDate, end: LocalDate, renewalOfId: Long?,
        payment: PaymentInfo, description: String,
    ): String {
        val reference = members.nextReference(c, today())
        val txId = members.insertTransaction(c, reference, txType, memberId, price.subtotal, price.discount, price.total)
        payments.insertItem(c, txId, description, 1, price.subtotal, price.subtotal)
        if (price.total > 0) {
            payments.insertPayment(c, txId, payment.method.code, price.total, payment.note.trim().ifEmpty { null })
        }
        members.insertMembership(c, memberId, plan.kind.name.uppercase(), start.toString(), end.toString(), txId, renewalOfId)
        plansRepo.tagMembership(c, txId, plan.id, plan.name)
        return reference
    }

    // ---- attendance, NFC cards and walk-ins ----

    val attendance: AttendanceService by lazy {
        AttendanceService(db, settings, members) { java.time.LocalDateTime.of(today(), java.time.LocalTime.now()) }
    }

    val inventory: InventoryService by lazy { InventoryService(db) }
    val reports: ReportService by lazy { ReportService(db) }
    val dashboard: DashboardService by lazy { DashboardService(db) }

    // ---- receipts and the payment QR ----

    val receipts: PaymentService by lazy { PaymentService(db) }

    fun getQr(): ByteArray? =
        settings.get(QR_KEY)?.takeIf { it.isNotBlank() }?.let { java.util.Base64.getDecoder().decode(it) }

    fun saveQr(bytes: ByteArray) {
        settings.setAll(mapOf(QR_KEY to java.util.Base64.getEncoder().encodeToString(bytes)))
    }

    fun removeQr() {
        settings.setAll(mapOf(QR_KEY to ""))
    }

    // ---- photos ----

    fun getPhoto(memberId: Long): ByteArray? = db.withConnection { c ->
        c.prepareStatement("SELECT photo FROM members WHERE id = ?").use { ps ->
            ps.setLong(1, memberId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getBytes(1) else null }
        }
    }

    fun savePhoto(memberId: Long, bytes: ByteArray): Outcome<Unit> {
        if (bytes.isEmpty()) return Outcome.Fail("The photo is empty.")
        if (members.find(memberId) == null) return Outcome.Fail("Member not found.")
        return db.transaction<Outcome<Unit>> { c ->
            c.prepareStatement("UPDATE members SET photo = ? WHERE id = ?").use { ps ->
                ps.setBytes(1, bytes)
                ps.setLong(2, memberId)
                ps.executeUpdate()
            }
            Outcome.Ok(Unit)
        }
    }

    // ---- reads for the UI ----

    fun search(query: String): List<MemberRow> {
        val planNames = plansRepo.lastPlanNames()
        return members.search(query).map { r ->
            MemberRow(
                id = r.id,
                number = r.number,
                name = r.name,
                contact = r.contact,
                membershipLabel = planNames[r.id] ?: r.lastType?.let { kindOf(it).label } ?: "\u2014",
                endLabel = r.lastEnd?.let { label(it) } ?: "\u2014",
                status = statusOf(r.lastEnd),
                isStudent = members.isVerifiedStudent(r.id),
                expiringSoon = r.lastEnd?.let { iso ->
                    val e = LocalDate.parse(iso)
                    !e.isBefore(today()) && !e.isAfter(today().plusDays(7))
                } ?: false,
            )
        }
    }

    fun getMember(id: Long): MemberDetail? {
        val m = members.find(id) ?: return null
        val history = members.history(id)
        val planNames = plansRepo.planNamesByReference(id)
        val student = members.student(id)
        val latest = history.firstOrNull()
        val studentLabel = student?.let {
            val parts = listOfNotNull(
                it.school?.takeIf { s -> s.isNotBlank() },
                it.idNumber?.takeIf { n -> n.isNotBlank() }?.let { n -> "ID $n" },
            )
            (listOf(eligibilityRepo.typeNameOf(id) ?: "Verified") + parts).joinToString(" \u00B7 ")
        }
        return MemberDetail(
            id = m.id,
            number = m.number,
            name = m.name,
            contact = m.contact,
            email = m.email ?: "",
            address = m.address ?: "",
            emergencyContact = m.emergencyContact ?: "",
            registeredLabel = label(m.registeredAt.substringBefore('T')),
            studentLabel = studentLabel,
            status = statusOf(latest?.endDate),
            lastKind = latest?.let { kindOf(it.type) },
            history = history.map { h ->
                MembershipEntry(
                    kindLabel = h.reference?.let { planNames[it] } ?: kindOf(h.type).label,
                    periodLabel = periodLabel(LocalDate.parse(h.startDate), LocalDate.parse(h.endDate)),
                    totalCents = h.totalCents,
                    reference = h.reference,
                )
            },
        )
    }

    fun stats(): MembershipStats {
        val now = today()
        val soon = now.plusDays(7)
        var active = 0
        var expiring = 0
        var expired = 0
        val ends = renewalRepo.latestEndDates()
        for (iso in ends) {
            if (iso == null) continue
            val end = LocalDate.parse(iso)
            when {
                end.isBefore(now) -> expired++
                !end.isAfter(soon) -> {
                    active++
                    expiring++
                }
                else -> active++
            }
        }
        return MembershipStats(total = ends.size, active = active, expiringSoon = expiring, expired = expired)
    }

    fun renewals(query: String, kind: MembershipKind?): List<RenewalRow> =
        renewalRepo.renewals(query, kind?.name?.uppercase()).map { r ->
            RenewalRow(
                reference = r.reference,
                memberName = r.memberName,
                memberNumber = r.memberNumber,
                previousPlan = r.previousPlanName ?: r.previousType?.let { kindOf(it).label } ?: "\u2014",
                newPlan = r.newPlanName ?: kindOf(r.newType).label,
                previousExpiry = r.previousEnd?.let { label(it) } ?: "\u2014",
                newExpiry = label(r.newEnd),
                renewedLabel = label(r.createdAt.substringBefore('T')),
            )
        }
    private companion object {
        const val DISCOUNT_TYPE_KEY = "discount.student_type"
        const val DISCOUNT_VALUE_KEY = "discount.student_value"
        const val DISCOUNT_ENABLED_KEY = "discount.student_enabled"
        const val QR_KEY = "payment.qr_png_base64"
        const val PLANS_MIGRATED_KEY = "plans.migrated"
    }
}

