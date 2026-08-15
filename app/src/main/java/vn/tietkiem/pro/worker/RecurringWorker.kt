package vn.tietkiem.pro.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import vn.tietkiem.pro.data.AppDatabase
import vn.tietkiem.pro.data.FinanceRepository

class RecurringWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = try {
        FinanceRepository(AppDatabase.get(applicationContext)).postDueRecurring()
        Result.success()
    } catch (_: Throwable) {
        Result.retry()
    }
}
