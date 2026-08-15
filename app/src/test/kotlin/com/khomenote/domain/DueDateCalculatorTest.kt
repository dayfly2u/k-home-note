package com.khomenote.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DueDateCalculatorTest {

    @Test
    fun `nextDueDate uses latest record date plus cycle when a record exists`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 1, 1),
            cycleDays = 180,
            latestRecordDate = LocalDate.of(2026, 6, 1),
        )
        assertEquals(LocalDate.of(2026, 11, 28), result)
    }

    @Test
    fun `nextDueDate falls back to item createdAt when there is no record yet`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 1, 1),
            cycleDays = 30,
            latestRecordDate = null,
        )
        assertEquals(LocalDate.of(2026, 1, 31), result)
    }

    @Test
    fun `daysUntilDue is positive when due date is in the future`() {
        val result = DueDateCalculator.daysUntilDue(
            nextDueDate = LocalDate.of(2026, 8, 20),
            today = LocalDate.of(2026, 8, 15),
        )
        assertEquals(5L, result)
    }

    @Test
    fun `daysUntilDue is negative when due date already passed`() {
        val result = DueDateCalculator.daysUntilDue(
            nextDueDate = LocalDate.of(2026, 8, 10),
            today = LocalDate.of(2026, 8, 15),
        )
        assertEquals(-5L, result)
    }
}
