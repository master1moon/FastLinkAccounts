package com.fastlink.accounts.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "packages",
    indices = [Index(value = ["name"]), Index(value = ["created_at"])]
)
data class PackageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "retail_price") val retailPrice: Double,
    @ColumnInfo(name = "wholesale_price") val wholesalePrice: Double,
    @ColumnInfo(name = "distributor_price") val distributorPrice: Double,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "inventory",
    foreignKeys = [
        ForeignKey(
            entity = PackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["package_id"], unique = true)]
)
data class InventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_id") val packageId: Long,
    @ColumnInfo(name = "quantity") val quantity: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "inventory_movements",
    foreignKeys = [
        ForeignKey(
            entity = PackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["package_id"]), Index(value = ["timestamp"])]
)
data class InventoryMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "package_id") val packageId: Long,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "change_amount") val changeAmount: Int,
    @ColumnInfo(name = "new_quantity") val newQuantity: Int,
    @ColumnInfo(name = "reason") val reason: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "stores",
    indices = [Index(value = ["name"]), Index(value = ["phone"]), Index(value = ["created_at"])]
)
data class StoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "owner_name") val ownerName: String,
    @ColumnInfo(name = "phone") val phone: String,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "price_type") val priceType: String, // retail | wholesale | distributor
    @ColumnInfo(name = "cycle_type") val cycleType: String = "weekly", // daily | weekly | monthly | custom
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "financial_cycles",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["store_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["store_id"]), Index(value = ["from_date", "to_date"])]
)
data class FinancialCycleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "store_id") val storeId: Long,
    @ColumnInfo(name = "from_date") val fromDate: Long,
    @ColumnInfo(name = "to_date") val toDate: Long,
    @ColumnInfo(name = "opening_balance") val openingBalance: Double,
    @ColumnInfo(name = "closing_balance") val closingBalance: Double,
    @ColumnInfo(name = "status") val status: String = "open", // open | closed
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["store_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PackageEntity::class,
            parentColumns = ["id"],
            childColumns = ["package_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index(value = ["store_id"]), Index(value = ["package_id"]), Index(value = ["date"]), Index(value = ["cycle_id"])]
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "store_id") val storeId: Long,
    @ColumnInfo(name = "package_id") val packageId: Long,
    @ColumnInfo(name = "quantity") val quantity: Int,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    @ColumnInfo(name = "total") val total: Double,
    @ColumnInfo(name = "date") val date: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "cycle_id") val cycleId: Long? = null
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = StoreEntity::class,
            parentColumns = ["id"],
            childColumns = ["store_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["store_id"]), Index(value = ["date"]), Index(value = ["cycle_id"])]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "store_id") val storeId: Long,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "date") val date: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "cycle_id") val cycleId: Long? = null
)

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["date"]), Index(value = ["type"]), Index(value = ["status"])]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "notes") val notes: String = "",
    @ColumnInfo(name = "status") val status: String = "مدفوع" // مدفوع | معلق
)

@Entity(
    tableName = "expense_types",
    indices = [Index(value = ["name"], unique = true)]
)
data class ExpenseTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "partners")
data class PartnerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "percentage") val percentage: Double,
    @ColumnInfo(name = "phone") val phone: String
)

@Entity(
    tableName = "partner_transactions",
    foreignKeys = [
        ForeignKey(
            entity = PartnerEntity::class,
            parentColumns = ["id"],
            childColumns = ["partner_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["partner_id"]), Index(value = ["date"])]
)
data class PartnerTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "partner_id") val partnerId: Long,
    @ColumnInfo(name = "type") val type: String, // withdrawal | carry_forward
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "date") val date: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "notes") val notes: String = ""
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String
)

@Entity(
    tableName = "backup_log",
    indices = [Index(value = ["timestamp"])]
)
data class BackupLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "type") val type: String, // local | drive
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "status") val status: String, // success | failed
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)

// Domain / UI Projection Models
data class PackageWithInventory(
    val pkg: PackageEntity,
    val quantity: Int,
    val updatedAt: Long
) {
    val retailTotalValue: Double get() = quantity * pkg.retailPrice
    val wholesaleTotalValue: Double get() = quantity * pkg.wholesalePrice
    val distributorTotalValue: Double get() = quantity * pkg.distributorPrice
}

data class StoreWithSummary(
    val store: StoreEntity,
    val totalSales: Double,
    val totalPayments: Double,
    val totalCardsSold: Int,
    val lastTransactionDate: Long?
) {
    val currentDebt: Double get() = totalSales - totalPayments
    val priceTypeArabic: String
        get() = when (store.priceType) {
            "wholesale" -> "جملة"
            "distributor" -> "موزعين"
            else -> "تجزئة"
        }
}

data class FinancialCycleRow(
    val cycleId: Long?,
    val storeId: Long,
    val fromDate: Long,
    val toDate: Long,
    val cardsSold: Int,
    val totalSales: Double,
    val totalPayments: Double,
    val openingBalance: Double,
    val closingBalance: Double,
    val status: String, // open | closed
    val salesInCycle: List<SaleWithPackageName>,
    val paymentsInCycle: List<PaymentEntity>
)

data class SaleWithPackageName(
    val sale: SaleEntity,
    val packageName: String,
    val storeName: String = ""
)

sealed class TimelineItem(val date: Long) {
    data class SaleItem(val saleWithPkg: SaleWithPackageName) : TimelineItem(saleWithPkg.sale.date)
    data class PaymentItem(val payment: PaymentEntity) : TimelineItem(payment.date)
}

data class DashboardStats(
    val packagesCount: Int = 0,
    val totalInventoryCards: Int = 0,
    val storesCount: Int = 0,
    val totalSales: Double = 0.0,
    val totalPayments: Double = 0.0,
    val totalDebts: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0
)

data class PartnerReportRow(
    val partner: PartnerEntity,
    val carriedOverBalance: Double,
    val withdrawals: Double,
    val profitShare: Double,
    val netPayableProfit: Double
)

data class MonthlyComparisonStats(
    val currentMonthSales: Double,
    val previousMonthSales: Double,
    val currentMonthPayments: Double,
    val previousMonthPayments: Double,
    val currentMonthExpenses: Double,
    val previousMonthExpenses: Double,
    val currentMonthProfit: Double,
    val previousMonthProfit: Double
)
