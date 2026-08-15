package com.khomenote.domain

import java.time.LocalDate

data class ReminderCandidate(
    val consumableItemId: Long,
    val nextDueDate: LocalDate,
    val reminderLeadDays: Int,
    val lastNotifiedForDate: LocalDate? = null,
)

object ReminderTargetSelector {
    fun selectTargets(candidates: List<ReminderCandidate>, today: LocalDate): List<ReminderCandidate> =
        candidates.filter { candidate ->
            val daysLeft = DueDateCalculator.daysUntilDue(candidate.nextDueDate, today)
            daysLeft in 0..candidate.reminderLeadDays.toLong() && candidate.lastNotifiedForDate != candidate.nextDueDate
        }
}
