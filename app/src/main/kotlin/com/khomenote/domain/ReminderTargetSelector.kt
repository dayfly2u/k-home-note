package com.khomenote.domain

import java.time.LocalDate

data class ReminderCandidate(
    val consumableItemId: Long,
    val nextDueDate: LocalDate,
    val reminderLeadDays: Int,
)

object ReminderTargetSelector {
    fun selectTargets(candidates: List<ReminderCandidate>, today: LocalDate): List<ReminderCandidate> =
        candidates.filter { candidate ->
            val daysLeft = DueDateCalculator.daysUntilDue(candidate.nextDueDate, today)
            daysLeft == candidate.reminderLeadDays.toLong() || daysLeft == 0L
        }
}
