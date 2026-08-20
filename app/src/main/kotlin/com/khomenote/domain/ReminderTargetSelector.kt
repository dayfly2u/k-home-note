package com.khomenote.domain

import java.time.LocalDate

data class ReminderCandidate(
    val consumableItemId: Long,
    val nextDueDate: LocalDate,
    val reminderLeadDays: Int,
    val lastNotifiedForDate: LocalDate? = null,
    val lastNotifiedMilestoneDays: Int? = null,
)

data class ReminderTarget(
    val consumableItemId: Long,
    val nextDueDate: LocalDate,
    val milestoneDays: Int,
)

object ReminderTargetSelector {
    /**
     * leadDays를 절반씩 줄여가며 알림 시점을 여러 개 만든다.
     * 예: 14 -> [14, 7, 3, 1]. leadDays가 0 이하면 당일([0])만 알린다.
     */
    fun reminderMilestones(leadDays: Int): List<Int> {
        if (leadDays <= 0) return listOf(0)
        val milestones = mutableListOf<Int>()
        var d = leadDays
        while (d >= 1) {
            milestones.add(d)
            d /= 2
        }
        return milestones
    }

    /**
     * 각 후보에 대해 "지금 보내야 할, 아직 안 보낸 시점 중 가장 임박한 것" 하나만 고른다.
     * 한 번에 여러 시점을 몰아서 보내는 대신(스팸 방지), 확인할 때마다 딱 하나만 알린다 —
     * 다음 날 다시 확인하면 그다음으로 임박한 시점이 또 하나 걸린다.
     */
    fun selectTargets(candidates: List<ReminderCandidate>, today: LocalDate): List<ReminderTarget> =
        candidates.mapNotNull { candidate ->
            val daysLeft = DueDateCalculator.daysUntilDue(candidate.nextDueDate, today)
            // 마감일이 바뀌었다면(다음 교체 주기로 넘어갔다면) 이전 주기의 알림 기록은 더 이상 유효하지 않다.
            val lastMilestone = if (candidate.lastNotifiedForDate == candidate.nextDueDate) {
                candidate.lastNotifiedMilestoneDays
            } else {
                null
            }
            val milestone = reminderMilestones(candidate.reminderLeadDays)
                .filter { daysLeft <= it && (lastMilestone == null || it < lastMilestone) }
                .minOrNull()
            milestone?.let { ReminderTarget(candidate.consumableItemId, candidate.nextDueDate, it) }
        }
}
