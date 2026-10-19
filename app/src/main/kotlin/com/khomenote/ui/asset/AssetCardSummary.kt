package com.khomenote.ui.asset

import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.RepairRecordEntity
import com.khomenote.domain.DueDateCalculator
import java.time.LocalDate

/** 모아보기 카드에 보여줄 "가장 가까운 작업" — 홈의 D-day 배지와 같은 기준으로 그리려고 알림 일수도 함께 든다. */
internal data class NearestTask(val name: String, val daysUntilDue: Long, val reminderLeadDays: Int)

internal data class AssetCardSummary(val recurringCount: Int, val oneTimeCount: Int, val nearest: NearestTask?) {
    companion object {
        val EMPTY = AssetCardSummary(0, 0, null)
    }
}

/**
 * 대상 하나의 카드 요약. 주기성작업은 전부, 일회성작업은 알림이 켜진(= 아직 안 끝난) 것만 "가장 가까운 작업" 후보가 된다.
 * 개수는 일회성작업 기록 전체(끝난 것 포함)를 센다.
 */
internal fun summarizeAsset(
    assetId: Long,
    statuses: List<ConsumableItemWithStatus>,
    records: List<RepairRecordEntity>,
    today: LocalDate,
): AssetCardSummary {
    val recurring = statuses.filter { it.item.assetId == assetId }
    val oneTime = records.filter { it.assetId == assetId }

    val recurringCandidates = recurring.map { status ->
        val item = status.item
        val nextDue = DueDateCalculator.nextDueDate(
            itemCreatedAt = item.createdAt,
            cycleDays = item.cycleDays,
            latestRecordDate = status.latestRecordDate,
            monthlyDueDay = item.monthlyDueDay,
            yearlyDueMonth = item.yearlyDueMonth,
            yearlyDueDay = item.yearlyDueDay,
            yearlyDueIsLunar = item.isYearlyLunar == true,
        )
        NearestTask(item.name, DueDateCalculator.daysUntilDue(nextDue, today), item.reminderLeadDays)
    }
    val oneTimeCandidates = oneTime.mapNotNull { record ->
        record.reminderLeadDays?.let { lead ->
            NearestTask(record.name, DueDateCalculator.daysUntilDue(record.date, today), lead)
        }
    }

    return AssetCardSummary(
        recurringCount = recurring.size,
        oneTimeCount = oneTime.size,
        nearest = (recurringCandidates + oneTimeCandidates).minByOrNull { it.daysUntilDue },
    )
}

/** "주기성 4 · 일회성 3" — 0개인 쪽은 생략하고, 둘 다 없으면 "작업 없음". */
internal fun cardCountsLabel(summary: AssetCardSummary): String {
    val parts = buildList {
        if (summary.recurringCount > 0) add("주기성 ${summary.recurringCount}")
        if (summary.oneTimeCount > 0) add("일회성 ${summary.oneTimeCount}")
    }
    return if (parts.isEmpty()) "작업 없음" else parts.joinToString(" · ")
}
