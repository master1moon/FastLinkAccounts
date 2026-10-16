package com.fastlink.accounts.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FastLinkDao {

    // ==================== PACKAGES & INVENTORY ====================
    @Query("SELECT * FROM packages ORDER BY name ASC")
    fun observePackages(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages ORDER BY name ASC")
    suspend fun getAllPackages(): List<PackageEntity>

    @Query("SELECT * FROM packages WHERE id = :id")
    suspend fun getPackageById(id: Long): PackageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: PackageEntity): Long

    @Update
    suspend fun updatePackage(pkg: PackageEntity)

    @Delete
    suspend fun deletePackage(pkg: PackageEntity)

    @Query("SELECT COUNT(*) FROM sales WHERE package_id = :packageId")
    suspend fun getSalesCountForPackage(packageId: Long): Int

    @Query("DELETE FROM sales WHERE package_id = :packageId")
    suspend fun deleteSalesForPackage(packageId: Long)

    @Query("SELECT * FROM inventory")
    fun observeInventory(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory WHERE package_id = :packageId LIMIT 1")
    suspend fun getInventoryForPackage(packageId: Long): InventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventory(inventory: InventoryEntity): Long

    @Query("UPDATE inventory SET quantity = :newQuantity, updated_at = :timestamp WHERE package_id = :packageId")
    suspend fun updateInventoryQuantity(packageId: Long, newQuantity: Int, timestamp: Long = System.currentTimeMillis())

    @Insert
    suspend fun insertInventoryMovement(movement: InventoryMovementEntity)

    @Query("SELECT * FROM inventory_movements ORDER BY timestamp DESC LIMIT 200")
    fun observeInventoryMovements(): Flow<List<InventoryMovementEntity>>

    // ==================== STORES ====================
    @Query("SELECT * FROM stores ORDER BY name ASC")
    fun observeStores(): Flow<List<StoreEntity>>

    @Query("SELECT * FROM stores ORDER BY name ASC")
    suspend fun getAllStores(): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE id = :storeId")
    suspend fun getStoreById(storeId: Long): StoreEntity?

    @Query("SELECT * FROM stores WHERE id = :storeId")
    fun observeStoreById(storeId: Long): Flow<StoreEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: StoreEntity): Long

    @Update
    suspend fun updateStore(store: StoreEntity)

    @Delete
    suspend fun deleteStore(store: StoreEntity)

    // ==================== SALES & PAYMENTS ====================
    @Query("SELECT * FROM sales ORDER BY date DESC")
    fun observeAllSales(): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE store_id = :storeId ORDER BY date DESC")
    fun observeSalesForStore(storeId: Long): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales WHERE store_id = :storeId ORDER BY date ASC")
    suspend fun getSalesForStoreAsc(storeId: Long): List<SaleEntity>

    @Query("SELECT * FROM sales WHERE date BETWEEN :fromDate AND :toDate ORDER BY date DESC")
    suspend fun getSalesBetweenDates(fromDate: Long, toDate: Long): List<SaleEntity>

    @Insert
    suspend fun insertSale(sale: SaleEntity): Long

    @Update
    suspend fun updateSale(sale: SaleEntity)

    @Delete
    suspend fun deleteSale(sale: SaleEntity)

    @Query("SELECT * FROM payments ORDER BY date DESC")
    fun observeAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE store_id = :storeId ORDER BY date DESC")
    fun observePaymentsForStore(storeId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE store_id = :storeId ORDER BY date ASC")
    suspend fun getPaymentsForStoreAsc(storeId: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE date BETWEEN :fromDate AND :toDate ORDER BY date DESC")
    suspend fun getPaymentsBetweenDates(fromDate: Long, toDate: Long): List<PaymentEntity>

    @Insert
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)

    // ==================== FINANCIAL CYCLES ====================
    @Query("SELECT * FROM financial_cycles WHERE store_id = :storeId ORDER BY from_date DESC")
    fun observeFinancialCyclesForStore(storeId: Long): Flow<List<FinancialCycleEntity>>

    @Query("SELECT * FROM financial_cycles WHERE store_id = :storeId ORDER BY from_date ASC")
    suspend fun getFinancialCyclesForStore(storeId: Long): List<FinancialCycleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFinancialCycle(cycle: FinancialCycleEntity): Long

    @Update
    suspend fun updateFinancialCycle(cycle: FinancialCycleEntity)

    // ==================== EXPENSES & EXPENSE TYPES ====================
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE date BETWEEN :fromDate AND :toDate ORDER BY date DESC")
    suspend fun getExpensesBetweenDates(fromDate: Long, toDate: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    suspend fun getAllExpenses(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expense_types ORDER BY is_default DESC, name ASC")
    fun observeExpenseTypes(): Flow<List<ExpenseTypeEntity>>

    @Query("SELECT * FROM expense_types ORDER BY is_default DESC, name ASC")
    suspend fun getAllExpenseTypes(): List<ExpenseTypeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpenseType(expenseType: ExpenseTypeEntity): Long

    @Query("DELETE FROM expense_types WHERE id = :id AND is_default = 0")
    suspend fun deleteCustomExpenseType(id: Long)

    // ==================== PARTNERS ====================
    @Query("SELECT * FROM partners ORDER BY name ASC")
    fun observePartners(): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partners ORDER BY name ASC")
    suspend fun getAllPartners(): List<PartnerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartner(partner: PartnerEntity): Long

    @Update
    suspend fun updatePartner(partner: PartnerEntity)

    @Delete
    suspend fun deletePartner(partner: PartnerEntity)

    @Query("SELECT * FROM partner_transactions ORDER BY date DESC")
    fun observePartnerTransactions(): Flow<List<PartnerTransactionEntity>>

    @Query("SELECT * FROM partner_transactions WHERE partner_id = :partnerId ORDER BY date DESC")
    suspend fun getTransactionsForPartner(partnerId: Long): List<PartnerTransactionEntity>

    @Insert
    suspend fun insertPartnerTransaction(tx: PartnerTransactionEntity): Long

    @Delete
    suspend fun deletePartnerTransaction(tx: PartnerTransactionEntity)

    // ==================== SETTINGS & BACKUP LOG ====================
    @Query("SELECT * FROM settings")
    fun observeSettings(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity)

    @Query("SELECT * FROM backup_log ORDER BY timestamp DESC LIMIT 100")
    fun observeBackupLogs(): Flow<List<BackupLogEntity>>

    @Insert
    suspend fun insertBackupLog(log: BackupLogEntity): Long

    // ==================== AGGREGATIONS & RESET ====================
    @Query("SELECT COUNT(*) FROM stores")
    suspend fun getStoresCount(): Int

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getExpensesCount(): Int

    @Query("SELECT COALESCE(SUM(total), 0.0) FROM sales")
    suspend fun getTotalSalesAmount(): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM payments")
    suspend fun getTotalPaymentsAmount(): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE status = 'مدفوع'")
    suspend fun getTotalPaidExpensesAmount(): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE status = 'معلق'")
    suspend fun getTotalPendingExpensesAmount(): Double

    @Transaction
    suspend fun clearAllUserData() {
        deleteAllSales()
        deleteAllPayments()
        deleteAllFinancialCycles()
        deleteAllStores()
        deleteAllInventoryMovements()
        deleteAllInventory()
        deleteAllPackages()
        deleteAllExpenses()
        deleteAllCustomExpenseTypes()
        deleteAllPartnerTransactions()
        deleteAllPartners()
    }

    @Query("DELETE FROM sales")
    suspend fun deleteAllSales()

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()

    @Query("DELETE FROM financial_cycles")
    suspend fun deleteAllFinancialCycles()

    @Query("DELETE FROM stores")
    suspend fun deleteAllStores()

    @Query("DELETE FROM inventory_movements")
    suspend fun deleteAllInventoryMovements()

    @Query("DELETE FROM inventory")
    suspend fun deleteAllInventory()

    @Query("DELETE FROM packages")
    suspend fun deleteAllPackages()

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("DELETE FROM expense_types WHERE is_default = 0")
    suspend fun deleteAllCustomExpenseTypes()

    @Query("DELETE FROM partner_transactions")
    suspend fun deleteAllPartnerTransactions()

    @Query("DELETE FROM partners")
    suspend fun deleteAllPartners()
}
