package org.example.t3gymmanagementsystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.example.t3gymmanagementsystem.data.database.DatabaseLocation
import org.example.t3gymmanagementsystem.data.database.DatabaseManager
import org.example.t3gymmanagementsystem.data.repositories.AdminRepository
import org.example.t3gymmanagementsystem.data.repositories.MemberRepository
import org.example.t3gymmanagementsystem.data.repositories.SettingKeys
import org.example.t3gymmanagementsystem.data.repositories.SettingsRepository
import org.example.t3gymmanagementsystem.services.AuthService
import org.example.t3gymmanagementsystem.services.MembershipService
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Wires the data layer to the UI-facing AppServices interface. All blocking work runs on Dispatchers.IO. */
class JvmAppServices(dbPath: Path = DatabaseLocation.resolve()) : AppServices {

    private val database = DatabaseManager(dbPath).also { it.migrate() }
    private val settings = SettingsRepository(database)
    private val auth = AuthService(AdminRepository(database))
    private val memberships = MembershipService(database, settings, MemberRepository(database)) { LocalDate.now() }

    private val backupDir: Path get() = DatabaseLocation.backupDir(database.dbPath)
    private val labelFormat = DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a", Locale.ENGLISH)

    private fun zone(): ZoneId =
        runCatching { ZoneId.of(settings.get(SettingKeys.TIMEZONE) ?: "Asia/Manila") }.getOrDefault(ZoneId.of("Asia/Manila"))

    private suspend fun <T> guarded(block: () -> Outcome<T>): Outcome<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            Outcome.Fail("Database error: ${e.message}")
        }
    }

    override suspend fun isAdminConfigured(): Boolean = withContext(Dispatchers.IO) { auth.isConfigured() }

    override suspend fun createAdmin(username: String, password: String): AuthResult =
        withContext(Dispatchers.IO) { auth.createAdmin(username, password) }

    override suspend fun login(username: String, password: String): AuthResult =
        withContext(Dispatchers.IO) { auth.login(username, password) }

    override suspend fun getGymInfo(): GymInfo = withContext(Dispatchers.IO) {
        GymInfo(
            name = settings.getOrDefault(SettingKeys.GYM_NAME, ""),
            contact = settings.getOrDefault(SettingKeys.GYM_CONTACT, ""),
            address = settings.getOrDefault(SettingKeys.GYM_ADDRESS, ""),
        )
    }

    override suspend fun saveGymInfo(info: GymInfo) = withContext(Dispatchers.IO) {
        settings.setAll(
            mapOf(
                SettingKeys.GYM_NAME to info.name,
                SettingKeys.GYM_CONTACT to info.contact,
                SettingKeys.GYM_ADDRESS to info.address,
            )
        )
    }

    override suspend fun databaseStatus(): DatabaseStatus = withContext(Dispatchers.IO) {
        DatabaseStatus(
            path = database.dbPath.toAbsolutePath().toString(),
            backupDir = backupDir.toAbsolutePath().toString(),
            schemaVersion = database.schemaVersion(),
            sizeBytes = runCatching { Files.size(database.dbPath) }.getOrDefault(0L),
        )
    }

    override suspend fun listBackups(): List<BackupInfo> = withContext(Dispatchers.IO) {
        if (!Files.isDirectory(backupDir)) return@withContext emptyList()
        Files.list(backupDir).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".db") }
                .map { p -> Triple(p, Files.getLastModifiedTime(p).toMillis(), Files.size(p)) }
                .toList()
        }.sortedByDescending { it.second }.map { (p, millis, size) ->
            BackupInfo(p.fileName.toString(), size, Instant.ofEpochMilli(millis).atZone(zone()).format(labelFormat))
        }
    }

    override suspend fun createBackup(): BackupInfo = withContext(Dispatchers.IO) {
        val target = backupDir.resolve(database.defaultBackupName(LocalDateTime.now(zone())))
        database.backupTo(target)
        BackupInfo(target.fileName.toString(), Files.size(target), ZonedDateTime.now(zone()).format(labelFormat))
    }

    override suspend fun getPricing(): Pricing = withContext(Dispatchers.IO) { memberships.getPricing() }

    override suspend fun savePricing(pricing: Pricing): Outcome<Unit> = guarded { memberships.savePricing(pricing) }

    override suspend fun quote(kind: MembershipKind, isStudent: Boolean): Outcome<Quote> =
        guarded { memberships.quote(kind, isStudent) }

    override suspend fun quoteRenewal(memberId: Long, kind: MembershipKind): Outcome<Quote> =
        guarded { memberships.quoteRenewal(memberId, kind) }

    override suspend fun searchMembers(query: String): List<MemberRow> =
        withContext(Dispatchers.IO) { memberships.search(query) }

    override suspend fun getMember(id: Long): MemberDetail? = withContext(Dispatchers.IO) { memberships.getMember(id) }

    override suspend fun registerMember(input: NewMember): Outcome<Registration> =
        guarded { memberships.register(input) }

    override suspend fun renewMembership(memberId: Long, kind: MembershipKind): Outcome<Registration> =
        guarded { memberships.renew(memberId, kind) }

    override suspend fun getDiscount(): StudentDiscount? = withContext(Dispatchers.IO) { memberships.getDiscount() }

    override suspend fun saveDiscount(discount: StudentDiscount): Outcome<Unit> =
        guarded { memberships.saveDiscount(discount) }

    override suspend fun membershipStats(): MembershipStats = withContext(Dispatchers.IO) { memberships.stats() }

    override suspend fun renewalHistory(query: String, kind: MembershipKind?): List<RenewalRow> =
        withContext(Dispatchers.IO) { memberships.renewals(query, kind) }

    override suspend fun renewMembershipPaid(memberId: Long, kind: MembershipKind, payment: PaymentInfo): Outcome<Registration> =
        guarded { memberships.renew(memberId, kind, payment) }

    override suspend fun memberPhoto(memberId: Long): androidx.compose.ui.graphics.ImageBitmap? =
        withContext(Dispatchers.IO) {
            memberships.getPhoto(memberId)?.let { org.example.t3gymmanagementsystem.services.PhotoSupport.toBitmap(it) }
        }

    override suspend fun choosePhoto(): PickedPhoto? =
        withContext(Dispatchers.IO) { org.example.t3gymmanagementsystem.services.PhotoSupport.choose() }

    override suspend fun saveMemberPhoto(memberId: Long, bytes: ByteArray): Outcome<Unit> =
        guarded { memberships.savePhoto(memberId, bytes) }

    override suspend fun listTransactions(query: String): List<TransactionRow> =
        withContext(Dispatchers.IO) { memberships.receipts.transactions(query) }

    override suspend fun getReceipt(transactionId: Long): ReceiptData? {
        val gym = getGymInfo()
        return withContext(Dispatchers.IO) { memberships.receipts.receipt(transactionId, gym) }
    }

    override suspend fun saveReceiptPdf(transactionId: Long): Outcome<String?> {
        val gym = getGymInfo()
        return guarded {
            val receipt = memberships.receipts.receipt(transactionId, gym)
                ?: return@guarded Outcome.Fail("Receipt not found.")
            val bytes = org.example.t3gymmanagementsystem.services.ReceiptPdf.build(
                org.example.t3gymmanagementsystem.util.ReceiptText.render(receipt)
            )
            Outcome.Ok(org.example.t3gymmanagementsystem.services.ReceiptFiles.savePdf(receipt.reference, bytes))
        }
    }

    override suspend fun printReceipt(transactionId: Long): Outcome<Boolean> {
        val gym = getGymInfo()
        return guarded {
            val receipt = memberships.receipts.receipt(transactionId, gym)
                ?: return@guarded Outcome.Fail("Receipt not found.")
            Outcome.Ok(
                org.example.t3gymmanagementsystem.services.ReceiptPrintable.print(
                    org.example.t3gymmanagementsystem.util.ReceiptText.render(receipt)
                )
            )
        }
    }

    override suspend fun qrImage(): androidx.compose.ui.graphics.ImageBitmap? =
        withContext(Dispatchers.IO) {
            memberships.getQr()?.let { org.example.t3gymmanagementsystem.services.PhotoSupport.toBitmap(it) }
        }

    override suspend fun chooseQrImage(): Outcome<Boolean> = guarded {
        val picked = org.example.t3gymmanagementsystem.services.PhotoSupport.choose("Select the gym's payment QR image", 600)
        if (picked == null) {
            Outcome.Ok(false)
        } else {
            memberships.saveQr(picked.bytes)
            Outcome.Ok(true)
        }
    }

    override suspend fun removeQrImage(): Outcome<Unit> = guarded {
        memberships.removeQr()
        Outcome.Ok(Unit)
    }

    override suspend fun listPlans(): List<Plan> = withContext(Dispatchers.IO) { memberships.listPlans() }

    override suspend fun savePlan(input: PlanInput): Outcome<Long> = guarded { memberships.savePlan(input) }

    override suspend fun deletePlan(id: Long): Outcome<String> = guarded { memberships.deletePlan(id) }

    override suspend fun quotePlan(planId: Long, isStudent: Boolean): Outcome<Quote> =
        guarded { memberships.quotePlan(planId, isStudent) }

    override suspend fun quoteRenewalPlan(memberId: Long, planId: Long): Outcome<Quote> =
        guarded { memberships.quoteRenewalPlan(memberId, planId) }

    override suspend fun renewMembershipPlan(memberId: Long, planId: Long, payment: PaymentInfo): Outcome<Registration> =
        guarded { memberships.renewPlan(memberId, planId, payment) }

    override fun cardEvents(): kotlinx.coroutines.flow.Flow<ReaderEvent> =
        org.example.t3gymmanagementsystem.services.CardReader.events()

    override suspend fun checkInByCard(uid: String): CheckInResult =
        withContext(Dispatchers.IO) { memberships.attendance.checkInByCard(uid) }

    override suspend fun checkInMember(memberId: Long): CheckInResult =
        withContext(Dispatchers.IO) { memberships.attendance.checkInMember(memberId) }

    override suspend fun todayAttendance(): List<AttendanceRow> =
        withContext(Dispatchers.IO) { memberships.attendance.todayAttendance() }

    override suspend fun getMemberCard(memberId: Long): CardInfo? =
        withContext(Dispatchers.IO) { memberships.attendance.getCard(memberId) }

    override suspend fun assignCard(memberId: Long, uid: String): Outcome<Unit> =
        guarded { memberships.attendance.assignCard(memberId, uid) }

    override suspend fun removeCard(memberId: Long): Outcome<Unit> =
        guarded { memberships.attendance.removeCard(memberId) }

    override suspend fun recordWalkIn(visitor: String, payment: PaymentInfo): Outcome<WalkInResult> =
        guarded { memberships.attendance.recordWalkIn(visitor, payment) }

    override suspend fun todayWalkIns(): List<WalkInRow> =
        withContext(Dispatchers.IO) { memberships.attendance.todayWalkIns() }

    override suspend fun todayDenied(): List<DeniedRow> =
        withContext(Dispatchers.IO) { memberships.attendance.todayDenied() }

    override fun refreshReader() {
        org.example.t3gymmanagementsystem.services.CardReader.requestRefresh()
    }

    override suspend fun paymentSummary(): PaymentSummary =
        withContext(Dispatchers.IO) { memberships.receipts.summary() }

    override suspend fun inventorySummary(): InventorySummary =
        withContext(Dispatchers.IO) { memberships.inventory.summary() }

    override suspend fun listProducts(query: String, category: String?, filter: StockFilter): List<ProductRow> =
        withContext(Dispatchers.IO) { memberships.inventory.products(query, category, filter) }

    override suspend fun productCategories(): List<String> =
        withContext(Dispatchers.IO) { memberships.inventory.categories() }

    override suspend fun saveProduct(input: ProductInput): Outcome<Long> =
        guarded { memberships.inventory.saveProduct(input) }

    override suspend fun setProductActive(productId: Long, active: Boolean): Outcome<Unit> =
        guarded { memberships.inventory.setActive(productId, active) }

    override suspend fun restockProduct(productId: Long, quantity: Int, note: String): Outcome<Unit> =
        guarded { memberships.inventory.restock(productId, quantity, note) }

    override suspend fun adjustStock(productId: Long, counted: Int, reason: String): Outcome<Unit> =
        guarded { memberships.inventory.adjust(productId, counted, reason) }

    override suspend fun stockMovements(): List<MovementRow> =
        withContext(Dispatchers.IO) { memberships.inventory.movements() }

    override suspend fun sellProducts(memberId: Long?, lines: List<CartLine>, payment: PaymentInfo): Outcome<ProductSaleResult> =
        guarded { memberships.inventory.sell(memberId, lines, payment) }

    override suspend fun recentProductSales(): List<ProductSaleRow> =
        withContext(Dispatchers.IO) { memberships.inventory.recentSales() }

    override suspend fun memberByCard(uid: String): MemberRow? = withContext(Dispatchers.IO) {
        val number = memberships.inventory.memberNumberByCard(uid)
        if (number == null) null else searchMembers(number).firstOrNull { it.number == number }
    }

    override suspend fun reportRange(preset: ReportPreset): ReportRange =
        withContext(Dispatchers.IO) { memberships.reports.rangeFor(preset) }

    override suspend fun buildReport(from: String, to: String): Outcome<ReportBundle> =
        guarded { memberships.reports.build(from, to) }

    override suspend fun exportReport(document: ReportDocument, format: ExportFormat): Outcome<String?> =
        guarded { org.example.t3gymmanagementsystem.services.ReportExporter.save(document, format) }

    override suspend fun dashboard(): DashboardData =
        withContext(Dispatchers.IO) { memberships.dashboard.load() }

    override suspend fun lowStockCount(): Int =
        withContext(Dispatchers.IO) { memberships.inventory.lowStockCount() }

    override fun clock(): ClockText {
        val now = ZonedDateTime.now(zone())
        return ClockText(
            dateLine = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)),
            timeLine = now.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)),
        )
    }
}
