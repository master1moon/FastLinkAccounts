package com.fastlink.accounts.worker

import android.content.Context
import androidx.work.*
import com.fastlink.accounts.backup.BackupManager
import com.fastlink.accounts.backup.GoogleDriveBackupHelper
import com.fastlink.accounts.data.FastLinkDatabase
import java.util.concurrent.TimeUnit

class BackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = FastLinkDatabase.getInstance(applicationContext)
        val dao = db.dao()

        val autoLocal = (dao.getSettingValue("auto_local_backup") ?: "true") == "true"
        val autoDrive = (dao.getSettingValue("auto_drive_backup") ?: "false") == "true"
        val maxKeep = (dao.getSettingValue("max_backup_count") ?: "10").toIntOrNull() ?: 10
        val notifyStatus = (dao.getSettingValue("notify_backup_status") ?: "true") == "true"

        var localFileResult = BackupManager.performLocalBackup(
            context = applicationContext,
            maxBackupsToKeep = maxKeep,
            notifyUser = autoLocal && notifyStatus
        )

        if (autoDrive && GoogleDriveBackupHelper.getSignedInAccount(applicationContext) != null) {
            val fileToUpload = localFileResult.getOrNull()
            if (fileToUpload != null) {
                GoogleDriveBackupHelper.uploadBackupToDrive(
                    context = applicationContext,
                    localBackupFile = fileToUpload,
                    maxBackupsToKeep = maxKeep,
                    notifyUser = notifyStatus
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "fastlink_periodic_backup_work"

        fun schedulePeriodicBackup(context: Context, intervalHours: Long = 24L) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                intervalHours.coerceAtLeast(6L),
                TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
