package org.example.t3gymmanagementsystem

import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingKeys
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import org.example.t3gymmanagementsystem.services.MembershipService
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanServiceTest {
    private lateinit var dir: Path
    private lateinit var settings: SettingsRepository
    private lateinit var service: MembershipService
    private var today: LocalDate = LocalDate.of(2026, 1, 15)

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("t3gym-plans")
        val db = DatabaseManager(dir.resolve("gym.db")).also { it.migrate() }
        settings = SettingsRepository(db)
        service = MembershipService(db, settings, MemberRepository(db)) { today }
    }

    @AfterTest
    fun tearDown() {
        dir.toFile().deleteRecursively()
    }

    private fun <T> Outcome<T>.ok(): T {
        assertIs<Outcome.Ok<T>>(this)
        return value
    }

    private fun savePlan(name: String, kind: MembershipKind = MembershipKind.Monthly, price: Long = 100_000, id: Long? = null) =
        service.savePlan(PlanInput(id, name, kind, price))

    private fun member(planId: Long?, kind: MembershipKind = MembershipKind.Monthly) = NewMember(
        "Juan Dela Cruz", "0917", "", "", "", kind, isStudent = false, planId = planId,
    )

    @Test
    fun createEditAndValidatePlans() {
        assertEquals(emptyList(), service.listPlans())
        val id = savePlan("Regular").ok()
        savePlan("Student Monthly", price = 80_000).ok()
        assertEquals(listOf("Regular", "Student Monthly"), service.listPlans().map { it.name })

        assertIs<Outcome.Fail>(savePlan("regular"))
        assertIs<Outcome.Fail>(savePlan("   "))
        assertIs<Outcome.Fail>(savePlan("Free", price = 0))

        savePlan("Regular Plus", price = 120_000, id = id).ok()
        val edited = service.listPlans().first { it.id == id }
        assertEquals("Regular Plus", edited.name)
        assertEquals(120_000L, edited.priceCents)
        assertIs<Outcome.Fail>(savePlan("Student Monthly", id = id))
    }

    @Test
    fun registerAndRenewUseTheChosenPlan() {
        val monthly = savePlan("Premium", price = 200_000).ok()
        val daily = savePlan("Day Pass", MembershipKind.Daily, 15_000).ok()

        val reg = service.register(member(monthly)).ok()
        assertEquals(200_000L, reg.totalCents)
        assertEquals("Premium", service.getMember(reg.memberId)!!.history[0].kindLabel)
        assertEquals("Premium", service.search("")[0].membershipLabel)
        assertEquals(15_000L, service.quotePlan(daily, false).ok().totalCents)

        today = LocalDate.of(2026, 2, 1)
        assertEquals(15_000L, service.renewPlan(reg.memberId, daily).ok().totalCents)
        val renewal = service.renewals("", null).single()
        assertEquals("Premium", renewal.previousPlan)
        assertEquals("Day Pass", renewal.newPlan)
    }

    @Test
    fun studentDiscountAppliesToMonthlyPlansOnly() {
        val monthly = savePlan("Premium", price = 200_000).ok()
        val daily = savePlan("Day Pass", MembershipKind.Daily, 15_000).ok()
        service.saveDiscount(StudentDiscount(DiscountType.Percentage, 10, true)).ok()
        assertEquals(180_000L, service.quotePlan(monthly, true).ok().totalCents)
        assertEquals(15_000L, service.quotePlan(daily, true).ok().totalCents)
    }

    @Test
    fun deleteRemovesUnusedPlansAndArchivesUsedOnes() {
        val unused = savePlan("Unused").ok()
        val used = savePlan("Premium", price = 200_000).ok()
        val reg = service.register(member(used)).ok()

        assertTrue(service.deletePlan(unused).ok().contains("deleted"))
        assertTrue(service.deletePlan(used).ok().contains("archived"))
        assertEquals(emptyList(), service.listPlans())
        assertEquals("Premium", service.getMember(reg.memberId)!!.history[0].kindLabel)
        assertIs<Outcome.Fail>(service.register(member(used)))
        assertIs<Outcome.Fail>(service.deletePlan(used))
    }

    @Test
    fun durationIsLockedOnceAPlanHasBeenUsed() {
        val used = savePlan("Premium", price = 200_000).ok()
        service.register(member(used)).ok()
        assertIs<Outcome.Fail>(savePlan("Premium", MembershipKind.Daily, 200_000, used))
        val unused = savePlan("Spare").ok()
        savePlan("Spare", MembershipKind.Daily, 15_000, unused).ok()
    }

    @Test
    fun oldPricingMapsToTheDefaultPlans() {
        service.savePricing(Pricing(100_000, 15_000)).ok()
        assertEquals(listOf("Monthly" to 100_000L, "Daily" to 15_000L), service.listPlans().map { it.name to it.priceCents })
        service.savePricing(Pricing(110_000, null)).ok()
        assertEquals(2, service.listPlans().size)
        assertEquals(110_000L, service.getPricing().monthlyCents)
    }

    @Test
    fun pricesFromEarlierVersionsBecomePlansOnlyOnce() {
        settings.set(SettingKeys.PRICE_MONTHLY_CENTS, "100000")
        val plans = service.listPlans()
        assertEquals(listOf("Monthly"), plans.map { it.name })
        service.deletePlan(plans[0].id).ok()
        assertEquals(emptyList(), service.listPlans())
    }
}