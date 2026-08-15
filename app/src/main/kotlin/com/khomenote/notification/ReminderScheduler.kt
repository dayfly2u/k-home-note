package com.khomenote.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

private const val REMINDER_WORK_NAME = "reminder_daily_check"

fun scheduleDailyReminderCheck(context: Context) {
    val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        REMINDER_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
