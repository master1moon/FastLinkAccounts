package com.fastlink.accounts.backup

import android.content.Context
import com.fastlink.accounts.data.BackupLogEntity
import com.fastlink.accounts.data.FastLinkDatabase
import com.fastlink.accounts.util.NotificationHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

data class DriveBackupItem(
    val fileId: String,
    val name: String,
    val sizeBytes: Long,
    val createdTimeMillis: Long
)

object GoogleDriveBackupHelper {

    const val DRIVE_FOLDER_NAME = "NGS BACKUP"

    fun getSignInClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignedInAccount(context: Context): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    private fun buildDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            Collections.singleton(DriveScopes.DRIVE_FILE)
        ).apply {
            selectedAccount = account.account
        }
        return Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("فاست لينك - حسابات").build()
    }

    suspend fun getOrCreateNgsBackupFolderId(context: Context): String? = withContext(Dispatchers.IO) {
        val account = getSignedInAccount(context) ?: return@withContext null
        val drive = buildDriveService(context, account)

        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$DRIVE_FOLDER_NAME' and 'root' in parents and trashed = false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        val existing = result.files?.firstOrNull()
        if (existing != null) {
            return@withContext existing.id
        }

        val folderMeta = com.google.api.services.drive.model.File().apply {
            name = DRIVE_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf("root")
        }
        val created = drive.files().create(folderMeta).setFields("id").execute()
        created.id
    }

    suspend fun uploadBackupToDrive(
        context: Context,
        localBackupFile: File,
        maxBackupsToKeep: Int = 10,
        notifyUser: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        val db = FastLinkDatabase.getInstance(context)
        try {
            val account = getSignedInAccount(context)
                ?: throw IllegalStateException("لم يتم تسجيل الدخول بحساب Google Drive")
            val drive = buildDriveService(context, account)
            val folderId = getOrCreateNgsBackupFolderId(context)
                ?: throw IllegalStateException("تعذر إنشاء مجلد NGS BACKUP في Google Drive")

            val fileMeta = com.google.api.services.drive.model.File().apply {
                name = localBackupFile.name
                parents = listOf(folderId)
            }
            val mediaContent = FileContent("application/x-sqlite3", localBackupFile)
            val uploaded = drive.files().create(fileMeta, mediaContent)
                .setFields("id, name, size, createdTime")
                .execute()

            pruneOldDriveBackups(drive, folderId, maxBackupsToKeep)

            db.dao().insertBackupLog(
                BackupLogEntity(
                    type = "drive",
                    filePath = "Drive:/$DRIVE_FOLDER_NAME/${uploaded.name}",
                    fileSize = localBackupFile.length(),
                    status = "success"
                )
            )
            db.dao().setSetting(
                com.fastlink.accounts.data.SettingEntity(
                    "last_drive_backup_time",
                    System.currentTimeMillis().toString()
                )
            )

            if (notifyUser) {
                NotificationHelper.showNotification(
                    context,
                    NotificationHelper.CHANNEL_BACKUP,
                    "تم رفع النسخة الاحتياطية إلى Google Drive",
                    "المجلد: $DRIVE_FOLDER_NAME/${uploaded.name}"
                )
            }
            Result.success(uploaded.id)
        } catch (e: Exception) {
            runCatching {
                db.dao().insertBackupLog(
                    BackupLogEntity(
                        type = "drive",
                        filePath = e.localizedMessage ?: "فشل الرفع",
                        fileSize = 0L,
                        status = "failed"
                    )
                )
            }
            if (notifyUser) {
                NotificationHelper.showNotification(
                    context,
                    NotificationHelper.CHANNEL_BACKUP,
                    "فشل النسخ الاحتياطي على Google Drive",
                    e.localizedMessage ?: "تحقق من الاتصال بالإنترنت وحساب Google"
                )
            }
            Result.failure(e)
        }
    }

    private fun pruneOldDriveBackups(drive: Drive, folderId: String, maxKeep: Int) {
        val query = "'$folderId' in parents and trashed = false"
        val listResult = drive.files().list()
            .setQ(query)
            .setOrderBy("createdTime desc")
            .setFields("files(id, name, createdTime)")
            .execute()
        val files = listResult.files ?: return
        if (files.size > maxKeep) {
            files.drop(maxKeep).forEach { old ->
                runCatching { drive.files().delete(old.id).execute() }
            }
        }
    }

    suspend fun listDriveBackups(context: Context): Result<List<DriveBackupItem>> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount(context)
                ?: throw IllegalStateException("الرجاء تسجيل الدخول بحساب Google أولاً")
            val drive = buildDriveService(context, account)
            val folderId = getOrCreateNgsBackupFolderId(context) ?: return@withContext Result.success(emptyList())

            val query = "'$folderId' in parents and trashed = false"
            val result = drive.files().list()
                .setQ(query)
                .setOrderBy("createdTime desc")
                .setFields("files(id, name, size, createdTime)")
                .execute()

            val items = result.files.orEmpty().map { f ->
                DriveBackupItem(
                    fileId = f.id,
                    name = f.name,
                    sizeBytes = f.getSize()?.toLong() ?: 0L,
                    createdTimeMillis = f.createdTime?.value ?: System.currentTimeMillis()
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadDriveBackupToCache(context: Context, item: DriveBackupItem): Result<File> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount(context)
                ?: throw IllegalStateException("الرجاء تسجيل الدخول بحساب Google أولاً")
            val drive = buildDriveService(context, account)
            val tempFile = File(context.cacheDir, item.name)
            FileOutputStream(tempFile).use { out ->
                drive.files().get(item.fileId).executeMediaAndDownloadTo(out)
            }
            Result.success(tempFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
