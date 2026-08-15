package vn.tietkiem.pro.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import vn.tietkiem.pro.R
import vn.tietkiem.pro.data.AppDatabase
import vn.tietkiem.pro.data.DebtType
import vn.tietkiem.pro.data.SettingsRepository
import vn.tietkiem.pro.data.TransactionType
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

object FinanceNotifications {
    const val CHANNEL_ID = "finance_alerts_v4"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Cảnh báo tài chính",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Ngân sách, nợ, mục tiêu, đồng bộ và lịch tài chính"
                }
            )
        }
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    fun sendTest(context: Context): Boolean {
        if (!canNotify(context)) return false
        createChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tiết Kiệm Pro V4")
            .setContentText("Thông báo thử nghiệm hoạt động bình thường")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Thông báo thử nghiệm hoạt động bình thường. Cảnh báo tài chính sẽ xuất hiện qua kênh này."))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(4004, notification)
        return true
    }
}

class FinancialAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext).settings.first()
        if (!settings.notificationsEnabled || !FinanceNotifications.canNotify(applicationContext)) return Result.success()

        val dao = AppDatabase.get(applicationContext).financeDao()
        val now = System.currentTimeMillis()
        val messages = mutableListOf<String>()

        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(now)
        val budgets = dao.getBudgets().filter { it.monthKey == monthKey }
        val txs = dao.getTransactions()
        if (budgets.isNotEmpty()) {
            val budgetIds = budgets.map { it.categoryId }.toSet()
            val spent = txs.filter {
                it.type == TransactionType.EXPENSE.name &&
                    it.categoryId in budgetIds &&
                    SimpleDateFormat("yyyy-MM", Locale.US).format(it.occurredAt) == monthKey
            }.sumOf { it.amount }
            val limit = budgets.sumOf { it.limitAmount }
            if (limit > 0 && spent >= limit) messages += "Ngân sách tháng đã vượt hạn mức"
            else if (limit > 0 && spent * 100 / limit >= 80) messages += "Ngân sách tháng đã dùng từ 80%"
        }

        val overdueDebt = dao.getDebts().count {
            it.type == DebtType.PAYABLE.name && it.remainingAmount > 0 && it.dueDate != null && it.dueDate < now
        }
        if (overdueDebt > 0) messages += "$overdueDebt khoản nợ đã quá hạn"

        val sevenDays = now + TimeUnit.DAYS.toMillis(7)
        val goalsDue = dao.getGoals().count {
            it.savedAmount < it.targetAmount && it.deadline != null && it.deadline in now..sevenDays
        }
        if (goalsDue > 0) messages += "$goalsDue mục tiêu sắp đến hạn"

        val recurringSoon = dao.getRecurring().count {
            it.active && it.nextDueAt in now..(now + TimeUnit.HOURS.toMillis(24))
        }
        if (recurringSoon > 0) messages += "$recurringSoon giao dịch định kỳ trong 24 giờ tới"

        if (messages.isEmpty()) return Result.success()

        FinanceNotifications.createChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, FinanceNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tiết Kiệm Pro")
            .setContentText(messages.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(messages.joinToString("\n")))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(3003, notification)
        return Result.success()
    }
}
