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
            lastNotifiedForDate = null,
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
            lastNotifiedForDate = null,
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
            lastNotifiedForDate = null,
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
            lastNotifiedForDate = null,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(emptyList<ReminderCandidate>(), result)
    }

    @Test
    fun `excludes an already-overdue item even one day past due`() {
        val candidate = ReminderCandidate(
            consumableItemId = 5L,
            nextDueDate = LocalDate.of(2026, 8, 14),
            reminderLeadDays = 7,
            lastNotifiedForDate = null,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(emptyList<ReminderCandidate>(), result)
    }

    @Test
    fun `with zero lead days excludes an item that is one day out`() {
        val candidate = ReminderCandidate(
            consumableItemId = 6L,
            nextDueDate = LocalDate.of(2026, 8, 16),
            reminderLeadDays = 0,
            lastNotifiedForDate = null,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(emptyList<ReminderCandidate>(), result)
    }

    @Test
    fun `with zero lead days selects an item due exactly today`() {
        val candidate = ReminderCandidate(
            consumableItemId = 7L,
            nextDueDate = LocalDate.of(2026, 8, 15),
            reminderLeadDays = 0,
            lastNotifiedForDate = null,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(candidate), result)
    }

    @Test
    fun `selects item within the widened lead window even if not on the exact lead day`() {
        val candidate = ReminderCandidate(
            consumableItemId = 8L,
            nextDueDate = LocalDate.of(2026, 8, 18),
            reminderLeadDays = 7,
            lastNotifiedForDate = null,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(candidate), result)
    }

    @Test
    fun `excludes item already notified for this exact due date`() {
        val nextDueDate = LocalDate.of(2026, 8, 18)
        val candidate = ReminderCandidate(
            consumableItemId = 9L,
            nextDueDate = nextDueDate,
            reminderLeadDays = 7,
            lastNotifiedForDate = nextDueDate,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(emptyList<ReminderCandidate>(), result)
    }

    @Test
    fun `re-selects item within window when last notification was for a different (earlier) cycle`() {
        val nextDueDate = LocalDate.of(2026, 8, 18)
        val candidate = ReminderCandidate(
            consumableItemId = 10L,
            nextDueDate = nextDueDate,
            reminderLeadDays = 7,
            lastNotifiedForDate = LocalDate.of(2026, 7, 1),
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(candidate), result)
    }
}
