package org.example.t3gymmanagementsystem

data class Plan(val id: Long, val name: String, val kind: MembershipKind, val priceCents: Long, val inUse: Boolean)

/** id = null creates a new plan; otherwise the plan with that id is edited. */
data class PlanInput(val id: Long?, val name: String, val kind: MembershipKind, val priceCents: Long)