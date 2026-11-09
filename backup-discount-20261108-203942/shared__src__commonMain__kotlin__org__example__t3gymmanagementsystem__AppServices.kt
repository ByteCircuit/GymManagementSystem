package org.example.t3gymmanagementsystem


import kotlinx.coroutines.flow.Flow/** What the UI is allowed to ask of the data layer. Implemented in jvmMain by JvmAppServices. */
interface AppServices {
    suspend fun isAdminConfigured(): Boolean
    suspend fun createAdmin(username: String, password: String): AuthResult
    suspend fun login(username: String, password: String): AuthResult

    suspend fun getGymInfo(): GymInfo
    suspend fun saveGymInfo(info: GymInfo)

    suspend fun databaseStatus(): DatabaseStatus
    suspend fun listBackups(): List<BackupInfo>
    suspend fun createBackup(): BackupInfo

    suspend fun getPricing(): Pricing
    suspend fun savePricing(pricing: Pricing): Outcome<Unit>
    suspend fun quote(kind: MembershipKind, isStudent: Boolean): Outcome<Quote>
    suspend fun quoteRenewal(memberId: Long, kind: MembershipKind): Outcome<Quote>

    suspend fun getDiscount(): StudentDiscount?
    suspend fun saveDiscount(discount: StudentDiscount): Outcome<Unit>
    suspend fun membershipStats(): MembershipStats
    suspend fun renewalHistory(query: String, kind: MembershipKind?): List<RenewalRow>

    suspend fun renewMembershipPaid(memberId: Long, kind: MembershipKind, payment: PaymentInfo): Outcome<Registration>
    suspend fun memberPhoto(memberId: Long): androidx.compose.ui.graphics.ImageBitmap?
    suspend fun choosePhoto(): PickedPhoto?
    suspend fun saveMemberPhoto(memberId: Long, bytes: ByteArray): Outcome<Unit>

    suspend fun listTransactions(query: String): List<TransactionRow>
    suspend fun getReceipt(transactionId: Long): ReceiptData?
    /** Ok(path) when saved, Ok(null) when the user cancelled the save dialog. */
    suspend fun saveReceiptPdf(transactionId: Long): Outcome<String?>
    /** Ok(true) when sent to the printer, Ok(false) when the user cancelled. */
    suspend fun printReceipt(transactionId: Long): Outcome<Boolean>
    suspend fun qrImage(): androidx.compose.ui.graphics.ImageBitmap?
    /** Ok(true) when a new QR image was saved, Ok(false) when the user cancelled. */
    suspend fun chooseQrImage(): Outcome<Boolean>
    suspend fun removeQrImage(): Outcome<Unit>

    suspend fun listPlans(): List<Plan>
    suspend fun savePlan(input: PlanInput): Outcome<Long>
    /** Ok(message) says whether the plan was deleted or archived. */
    suspend fun deletePlan(id: Long): Outcome<String>
    suspend fun quotePlan(planId: Long, isStudent: Boolean): Outcome<Quote>
    suspend fun quoteRenewalPlan(memberId: Long, planId: Long): Outcome<Quote>
    suspend fun renewMembershipPlan(memberId: Long, planId: Long, payment: PaymentInfo): Outcome<Registration>

    /** Card taps refused today (expired, no membership, card not assigned). */
    suspend fun todayDenied(): List<DeniedRow>

    /** Ask the card reader to drop its connection and look for the reader again. */
    fun refreshReader()

    suspend fun paymentSummary(): PaymentSummary

    suspend fun inventorySummary(): InventorySummary
    suspend fun listProducts(query: String, category: String?, filter: StockFilter): List<ProductRow>
    suspend fun productCategories(): List<String>
    suspend fun saveProduct(input: ProductInput): Outcome<Long>
    suspend fun setProductActive(productId: Long, active: Boolean): Outcome<Unit>
    suspend fun restockProduct(productId: Long, quantity: Int, note: String): Outcome<Unit>
    suspend fun adjustStock(productId: Long, counted: Int, reason: String): Outcome<Unit>
    suspend fun stockMovements(): List<MovementRow>
    suspend fun sellProducts(memberId: Long?, lines: List<CartLine>, payment: PaymentInfo): Outcome<ProductSaleResult>
    suspend fun recentProductSales(): List<ProductSaleRow>
    suspend fun lowStockCount(): Int
    suspend fun dashboard(): DashboardData
    suspend fun memberByCard(uid: String): MemberRow?
    suspend fun reportRange(preset: ReportPreset): ReportRange
    suspend fun buildReport(from: String, to: String): Outcome<ReportBundle>
    suspend fun exportReport(document: ReportDocument, format: ExportFormat): Outcome<String?>

    suspend fun searchMembers(query: String): List<MemberRow>
    suspend fun getMember(id: Long): MemberDetail?
    suspend fun registerMember(input: NewMember): Outcome<Registration>
    suspend fun renewMembership(memberId: Long, kind: MembershipKind): Outcome<Registration>

    fun cardEvents(): Flow<ReaderEvent>
    suspend fun checkInByCard(uid: String): CheckInResult
    suspend fun todayAttendance(): List<AttendanceRow>
    suspend fun todayWalkIns(): List<WalkInRow>
    suspend fun checkInMember(memberId: Long): CheckInResult
    suspend fun recordWalkIn(visitor: String, payment: PaymentInfo): Outcome<WalkInResult>
    suspend fun getMemberCard(memberId: Long): CardInfo?
    suspend fun assignCard(memberId: Long, uid: String): Outcome<Unit>
    suspend fun removeCard(memberId: Long): Outcome<Unit>

    fun clock(): ClockText
}

data class GymInfo(val name: String, val contact: String, val address: String)
data class DatabaseStatus(val path: String, val backupDir: String, val schemaVersion: Int, val sizeBytes: Long)
data class BackupInfo(val fileName: String, val sizeBytes: Long, val createdLabel: String)
data class ClockText(val dateLine: String, val timeLine: String)

sealed interface AuthResult {
    data class Success(val username: String) : AuthResult
    data class Failure(val message: String) : AuthResult
}
