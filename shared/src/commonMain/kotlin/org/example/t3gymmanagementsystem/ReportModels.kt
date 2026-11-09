package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.util.Money
import kotlin.math.abs
import kotlin.math.roundToLong

enum class ReportPreset(val label: String) {
    Today("Today"),
    Last7("Last 7 days"),
    Last30("Last 30 days"),
    Last90("Last 90 days"),
    ThisMonth("This month"),
    ThisYear("This year"),
}

enum class ExportFormat(val label: String) { Csv("CSV"), Pdf("PDF") }

data class ReportRange(val from: String, val to: String)

data class PeriodFigures(
    val membershipCents: Long,
    val productCents: Long,
    val walkInCents: Long,
    val discountCents: Long,
    val transactions: Int,
    val newMembers: Int,
    val renewals: Int,
    val checkIns: Int,
    val walkIns: Int,
    val unitsSold: Int,
) {
    val revenueCents: Long get() = membershipCents + productCents + walkInCents
}

data class DayPoint(
    val date: String,
    val membershipCents: Long,
    val productCents: Long,
    val walkInCents: Long,
    val checkIns: Int,
    val walkIns: Int,
) {
    val totalCents: Long get() = membershipCents + productCents + walkInCents
}

data class PlanLine(val plan: String, val newCount: Int, val renewals: Int, val revenueCents: Long)
data class MethodLine(val method: String, val count: Int, val cents: Long)
data class TypeLine(val label: String, val count: Int, val cents: Long)
data class TopProduct(val name: String, val sku: String, val units: Int, val cents: Long)
data class StockAlert(val name: String, val sku: String, val stock: Int, val lowLevel: Int)
data class StockCount(val name: String, val sku: String, val category: String, val stock: Int, val lowLevel: Int)
data class StatusCounts(val total: Int, val active: Int, val expired: Int, val none: Int)

data class ReportBundle(
    val from: String,
    val to: String,
    val previousFrom: String,
    val previousTo: String,
    val days: Int,
    val current: PeriodFigures,
    val previous: PeriodFigures,
    val daily: List<DayPoint>,
    val plans: List<PlanLine>,
    val methods: List<MethodLine>,
    val types: List<TypeLine>,
    val topProducts: List<TopProduct>,
    val members: StatusCounts,
    val distinctMembers: Int,
    val denied: Int,
    val peakHour: String?,
    val stockAlerts: List<StockAlert>,
    val stockCounts: List<StockCount> = emptyList(),
)

data class ReportSection(val title: String, val headers: List<String>, val rows: List<List<String>>)

data class ReportDocument(val title: String, val subtitle: String, val fileStem: String, val sections: List<ReportSection>)

val REPORT_TABS = listOf("Overview", "Membership", "Attendance", "Sales", "Inventory")

data class PerfRow(val metric: String, val current: String, val previous: String, val change: String, val trend: String)

/** "+12.5%", "-3.0%", "New" (nothing before) or a dash (nothing in either period). */
fun changeText(current: Long, previous: Long): String {
    if (previous == 0L) return if (current == 0L) "\u2014" else "New"
    val tenths = ((current - previous).toDouble() * 1000.0 / previous).roundToLong()
    val sign = if (tenths > 0) "+" else if (tenths < 0) "-" else ""
    val a = abs(tenths)
    return "$sign${a / 10}.${a % 10}%"
}

fun trendText(current: Long, previous: Long): String = when {
    current > previous -> "Growing"
    current < previous -> "Declining"
    else -> "Steady"
}

fun changeHint(current: Long, previous: Long): String =
    if (previous == 0L) "No data in the previous period" else "${changeText(current, previous)} vs previous period"

/** One decimal place, for example 72.8. */
fun perDay(count: Int, days: Int): String {
    val t = count * 10L / days.coerceAtLeast(1)
    return "${t / 10}.${t % 10}"
}

fun ReportBundle.performanceRows(): List<PerfRow> {
    fun money(name: String, c: Long, p: Long) = PerfRow(name, Money.format(c), Money.format(p), changeText(c, p), trendText(c, p))
    fun count(name: String, c: Int, p: Int) = PerfRow(name, c.toString(), p.toString(), changeText(c.toLong(), p.toLong()), trendText(c.toLong(), p.toLong()))
    val d = days.coerceAtLeast(1)
    return listOf(
        money("Membership revenue", current.membershipCents, previous.membershipCents),
        money("Product revenue", current.productCents, previous.productCents),
        money("Walk-in revenue", current.walkInCents, previous.walkInCents),
        money("Total revenue", current.revenueCents, previous.revenueCents),
        count("New members", current.newMembers, previous.newMembers),
        count("Renewals", current.renewals, previous.renewals),
        count("Check-ins", current.checkIns, previous.checkIns),
        PerfRow(
            "Average daily attendance", perDay(current.checkIns, d), perDay(previous.checkIns, d),
            changeText(current.checkIns * 10L / d, previous.checkIns * 10L / d),
            trendText(current.checkIns * 10L / d, previous.checkIns * 10L / d),
        ),
        count("Product units sold", current.unitsSold, previous.unitsSold),
    )
}

/** The tables of one report tab as plain text, for CSV and PDF export. */
fun ReportBundle.document(tab: Int): ReportDocument {
    val name = REPORT_TABS[tab]
    val statusRows = listOf(
        listOf("Active", members.active.toString()),
        listOf("Expired", members.expired.toString()),
        listOf("No membership", members.none.toString()),
        listOf("All members", members.total.toString()),
    )
    val dailyMoney = ReportSection(
        "Daily revenue", listOf("Date", "Membership", "Products", "Walk-ins", "Total"),
        daily.map { listOf(it.date, Money.format(it.membershipCents), Money.format(it.productCents), Money.format(it.walkInCents), Money.format(it.totalCents)) },
    )
    val sections = when (tab) {
        0 -> listOf(
            ReportSection(
                "Performance summary", listOf("Metric", "Current period", "Previous period", "Change", "Trend"),
                performanceRows().map { listOf(it.metric, it.current, it.previous, it.change, it.trend) },
            ),
            dailyMoney,
            ReportSection("Members by status (today)", listOf("Status", "Members"), statusRows),
        )
        1 -> listOf(
            ReportSection(
                "Summary", listOf("Measure", "Value"),
                listOf(
                    listOf("New members", current.newMembers.toString()),
                    listOf("Renewals", current.renewals.toString()),
                    listOf("Membership revenue", Money.format(current.membershipCents)),
                    listOf("Discounts given", Money.format(current.discountCents)),
                ),
            ),
            ReportSection(
                "Plan breakdown", listOf("Plan", "New", "Renewals", "Revenue"),
                plans.map { listOf(it.plan, it.newCount.toString(), it.renewals.toString(), Money.format(it.revenueCents)) },
            ),
            ReportSection("Members by status (today)", listOf("Status", "Members"), statusRows),
        )
        2 -> listOf(
            ReportSection(
                "Summary", listOf("Measure", "Value"),
                listOf(
                    listOf("Check-ins", current.checkIns.toString()),
                    listOf("Different members", distinctMembers.toString()),
                    listOf("Walk-ins", current.walkIns.toString()),
                    listOf("Average check-ins per day", perDay(current.checkIns, days)),
                    listOf("Peak hour", peakHour ?: "\u2014"),
                    listOf("Denied card scans", denied.toString()),
                ),
            ),
            ReportSection("Daily attendance", listOf("Date", "Check-ins", "Walk-ins"), daily.map { listOf(it.date, it.checkIns.toString(), it.walkIns.toString()) }),
        )
        3 -> listOf(
            ReportSection(
                "Summary", listOf("Measure", "Value"),
                listOf(
                    listOf("Total revenue", Money.format(current.revenueCents)),
                    listOf("Transactions", current.transactions.toString()),
                    listOf("Average sale", Money.format(if (current.transactions == 0) 0L else current.revenueCents / current.transactions)),
                    listOf("Discounts given", Money.format(current.discountCents)),
                ),
            ),
            ReportSection("Revenue by payment method", listOf("Method", "Payments", "Amount"), methods.map { listOf(it.method, it.count.toString(), Money.format(it.cents)) }),
            ReportSection("Revenue by type", listOf("Type", "Transactions", "Amount"), types.map { listOf(it.label, it.count.toString(), Money.format(it.cents)) }),
            dailyMoney,
        )
        else -> listOf(
            ReportSection(
                "Summary", listOf("Measure", "Value"),
                listOf(
                    listOf("Product units sold", current.unitsSold.toString()),
                    listOf("Product revenue", Money.format(current.productCents)),
                    listOf("Products low or out of stock now", stockAlerts.size.toString()),
                ),
            ),
            ReportSection("Top selling products", listOf("Product", "SKU", "Units", "Revenue"), topProducts.map { listOf(it.name, it.sku, it.units.toString(), Money.format(it.cents)) }),
            ReportSection("Stock count (now)", listOf("Product", "SKU", "Category", "In stock", "Low-stock level", "Status"), stockCounts.map { listOf(it.name, it.sku, it.category, it.stock.toString(), it.lowLevel.toString(), if (it.stock == 0) "Out of stock" else if (it.stock <= it.lowLevel) "Low" else "OK") }),
            ReportSection("Needs restocking (now)", listOf("Product", "SKU", "Stock", "Low-stock level"), stockAlerts.map { listOf(it.name, it.sku, it.stock.toString(), it.lowLevel.toString()) }),
        )
    }
    return ReportDocument(
        "T3 Fitness & Martial Arts Gym - $name report",
        "Period: $from to $to (compared with $previousFrom to $previousTo)",
        "T3-Report-$name-$from-to-$to",
        sections,
    )
}