package com.fastlink.accounts.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import com.fastlink.accounts.data.FastLinkDatabase
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.util.NotificationHelper
import java.io.File

data class BackupPreviewInfo(
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val lastModified: Long,
    val storesCount: Int,
    val expensesCount: Int,
    val salesCount: Int
)

object BackupManager {

    const val BACKUP_FOLDER_NAME = "NGS BACKUP"

    fun getLocalBackupDirectory(context: Context): File {
        // المسار الإلزامي: /storage/emulated/0/NGS BACKUP/
        val primaryRoot = Environment.getExternalStorageDirectory()
        val ngsDir = File(primaryRoot, BACKUP_FOLDER_NAME)
        if (ngsDir.exists() || ngsDir.mkdirs()) {
            return ngsDir
        }
        // مسار بديل آمن في حال لم يمنح المستخدم صلاحية MANAGE_EXTERNAL_STORAGE بعد
        val fallbackDir = File(context.getExternalFilesDir(null), BACKUP_FOLDER_NAME)
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }
        return fallbackDir
    }

    fun ensureBackupDirectoryExists(context: Context): File {
        return getLocalBackupDirectory(context)
    }

    suspend fun performLocalBackup(
        context: Context,
        maxBackupsToKeep: Int = 10,
        notifyUser: Boolean = true
    ): Result<File> {
        return try {
            val db = FastLinkDatabase.getInstance(context)
            // تنفيذ checkpoint لضمان كتابة كافة البيانات في ملف القاعدة الرئيسي
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()

            val dbFile = context.getDatabasePath(FastLinkDatabase.DB_NAME)
            val backupDir = getLocalBackupDirectory(context)
            val fileName = "FastLink_Backup_${FormatUtils.formatFileTimestamp()}.db"
            val destFile = File(backupDir, fileName)

            dbFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // الاحتفاظ بآخر N نسخ فقط وحذف الأقدم تلقائياً
            pruneOldLocalBackups(backupDir, maxBackupsToKeep)

            db.dao().insertBackupLog(
                com.fastlink.accounts.data.BackupLogEntity(
                    type = "local",
                    filePath = destFile.absolutePath,
                    fileSize = destFile.length(),
                    status = "success"
                )
            )

            if (notifyUser) {
                NotificationHelper.showNotification(
                    context,
                    NotificationHelper.CHANNEL_BACKUP,
                    "تم إنشاء النسخة الاحتياطية المحلية",
                    "المجلد: NGS BACKUP/${destFile.name}"
                )
            }
            Result.success(destFile)
        } catch (e: Exception) {
            runCatching {
                FastLinkDatabase.getInstance(context).dao().insertBackupLog(
                    com.fastlink.accounts.data.BackupLogEntity(
                        type = "local",
                        filePath = e.localizedMessage ?: "خطأ غير معروف",
                        fileSize = 0L,
                        status = "failed"
                    )
                )
            }
            if (notifyUser) {
                NotificationHelper.showNotification(
                    context,
                    NotificationHelper.CHANNEL_BACKUP,
                    "فشل النسخ الاحتياطي المحلي",
                    e.localizedMessage ?: "تأكد من منح صلاحية التخزين"
                )
            }
            Result.failure(e)
        }
    }

    private fun pruneOldLocalBackups(backupDir: File, maxKeep: Int) {
        val files = backupDir.listFiles { f ->
            f.isFile && f.name.startsWith("FastLink_Backup_") && f.name.endsWith(".db")
        }?.sortedByDescending { it.lastModified() } ?: return

        if (files.size > maxKeep) {
            files.drop(maxKeep).forEach { oldFile ->
                oldFile.delete()
            }
        }
    }

    fun listLocalBackups(context: Context): List<File> {
        val dir = getLocalBackupDirectory(context)
        return dir.listFiles { f ->
            f.isFile && f.name.endsWith(".db")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun inspectBackupFile(file: File): BackupPreviewInfo {
        var stores = 0
        var expenses = 0
        var sales = 0
        var sqlDb: SQLiteDatabase? = null
        try {
            sqlDb = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            sqlDb.rawQuery("SELECT COUNT(*) FROM stores", null).use { c ->
                if (c.moveToFirst()) stores = c.getInt(0)
            }
            sqlDb.rawQuery("SELECT COUNT(*) FROM expenses", null).use { c ->
                if (c.moveToFirst()) expenses = c.getInt(0)
            }
            sqlDb.rawQuery("SELECT COUNT(*) FROM sales", null).use { c ->
                if (c.moveToFirst()) sales = c.getInt(0)
            }
        } catch (_: Exception) {
        } finally {
            sqlDb?.close()
        }
        return BackupPreviewInfo(
            fileName = file.name,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            lastModified = file.lastModified(),
            storesCount = stores,
            expensesCount = expenses,
            salesCount = sales
        )
    }

    fun restoreFromBackupFile(context: Context, backupFile: File): Result<Unit> {
        return try {
            FastLinkDatabase.closeAndResetInstance()
            val targetDbFile = context.getDatabasePath(FastLinkDatabase.DB_NAME)
            val walFile = File(targetDbFile.path + "-wal")
            val shmFile = File(targetDbFile.path + "-shm")
            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            backupFile.inputStream().use { input ->
                targetDbFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // إعادة تهيئة قاعدة البيانات
            FastLinkDatabase.getInstance(context)

            NotificationHelper.showNotification(
                context,
                NotificationHelper.CHANNEL_BACKUP,
                "تمت استعادة البيانات بنجاح",
                "تم استرجاع النسخة: ${backupFile.name}"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
