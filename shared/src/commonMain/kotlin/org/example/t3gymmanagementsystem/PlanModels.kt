package org.example.t3gymmanagementsystem

data class Plan(val id: Long, val name: String, val kind: MembershipKind, val priceCents: Long, val inUse: Boolean, val eligibilityTypeId: Long? = null, val eligibilityName: String? = null, val durationCount: Int = 1)

/** id = null creates a new plan; otherwise the plan with that id is edited. */
data class PlanInput(val id: Long?, val name: String, val kind: MembershipKind, val priceCents: Long, val eligibilityTypeId: Long? = null, val durationCount: Int = 1)

/** A kind of discount eligibility staff can verify (Student, PWD, ...). inUse = members or plans reference it. */
data class EligibilityType(val id: Long, val name: String, val inUse: Boolean)
