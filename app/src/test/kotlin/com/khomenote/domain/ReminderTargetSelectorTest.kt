package com.khomenote.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReminderTargetSelectorTest {

    private val today = LocalDate.of(2026, 8, 15)

    @Test
    fun `selects item exactly at its reminder lead time`() {
        val candidate = ReminderCandidate(
            consumableItemId = 1L,
            nextDueDate = LocalDate.of(2026, 8, 22),
            reminderLeadDays = 7,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(candidate), result)
    }

    @Test
    fun `selects item due exactly today regardless of lead days`() {
        val candidate = ReminderCandidate(
            consumableItemId = 2L,
            nextDueDate = LocalDate.of(2026, 8, 15),
            reminderLeadDays = 3,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(candidate), result)
    }

    @Test
    fun `excludes item that is neither at lead time nor due today`() {
        val candidate = ReminderCandidate(
            consumableItemId = 3L,
            nextDueDate = LocalDate.of(2026, 9, 1),
            reminderLeadDays = 7,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(emptyList<ReminderCandidate>(), result)
    }

    @Test
    fun `does not re-notify for an item that is already overdue by more than the lead time`() {
        val candidate = ReminderCandidate(
            consumableItemId = 4L,
            nextDueDate = LocalDate.of(2026, 8, 1),
            reminderLeadDays = 7,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(emptyList<ReminderCandidate>(), result)
    }
}
