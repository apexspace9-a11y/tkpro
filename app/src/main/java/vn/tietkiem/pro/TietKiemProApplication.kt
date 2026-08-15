package vn.tietkiem.pro

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import vn.tietkiem.pro.data.AppDatabase
import vn.tietkiem.pro.data.BackupManager
import vn.tietkiem.pro.data.FinanceRepository
import vn.tietkiem.pro.data.SettingsRepository
import vn.tietkiem.pro.worker.FinanceNotifications
import vn.tietkiem.pro.worker.FinancialAlertWorker
import vn.tietkiem.pro.worker.RecurringWorker
import java.util.concurrent.TimeUnit

class TietKiemProApplication : Application() {
    val database by lazy { AppDatabase.get(this) }
    val financeRepository by lazy { FinanceRepository(database) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val backupManager by lazy { BackupManager(this, database) }

    override fun onCreate() {
        super.onCreate()
        FinanceNotifications.createChannel(this)

        val recurringRequest = PeriodicWorkRequestBuilder<RecurringWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "recurring-finance",
            ExistingPeriodicWorkPolicy.UPDATE,
            recurringRequest
        )

        val alertRequest = PeriodicWorkRequestBuilder<FinancialAlertWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "financial-alerts-v3",
            ExistingPeriodicWorkPolicy.UPDATE,
            alertRequest
        )
    }
}
