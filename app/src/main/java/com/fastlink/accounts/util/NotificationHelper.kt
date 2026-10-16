package com.fastlink.accounts.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.fastlink.accounts.R
import com.fastlink.accounts.ui.MainActivity

object NotificationHelper {
    const val CHANNEL_BACKUP = "fastlink_backup_channel"
    const val CHANNEL_REMINDERS = "fastlink_reminders_channel"
    const val CHANNEL_EXPORT = "fastlink_export_channel"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val backupChannel = NotificationChannel(
                CHANNEL_BACKUP,
                "النسخ الاحتياطي والمزامنة",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات نجاح أو فشل النسخ الاحتياطي واستعادة البيانات"
            }
            val reminderChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "تذكيرات الديون والمصروفات",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "التذكير اليومي بالديون المستحقة والأسبوعي بالمصروفات المعلقة"
            }
            val exportChannel = NotificationChannel(
                CHANNEL_EXPORT,
                "تصدير التقارير والملفات",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات حفظ تقارير PDF و Excel"
            }
            nm.createNotificationChannel(backupChannel)
            nm.createNotificationChannel(reminderChannel)
            nm.createNotificationChannel(exportChannel)
        }
    }

    fun showNotification(
        context: Context,
        channelId: String,
        title: String,
        message: String,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        createChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, piFlags)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, builder.build())
    }
}
