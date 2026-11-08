package org.example.t3gymmanagementsystem

data class RecentCheckIn(val name: String, val number: String, val timeLabel: String, val source: String)

/** target says where a click goes: MEMBERS, INVENTORY or SETTINGS. */
data class AttentionItem(val target: String, val title: String, val detail: String)

data class DashboardData(
    val totalMembers: Int,
    val activeMembers: Int,
    val expiringSoon: Int,
    val expiredMembers: Int,
    val noMembership: Int,
    val newThisMonth: Int,
    val checkInsToday: Int,
    val checkInsLastWeek: Int,
    val lastWeekDay: String,
    val revenueTodayCents: Long,
    val revenueYesterdayCents: Long,
    val lowStock: Int,
    val outOfStock: Int,
    val recent: List<RecentCheckIn>,
    val attention: List<AttentionItem>,
)