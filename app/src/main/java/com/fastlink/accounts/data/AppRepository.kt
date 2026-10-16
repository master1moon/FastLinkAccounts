package com.fastlink.accounts.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.Calendar
import kotlin.math.max

class InsufficientStockException(val available: Int, val requested: Int) :
    Exception("الكمية المتوفرة في المخزون ($available) أقل من الكمية المطلوبة ($requested)")

class PackageLinkedToSalesException(val salesCount: Int) :
    Exception("هذه الباقة مرتبطة بـ $salesCount عملية بيع. هل تريد حذف الباقة مع سجلاتها؟")

class StoreHasDebtException(val debtAmount: Double) :
    Exception("هذا المحل عليه مديونية مستحقة بقيمة ${String.format("%.2f", debtAmount)}. هل أنت متأكد من الحذف؟")

class AppRepository(private val db: FastLinkDatabase) {

    private val dao = db.dao()

    // ==================== OBSERVABLE STREAMS ====================
    val packagesFlow: Flow<List<PackageEntity>> = dao.observePackages()
    val inventoryFlow: Flow<List<InventoryEntity>> = dao.observeInventory()
    val inventoryMovementsFlow: Flow<List<InventoryMovementEntity>> = dao.observeInventoryMovements()
    val storesFlow: Flow<List<StoreEntity>> = dao.observeStores()
    val salesFlow: Flow<List<SaleEntity>> = dao.observeAllSales()
    val paymentsFlow: Flow<List<PaymentEntity>> = dao.observeAllPayments()
    val expensesFlow: Flow<List<ExpenseEntity>> = dao.observeExpenses()
    val expenseTypesFlow: Flow<List<ExpenseTypeEntity>> = dao.observeExpenseTypes()
    val partnersFlow: Flow<List<PartnerEntity>> = dao.observePartners()
    val partnerTransactionsFlow: Flow<List<PartnerTransactionEntity>> = dao.observePartnerTransactions()
    val settingsFlow: Flow<List<SettingEntity>> = dao.observeSettings()
    val backupLogsFlow: Flow<List<BackupLogEntity>> = dao.observeBackupLogs()

    val packagesWithInventoryFlow: Flow<List<PackageWithInventory>> = combine(
        packagesFlow,
        inventoryFlow
    ) { packages, inventories ->
        val invMap = inventories.associateBy { it.packageId }
        packages.map { pkg ->
            val inv = invMap[pkg.id]
            PackageWithInventory(
                pkg = pkg,
                quantity = inv?.quantity ?: 0,
                updatedAt = inv?.updatedAt ?: pkg.createdAt
            )
        }
    }

    val storesWithSummaryFlow: Flow<List<StoreWithSummary>> = combine(
        storesFlow,
        salesFlow,
        paymentsFlow
    ) { stores, sales, payments ->
        val salesByStore = sales.groupBy { it.storeId }
        val paymentsByStore = payments.groupBy { it.storeId }
        stores.map { store ->
            val stSales = salesByStore[store.id].orEmpty()
            val stPayments = paymentsByStore[store.id].orEmpty()
            val totalS = stSales.sumOf { it.total }
            val totalP = stPayments.sumOf { it.amount }
            val cards = stSales.sumOf { it.quantity }
            val lastSaleDate = stSales.maxOfOrNull { it.date } ?: 0L
            val lastPayDate = stPayments.maxOfOrNull { it.date } ?: 0L
            val lastTx = max(lastSaleDate, lastPayDate).takeIf { it > 0L }
            StoreWithSummary(
                store = store,
                totalSales = totalS,
                totalPayments = totalP,
                totalCardsSold = cards,
                lastTransactionDate = lastTx
            )
        }
    }

    val dashboardStatsFlow: Flow<DashboardStats> = combine(
        packagesWithInventoryFlow,
        storesWithSummaryFlow,
        expensesFlow
    ) { pkgsWithInv, storesWithSum, expenses ->
        val totalSales = storesWithSum.sumOf { it.totalSales }
        val totalPayments = storesWithSum.sumOf { it.totalPayments }
        val totalDebts = storesWithSum.sumOf { it.currentDebt }
        val totalExpenses = expenses.sumOf { it.amount }
        DashboardStats(
            packagesCount = pkgsWithInv.size,
            totalInventoryCards = pkgsWithInv.sumOf { it.quantity },
            storesCount = storesWithSum.size,
            totalSales = totalSales,
            totalPayments = totalPayments,
            totalDebts = totalDebts,
            totalExpenses = totalExpenses,
            netProfit = totalSales - totalExpenses
        )
    }

    // ==================== PACKAGES & INVENTORY OPERATIONS ====================
    suspend fun savePackage(pkg: PackageEntity, initialQuantity: Int = 0): Long {
        return db.withTransaction {
            val id = if (pkg.id == 0L) {
                val newId = dao.insertPackage(pkg)
                dao.insertInventory(InventoryEntity(packageId = newId, quantity = initialQuantity))
                if (initialQuantity > 0) {
                    dao.insertInventoryMovement(
                        InventoryMovementEntity(
                            packageId = newId,
                            packageName = pkg.name,
                            changeAmount = initialQuantity,
                            newQuantity = initialQuantity,
                            reason = "رصيد افتتاحي عند تعريف الباقة"
                        )
                    )
                }
                newId
            } else {
                dao.updatePackage(pkg)
                pkg.id
            }
            id
        }
    }

    suspend fun deletePackage(pkg: PackageEntity, forceConfirm: Boolean = false) {
        val salesCount = dao.getSalesCountForPackage(pkg.id)
        if (salesCount > 0 && !forceConfirm) {
            throw PackageLinkedToSalesException(salesCount)
        }
        db.withTransaction {
            if (salesCount > 0) {
                dao.deleteSalesForPackage(pkg.id)
            }
            dao.deletePackage(pkg)
        }
    }

    suspend fun updatePackageInventory(packageId: Long, newQuantity: Int, reason: String = "تحديث يدوي للمخزون") {
        db.withTransaction {
            val pkg = dao.getPackageById(packageId) ?: return@withTransaction
            val currentInv = dao.getInventoryForPackage(packageId)
            val oldQty = currentInv?.quantity ?: 0
            val diff = newQuantity - oldQty
            if (currentInv == null) {
                dao.insertInventory(InventoryEntity(packageId = packageId, quantity = newQuantity))
            } else {
                dao.updateInventoryQuantity(packageId, newQuantity)
            }
            dao.insertInventoryMovement(
                InventoryMovementEntity(
                    packageId = packageId,
                    packageName = pkg.name,
                    changeAmount = diff,
                    newQuantity = newQuantity,
                    reason = reason
                )
            )
        }
    }

    // ==================== STORES OPERATIONS ====================
    fun isValidPhoneNumber(phone: String): Boolean {
        val cleaned = phone.trim().replace(" ", "").replace("-", "")
        return cleaned.matches(Regex("^(\+?[0-9]{7,15})$"))
    }

    suspend fun saveStore(store: StoreEntity): Long {
        if (store.id == 0L) {
            return dao.insertStore(store)
        } else {
            dao.updateStore(store)
            return store.id
        }
    }

    suspend fun deleteStore(storeSummary: StoreWithSummary, forceConfirm: Boolean = false) {
        if (storeSummary.currentDebt > 0.009 && !forceConfirm) {
            throw StoreHasDebtException(storeSummary.currentDebt)
        }
        dao.deleteStore(storeSummary.store)
    }

    suspend fun getUnitPriceForStore(store: StoreEntity, pkg: PackageEntity): Double {
        return when (store.priceType) {
            "wholesale" -> pkg.wholesalePrice
            "distributor" -> pkg.distributorPrice
            else -> pkg.retailPrice
        }
    }

    // ==================== SALES & PAYMENTS OPERATIONS ====================
    suspend fun recordSale(
        storeId: Long,
        packageId: Long,
        quantity: Int,
        customUnitPrice: Double? = null,
        date: Long = System.currentTimeMillis()
    ): Long {
        return db.withTransaction {
            val store = dao.getStoreById(storeId) ?: throw IllegalArgumentException("المحل غير موجود")
            val pkg = dao.getPackageById(packageId) ?: throw IllegalArgumentException("الباقة غير موجودة")
            val inv = dao.getInventoryForPackage(packageId)
            val available = inv?.quantity ?: 0
            if (available < quantity) {
                throw InsufficientStockException(available, quantity)
            }
            val unitPrice = customUnitPrice ?: getUnitPriceForStore(store, pkg)
            val total = kotlin.math.round(quantity * unitPrice * 100.0) / 100.0
            val newQty = available - quantity

            dao.updateInventoryQuantity(packageId, newQty)
            dao.insertInventoryMovement(
                InventoryMovementEntity(
                    packageId = packageId,
                    packageName = pkg.name,
                    changeAmount = -quantity,
                    newQuantity = newQty,
                    reason = "بيع للمحل: ${store.name}"
                )
            )
            dao.insertSale(
                SaleEntity(
                    storeId = storeId,
                    packageId = packageId,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    total = total,
                    date = date
                )
            )
        }
    }

    suspend fun updateSaleWithStockAdjustment(oldSale: SaleEntity, newQuantity: Int, newUnitPrice: Double, newDate: Long) {
        db.withTransaction {
            val pkg = dao.getPackageById(oldSale.packageId) ?: return@withTransaction
            val inv = dao.getInventoryForPackage(oldSale.packageId)
            val available = inv?.quantity ?: 0
            val diffQty = newQuantity - oldSale.quantity
            if (diffQty > 0 && available < diffQty) {
                throw InsufficientStockException(available, diffQty)
            }
            val updatedStock = available - diffQty
            dao.updateInventoryQuantity(oldSale.packageId, updatedStock)
            if (diffQty != 0) {
                dao.insertInventoryMovement(
                    InventoryMovementEntity(
                        packageId = pkg.id,
                        packageName = pkg.name,
                        changeAmount = -diffQty,
                        newQuantity = updatedStock,
                        reason = "تعديل عملية بيع رقم #${oldSale.id}"
                    )
                )
            }
            val newTotal = kotlin.math.round(newQuantity * newUnitPrice * 100.0) / 100.0
            dao.updateSale(
                oldSale.copy(
                    quantity = newQuantity,
                    unitPrice = newUnitPrice,
                    total = newTotal,
                    date = newDate
                )
            )
        }
    }

    suspend fun deleteSaleAndRestoreStock(sale: SaleEntity) {
        db.withTransaction {
            val pkg = dao.getPackageById(sale.packageId)
            val inv = dao.getInventoryForPackage(sale.packageId)
            val newQty = (inv?.quantity ?: 0) + sale.quantity
            dao.updateInventoryQuantity(sale.packageId, newQty)
            if (pkg != null) {
                dao.insertInventoryMovement(
                    InventoryMovementEntity(
                        packageId = pkg.id,
                        packageName = pkg.name,
                        changeAmount = sale.quantity,
                        newQuantity = newQty,
                        reason = "إلغاء/حذف عملية بيع رقم #${sale.id}"
                    )
                )
            }
            dao.deleteSale(sale)
        }
    }

    suspend fun recordPayment(storeId: Long, amount: Double, notes: String, date: Long = System.currentTimeMillis()): Long {
        val rounded = kotlin.math.round(amount * 100.0) / 100.0
        return dao.insertPayment(
            PaymentEntity(
                storeId = storeId,
                amount = rounded,
                date = date,
                notes = notes
            )
        )
    }

    suspend fun updatePayment(payment: PaymentEntity) {
        dao.updatePayment(payment)
    }

    suspend fun deletePayment(payment: PaymentEntity) {
        dao.deletePayment(payment)
    }

    // ==================== FINANCIAL CYCLES ENGINE ====================
    suspend fun computeStoreFinancialCycles(
        storeId: Long,
        cycleType: String, // daily | weekly | monthly | custom
        customFrom: Long? = null,
        customTo: Long? = null
    ): List<FinancialCycleRow> {
        val packagesMap = dao.getAllPackages().associateBy { it.id }
        val store = dao.getStoreById(storeId)
        val sales = dao.getSalesForStoreAsc(storeId)
        val payments = dao.getPaymentsForStoreAsc(storeId)
        val closedCycles = dao.getFinancialCyclesForStore(storeId)

        if (cycleType == "custom" && customFrom != null && customTo != null) {
            val start = startOfDay(customFrom)
            val end = endOfDay(customTo)
            val openingSales = sales.filter { it.date < start }.sumOf { it.total }
            val openingPayments = payments.filter { it.date < start }.sumOf { it.amount }
            val openingBal = openingSales - openingPayments

            val cycleSales = sales.filter { it.date in start..end }.map {
                SaleWithPackageName(it, packagesMap[it.packageId]?.name ?: "باقة #${it.packageId}", store?.name ?: "")
            }
            val cyclePayments = payments.filter { it.date in start..end }
            val sumSales = cycleSales.sumOf { it.sale.total }
            val sumPay = cyclePayments.sumOf { it.amount }
            val closingBal = openingBal + sumSales - sumPay
            val matchedClosed = closedCycles.find { it.fromDate == start && it.toDate == end }

            return listOf(
                FinancialCycleRow(
                    cycleId = matchedClosed?.id,
                    storeId = storeId,
                    fromDate = start,
                    toDate = end,
                    cardsSold = cycleSales.sumOf { it.sale.quantity },
                    totalSales = sumSales,
                    totalPayments = sumPay,
                    openingBalance = openingBal,
                    closingBalance = closingBal,
                    status = matchedClosed?.status ?: "open",
                    salesInCycle = cycleSales,
                    paymentsInCycle = cyclePayments
                )
            )
        }

        val allTimestamps = (sales.map { it.date } + payments.map { it.date } + listOf(System.currentTimeMillis()))
        val minDate = allTimestamps.minOrNull() ?: System.currentTimeMillis()
        val maxDate = max(allTimestamps.maxOrNull() ?: System.currentTimeMillis(), System.currentTimeMillis())

        val windows = buildPeriodWindows(minDate, maxDate, cycleType)
        var runningBalance = 0.0
        val rows = mutableListOf<FinancialCycleRow>()

        for ((wStart, wEnd) in windows) {
            val cycleSales = sales.filter { it.date in wStart..wEnd }.map {
                SaleWithPackageName(it, packagesMap[it.packageId]?.name ?: "باقة #${it.packageId}", store?.name ?: "")
            }
            val cyclePayments = payments.filter { it.date in wStart..wEnd }
            if (cycleSales.isEmpty() && cyclePayments.isEmpty() && wEnd < startOfDay(System.currentTimeMillis())) {
                continue
            }
            val openingBal = runningBalance
            val sumSales = cycleSales.sumOf { it.sale.total }
            val sumPay = cyclePayments.sumOf { it.amount }
            val closingBal = openingBal + sumSales - sumPay
            runningBalance = closingBal

            val matchedClosed = closedCycles.find {
                kotlin.math.abs(it.fromDate - wStart) < 60_000L && kotlin.math.abs(it.toDate - wEnd) < 60_000L
            }

            rows.add(
                FinancialCycleRow(
                    cycleId = matchedClosed?.id,
                    storeId = storeId,
                    fromDate = wStart,
                    toDate = wEnd,
                    cardsSold = cycleSales.sumOf { it.sale.quantity },
                    totalSales = sumSales,
                    totalPayments = sumPay,
                    openingBalance = openingBal,
                    closingBalance = closingBal,
                    status = matchedClosed?.status ?: "open",
                    salesInCycle = cycleSales,
                    paymentsInCycle = cyclePayments
                )
            )
        }
        return rows.reversed() // الأحدث أولاً
    }

    suspend fun closeCycle(row: FinancialCycleRow) {
        val entity = FinancialCycleEntity(
            id = row.cycleId ?: 0L,
            storeId = row.storeId,
            fromDate = row.fromDate,
            toDate = row.toDate,
            openingBalance = row.openingBalance,
            closingBalance = row.closingBalance,
            status = "closed"
        )
        dao.insertFinancialCycle(entity)
    }

    private fun buildPeriodWindows(minDate: Long, maxDate: Long, cycleType: String): List<Pair<Long, Long>> {
        val result = mutableListOf<Pair<Long, Long>>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = startOfDay(minDate)

        when (cycleType) {
            "daily" -> {
                while (cal.timeInMillis <= maxDate) {
                    val s = startOfDay(cal.timeInMillis)
                    val e = endOfDay(cal.timeInMillis)
                    result.add(s to e)
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            "monthly" -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                while (cal.timeInMillis <= maxDate) {
                    val s = startOfDay(cal.timeInMillis)
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    val e = endOfDay(cal.timeInMillis)
                    result.add(s to e)
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            else -> { // weekly (7 days)
                while (cal.timeInMillis <= maxDate) {
                    val s = startOfDay(cal.timeInMillis)
                    cal.add(Calendar.DAY_OF_YEAR, 6)
                    val e = endOfDay(cal.timeInMillis)
                    result.add(s to e)
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }
        return result
    }

    fun startOfDay(ts: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return c.timeInMillis
    }

    fun endOfDay(ts: Long): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return c.timeInMillis
    }

    // ==================== EXPENSES & CUSTOM TYPES ====================
    suspend fun saveExpense(expense: ExpenseEntity): Long {
        return if (expense.id == 0L) dao.insertExpense(expense) else {
            dao.updateExpense(expense)
            expense.id
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        dao.deleteExpense(expense)
    }

    suspend fun addCustomExpenseType(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        return dao.insertExpenseType(ExpenseTypeEntity(name = trimmed, isDefault = false))
    }

    suspend fun deleteCustomExpenseType(id: Long) {
        dao.deleteCustomExpenseType(id)
    }

    // ==================== PARTNERS & REPORTS ====================
    suspend fun savePartner(partner: PartnerEntity): Long {
        return if (partner.id == 0L) dao.insertPartner(partner) else {
            dao.updatePartner(partner)
            partner.id
        }
    }

    suspend fun deletePartner(partner: PartnerEntity) {
        dao.deletePartner(partner)
    }

    suspend fun addPartnerTransaction(partnerId: Long, type: String, amount: Double, notes: String = "", date: Long = System.currentTimeMillis()) {
        dao.insertPartnerTransaction(
            PartnerTransactionEntity(
                partnerId = partnerId,
                type = type,
                amount = amount,
                date = date,
                notes = notes
            )
        )
    }

    suspend fun getMonthlyComparisonStats(): MonthlyComparisonStats {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val currStart = startOfDay(cal.timeInMillis)
        val currEnd = endOfDay(System.currentTimeMillis())

        val prevCal = Calendar.getInstance().apply {
            timeInMillis = currStart
            add(Calendar.MONTH, -1)
        }
        val prevStart = startOfDay(prevCal.timeInMillis)
        prevCal.set(Calendar.DAY_OF_MONTH, prevCal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val prevEnd = endOfDay(prevCal.timeInMillis)

        val currSales = dao.getSalesBetweenDates(currStart, currEnd).sumOf { it.total }
        val prevSales = dao.getSalesBetweenDates(prevStart, prevEnd).sumOf { it.total }
        val currPayments = dao.getPaymentsBetweenDates(currStart, currEnd).sumOf { it.amount }
        val prevPayments = dao.getPaymentsBetweenDates(prevStart, prevEnd).sumOf { it.amount }
        val currExpenses = dao.getExpensesBetweenDates(currStart, currEnd).sumOf { it.amount }
        val prevExpenses = dao.getExpensesBetweenDates(prevStart, prevEnd).sumOf { it.amount }

        return MonthlyComparisonStats(
            currentMonthSales = currSales,
            previousMonthSales = prevSales,
            currentMonthPayments = currPayments,
            previousMonthPayments = prevPayments,
            currentMonthExpenses = currExpenses,
            previousMonthExpenses = prevExpenses,
            currentMonthProfit = currSales - currExpenses,
            previousMonthProfit = prevSales - prevExpenses
        )
    }

    // ==================== SETTINGS & BACKUP LOG ====================
    suspend fun updateSetting(key: String, value: String) {
        dao.setSetting(SettingEntity(key, value))
    }

    suspend fun getSetting(key: String, defaultVal: String = ""): String {
        return dao.getSettingValue(key) ?: defaultVal
    }

    suspend fun logBackup(type: String, filePath: String, fileSize: Long, status: String) {
        dao.insertBackupLog(
            BackupLogEntity(
                type = type,
                filePath = filePath,
                fileSize = fileSize,
                status = status
            )
        )
    }

    suspend fun clearAllData() {
        dao.clearAllUserData()
    }
}
