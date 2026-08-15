package com.khomenote.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.khomenote.di.AppContainer
import com.khomenote.domain.DueDateCalculator
import com.khomenote.domain.ReminderCandidate
import com.khomenote.domain.ReminderTargetSelector
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val container = AppContainer.getInstance(applicationContext)
            val itemsWithStatus = container.consumableItemRepository.getAllWithStatus().first()
            val today = LocalDate.now()

            val candidates = itemsWithStatus.map { status ->
                ReminderCandidate(
                    consumableItemId = status.item.id,
                    nextDueDate = DueDateCalculator.nextDueDate(
                        itemCreatedAt = status.item.createdAt,
                        cycleDays = status.item.cycleDays,
                        latestRecordDate = status.latestRecordDate,
                    ),
                    reminderLeadDays = status.item.reminderLeadDays,
                    lastNotifiedForDate = status.item.lastNotifiedForDate,
                )
            }

            val targets = ReminderTargetSelector.selectTargets(candidates, today)
            targets.forEach { target ->
                val status = itemsWithStatus.first { it.item.id == target.consumableItemId }
                val daysLeft = ChronoUnit.DAYS.between(today, target.nextDueDate)
                val contentText = if (daysLeft <= 0) "오늘이 교체일이에요" else "${daysLeft}일 후 교체 예정이에요"
                showReminderNotification(
                    context = applicationContext,
                    consumableItemId = target.consumableItemId,
                    title = "${status.assetName} · ${status.item.name}",
                    contentText = contentText,
                )
                container.consumableItemRepository.save(status.item.copy(lastNotifiedForDate = target.nextDueDate))
            }
            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }
}
