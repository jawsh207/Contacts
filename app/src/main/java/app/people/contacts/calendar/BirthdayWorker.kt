package app.people.contacts.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.people.contacts.data.Settings
import java.util.concurrent.TimeUnit

/** Refreshes the birthday calendar once a day while it's turned on, so ages and years stay right. */
class BirthdayWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Settings(applicationContext).birthdayCalendarNow()) BirthdayCalendar.sync(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "birthday-calendar"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BirthdayWorker>(1, TimeUnit.DAYS).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
