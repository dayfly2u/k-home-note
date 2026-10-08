package com.khomenote.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.khomenote.di.AppContainer
import com.khomenote.domain.DueDateCalculator
import com.khomenote.domain.ReminderCandidate
import com.khomenote.domain.ReminderTargetSelector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private const val TAG = "ReminderWorker"

class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = try {
            runReminderCheck()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reminder check failed", e)
            Result.failure()
        }
        // 한 번 실패했다고 다음 날 스케줄 자체가 끊기면 안 되므로, 성공/실패와 무관하게
        // 항상 다음 실행을 예약한다 — WorkManager 기본 재시도 정책(Result.retry())에 맡기면
        // 재시도 간격이 "다음 날 08:00"이라는 요구사항과 안 맞는다.
        ReminderScheduler.scheduleNext(applicationContext)
        return result
    }

    private suspend fun runReminderCheck() {
        val container = AppContainer.getInstance(applicationContext)
        val today = LocalDate.now()

        checkConsumableItems(container, today)
        checkOneTimeTasks(container, today)
    }

    private suspend fun checkConsumableItems(container: AppContainer, today: LocalDate) {
        val itemsWithStatus = container.consumableItemRepository.getAllWithStatus().first()

        val candidates = itemsWithStatus.map { status ->
            ReminderCandidate(
                consumableItemId = status.item.id,
                nextDueDate = DueDateCalculator.nextDueDate(
                    itemCreatedAt = status.item.createdAt,
                    cycleDays = status.item.cycleDays,
                    latestRecordDate = status.latestRecordDate,
                    monthlyDueDay = status.item.monthlyDueDay,
                    yearlyDueMonth = status.item.yearlyDueMonth,
                    yearlyDueDay = status.item.yearlyDueDay,
                    yearlyDueIsLunar = status.item.isYearlyLunar == true,
                ),
                reminderLeadDays = status.item.reminderLeadDays,
                lastNotifiedForDate = status.item.lastNotifiedForDate,
                lastNotifiedMilestoneDays = status.item.lastNotifiedMilestoneDays,
            )
        }

        val targets = ReminderTargetSelector.selectTargets(candidates, today)
        targets.forEach { target ->
            val status = itemsWithStatus.first { it.item.id == target.consumableItemId }
            val daysLeft = ChronoUnit.DAYS.between(today, target.nextDueDate)
            val contentText = if (daysLeft <= 0) "오늘이 교체일이에요" else "${daysLeft}일 후 교체 예정이에요"
            val posted = showReminderNotification(
                context = applicationContext,
                consumableItemId = target.consumableItemId,
                title = "${status.assetName} · ${status.item.name}",
                contentText = contentText,
            )
            if (posted) {
                container.consumableItemRepository.save(
                    status.item.copy(
                        lastNotifiedForDate = target.nextDueDate,
                        lastNotifiedMilestoneDays = target.milestoneDays,
                    )
                )
            }
        }
    }

    // 일회성작업(repair_records)은 알림 받기를 켠 것만 대상 — 소모품과 달리 다음 주기로 안 넘어가고
    // 날짜가 고정이라, DueDateCalculator 없이 record.date를 그대로 nextDueDate로 쓴다.
    private suspend fun checkOneTimeTasks(container: AppContainer, today: LocalDate) {
        val pending = container.repairRecordRepository.getPendingReminders().first()

        val candidates = pending.map { withAsset ->
            ReminderCandidate(
                consumableItemId = withAsset.record.id,
                nextDueDate = withAsset.record.date,
                reminderLeadDays = withAsset.record.reminderLeadDays ?: 0,
                lastNotifiedForDate = withAsset.record.lastNotifiedForDate,
                lastNotifiedMilestoneDays = withAsset.record.lastNotifiedMilestoneDays,
            )
        }

        val targets = ReminderTargetSelector.selectTargets(candidates, today)
        targets.forEach { target ->
            val withAsset = pending.first { it.record.id == target.consumableItemId }
            val daysLeft = ChronoUnit.DAYS.between(today, target.nextDueDate)
            val contentText = if (daysLeft <= 0) "오늘 예정된 일이에요" else "${daysLeft}일 후 예정된 일이에요"
            val posted = showOneTimeTaskReminderNotification(
                context = applicationContext,
                repairRecordId = withAsset.record.id,
                assetId = withAsset.record.assetId,
                title = "${withAsset.assetName} · ${withAsset.record.name}",
                contentText = contentText,
            )
            if (posted) {
                container.repairRecordRepository.save(
                    withAsset.record.copy(
                        lastNotifiedForDate = target.nextDueDate,
                        lastNotifiedMilestoneDays = target.milestoneDays,
                    )
                )
            }
        }
    }
}
