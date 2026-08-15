package vn.tietkiem.pro.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import vn.tietkiem.pro.data.AppDatabase
import vn.tietkiem.pro.data.BackupManager
import vn.tietkiem.pro.data.FinanceRepository
import vn.tietkiem.pro.data.SettingsRepository
import vn.tietkiem.pro.online.CloudSyncManager

class RecurringWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        val db = AppDatabase.get(applicationContext)
        val repo = FinanceRepository(db)
        val settings = SettingsRepository(applicationContext)
        val posted = repo.postDueRecurring()
        if (posted > 0) {
            settings.setCloudDirty(true)
            val cloud = CloudSyncManager(settings, BackupManager(applicationContext, db))
            val session = cloud.restoreSession()
            if (session == null) return Result.retry()
        }
        Result.success()
    } catch (_: Throwable) {
        Result.retry()
    }
}
