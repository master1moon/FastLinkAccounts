package com.fastlink.accounts

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.fastlink.accounts.backup.BackupManager
import com.fastlink.accounts.data.FastLinkDatabase
import com.fastlink.accounts.util.NotificationHelper
import com.fastlink.accounts.worker.BackupWorker
import com.fastlink.accounts.worker.ReminderWorker

class FastLinkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // فرض اللغة العربية واتجاه RTL بشكل إجباري على مستوى التطبيق
        val appLocale = LocaleListCompat.forLanguageTags("ar")
        AppCompatDelegate.setApplicationLocales(appLocale)

        // تهيئة قنوات الإشعارات وقاعدة البيانات ومجلد النسخ الاحتياطي NGS BACKUP
        NotificationHelper.createChannels(this)
        FastLinkDatabase.getInstance(this)
        BackupManager.ensureBackupDirectoryExists(this)

        // جدولة المهام الخلفية الدورية (WorkManager)
        BackupWorker.schedulePeriodicBackup(this, 24L)
        ReminderWorker.scheduleDailyReminders(this)
    }
}
