package com.fastlink.accounts.worker

import android.content.Context
import androidx.work.*
import com.fastlink.accounts.data.FastLinkDatabase
import com.fastlink.accounts.util.FormatUtils
import com.fastlink.accounts.util.NotificationHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = FastLinkDatabase.getInstance(applicationContext)
        val dao = db.dao()

        val currency = dao.getSettingValue("currency") ?: "ريال"
        val notifyDailyDebts = (dao.getSettingValue("notify_daily_debts") ?: "true") == "true"
        val notifyWeeklyExpenses = (dao.getSettingValue("notify_weekly_expenses") ?: "true") == "true"

        if (notifyDailyDebts) {
            val totalSales = dao.getTotalSalesAmount()
            val totalPayments = dao.getTotalPaymentsAmount()
            val totalDebt = totalSales - totalPayments
            if (totalDebt > 0.01) {
                NotificationHelper.showNotification(
                    applicationContext,
                    NotificationHelper.CHANNEL_REMINDERS,
                    "تذكير يومي بالديون المستحقة",
                    "إجمالي الديون المستحقة لدى المحلات حالياً: ${FormatUtils.formatAmount(totalDebt, currency)}"
                )
            }
        }

        val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (notifyWeeklyExpenses && dayOfWeek == Calendar.SATURDAY) {
            val pendingExpenses = dao.getTotalPendingExpensesAmount()
            if (pendingExpenses > 0.01) {
                NotificationHelper.showNotification(
                    applicationContext,
                    NotificationHelper.CHANNEL_REMINDERS,
                    "تذكير أسبوعي بالمصروفات المعلقة",
                    "يوجد مصروفات معلقة غير مسددة بقيمة: ${FormatUtils.formatAmount(pendingExpenses, currency)}"
                )
            }
        }

        return Result.success()
    }

    companion object {
        private const val REMINDER_WORK_NAME = "fastlink_daily_reminder_work"

        fun scheduleDailyReminders(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                REMINDER_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
