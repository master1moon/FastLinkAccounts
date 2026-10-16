package com.fastlink.accounts.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PackageEntity::class,
        InventoryEntity::class,
        InventoryMovementEntity::class,
        StoreEntity::class,
        FinancialCycleEntity::class,
        SaleEntity::class,
        PaymentEntity::class,
        ExpenseEntity::class,
        ExpenseTypeEntity::class,
        PartnerEntity::class,
        PartnerTransactionEntity::class,
        SettingEntity::class,
        BackupLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FastLinkDatabase : RoomDatabase() {

    abstract fun dao(): FastLinkDao

    companion object {
        const val DB_NAME = "fastlink_accounts.db"

        // القائمة الإلزامية لأنواع المصروفات الافتراضية (14 نوعاً)
        val MANDATORY_DEFAULT_EXPENSE_TYPES = listOf(
            "تسديد انترنت دي اس ال",
            "انترنت فايبر",
            "انترنت ستار",
            "ايجار بيوت واستراحة",
            "كهرباء",
            "بترول",
            "بترول للماطور",
            "كيبل انترنت منزلي",
            "كيبل انترنت خارجي",
            "كيبل كهرباء",
            "مواصلات",
            "سويتش",
            "صيانة",
            "عامل"
        )

        val DEFAULT_SETTINGS = mapOf(
            "currency" to "ريال",
            "dark_mode" to "system",
            "font_scale" to "medium",
            "primary_color" to "#0D47A1",
            "secondary_color" to "#00897B",
            "auto_local_backup" to "true",
            "auto_drive_backup" to "false",
            "local_backup_interval_hours" to "24",
            "drive_backup_interval_hours" to "24",
            "max_backup_count" to "10",
            "backup_on_exit" to "false",
            "notify_daily_debts" to "true",
            "notify_weekly_expenses" to "true",
            "notify_backup_status" to "true",
            "partner_profit_basis" to "sales"
        )

        @Volatile
        private var INSTANCE: FastLinkDatabase? = null

        fun getInstance(context: Context): FastLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FastLinkDatabase::class.java,
                    DB_NAME
                )
                    .setJournalMode(JournalMode.TRUNCATE) // يضمن حفظ جميع البيانات مباشرة في ملف .db واحد لسهولة النسخ الاحتياطي
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedDefaultsSync(db)
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            seedDefaultsSync(db)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun seedDefaultsSync(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.beginTransaction()
            try {
                for (typeName in MANDATORY_DEFAULT_EXPENSE_TYPES) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO expense_types (name, is_default, created_at) VALUES (?, 1, ?)",
                        arrayOf<Any>(typeName, now)
                    )
                }
                for ((k, v) in DEFAULT_SETTINGS) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO settings (`key`, `value`) VALUES (?, ?)",
                        arrayOf<Any>(k, v)
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        fun closeAndResetInstance() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
