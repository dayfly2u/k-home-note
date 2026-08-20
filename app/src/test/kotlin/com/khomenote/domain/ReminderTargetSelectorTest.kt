package com.khomenote.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReminderTargetSelectorTest {

    private val today = LocalDate.of(2026, 8, 15)

    @Test
    fun `reminderMilestones halves the lead time down to 1`() {
        assertEquals(listOf(14, 7, 3, 1), ReminderTargetSelector.reminderMilestones(14))
        assertEquals(listOf(7, 3, 1), ReminderTargetSelector.reminderMilestones(7))
        assertEquals(listOf(2, 1), ReminderTargetSelector.reminderMilestones(2))
        assertEquals(listOf(1), ReminderTargetSelector.reminderMilestones(1))
        assertEquals(listOf(0), ReminderTargetSelector.reminderMilestones(0))
    }

    @Test
    fun `fires the largest milestone the first time it is crossed`() {
        // reminderLeadDays=14 -> milestones [14,7,3,1]; 7 days left crosses the 14-day milestone too,
        // but the tightest (smallest) one that's already been reached should fire: min(14,7) = 7.
        val candidate = ReminderCandidate(
            consumableItemId = 1L,
            nextDueDate = LocalDate.of(2026, 8, 22),
            reminderLeadDays = 14,
            lastNotifiedForDate = null,
            lastNotifiedMilestoneDays = null,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(ReminderTarget(1L, candidate.nextDueDate, 7)), result)
    }

    @Test
    fun `does not re-fire a milestone already notified this cycle`() {
        val nextDueDate = LocalDate.of(2026, 8, 22)
        val candidate = ReminderCandidate(
            consumableItemId = 2L,
            nextDueDate = nextDueDate,
            reminderLeadDays = 14,
            lastNotifiedForDate = nextDueDate,
            lastNotifiedMilestoneDays = 7,
        )
        // still 7 days left, same as last notified milestone -> nothing new to fire
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(emptyList<ReminderTarget>(), result)
    }

    @Test
    fun `fires the next smaller milestone once time advances past it`() {
        val nextDueDate = LocalDate.of(2026, 8, 18)
        val candidate = ReminderCandidate(
            consumableItemId = 3L,
            nextDueDate = nextDueDate,
            reminderLeadDays = 14,
            lastNotifiedForDate = nextDueDate,
            lastNotifiedMilestoneDays = 7,
        )
        // 3 days left now -> next unfired milestone (3) should fire
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(ReminderTarget(3L, nextDueDate, 3)), result)
    }

    @Test
    fun `resets milestone history when the due date cycle changes`() {
        val nextDueDate = LocalDate.of(2026, 8, 22)
        val candidate = ReminderCandidate(
            consumableItemId = 4L,
            nextDueDate = nextDueDate,
            reminderLeadDays = 14,
            lastNotifiedForDate = LocalDate.of(2026, 7, 1), // an earlier, now-irrelevant cycle
            lastNotifiedMilestoneDays = 1,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(ReminderTarget(4L, nextDueDate, 7)), result)
    }

    @Test
    fun `excludes item far outside its lead window`() {
        val candidate = ReminderCandidate(
            consumableItemId = 5L,
            nextDueDate = LocalDate.of(2026, 9, 1),
            reminderLeadDays = 7,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(emptyList<ReminderTarget>(), result)
    }

    @Test
    fun `catches up to the tightest unfired milestone after being overdue`() {
        val nextDueDate = LocalDate.of(2026, 8, 1)
        val candidate = ReminderCandidate(
            consumableItemId = 6L,
            nextDueDate = nextDueDate,
            reminderLeadDays = 14,
            lastNotifiedForDate = null,
            lastNotifiedMilestoneDays = null,
        )
        // 14 days overdue, never notified -> fires the smallest (most urgent) milestone, not a spam of all of them
        val result = ReminderTargetSelector.selectTargets(listOf(candidate), today)
        assertEquals(listOf(ReminderTarget(6L, nextDueDate, 1)), result)
    }

    @Test
    fun `zero lead days only fires on the due day itself`() {
        val dueToday = ReminderCandidate(
            consumableItemId = 7L,
            nextDueDate = today,
            reminderLeadDays = 0,
        )
        val dueTomorrow = ReminderCandidate(
            consumableItemId = 8L,
            nextDueDate = today.plusDays(1),
            reminderLeadDays = 0,
        )
        val result = ReminderTargetSelector.selectTargets(listOf(dueToday, dueTomorrow), today)
        assertEquals(listOf(ReminderTarget(7L, today, 0)), result)
    }
}
