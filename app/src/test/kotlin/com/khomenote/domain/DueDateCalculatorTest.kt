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
    fun `nextDueDate with monthly fixed day returns this month's date when no record exists yet and it is still ahead`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 9, 3),
            cycleDays = 30,
            latestRecordDate = null,
            monthlyDueDay = 25,
        )
        assertEquals(LocalDate.of(2026, 9, 25), result)
    }

    @Test
    fun `nextDueDate with monthly fixed day advances to next month once a record exists, even if this month's date has not passed yet`() {
        // 25일 결제일인데 20일에 미리 결제 기록을 남긴 경우 — 이번 달을 이미 처리한 것으로 보고
        // 다음 달 25일로 넘어가야 한다("아직 지나지 않았어도 이력이 생기면 리셋").
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 1, 1),
            cycleDays = 30,
            latestRecordDate = LocalDate.of(2026, 9, 20),
            monthlyDueDay = 25,
        )
        assertEquals(LocalDate.of(2026, 10, 25), result)
    }

    @Test
    fun `nextDueDate with monthly fixed day still advances to next month when the record is after this month's date too`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 1, 1),
            cycleDays = 30,
            latestRecordDate = LocalDate.of(2026, 9, 28),
            monthlyDueDay = 25,
        )
        assertEquals(LocalDate.of(2026, 10, 25), result)
    }

    @Test
    fun `nextDueDate with yearly fixed date returns this year's date when it is still ahead`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2027, 1, 1),
            cycleDays = 30,
            latestRecordDate = null,
            yearlyDueMonth = 5,
            yearlyDueDay = 20,
        )
        assertEquals(LocalDate.of(2027, 5, 20), result)
    }

    @Test
    fun `nextDueDate with yearly fixed date rolls over to next year once this year's date has passed`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2027, 1, 1),
            cycleDays = 30,
            latestRecordDate = LocalDate.of(2027, 6, 1),
            yearlyDueMonth = 5,
            yearlyDueDay = 20,
        )
        assertEquals(LocalDate.of(2028, 5, 20), result)
    }

    @Test
    fun `nextDueDate with yearly fixed date advances to next year once a record exists, even if this year's date has not passed yet`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 1, 1),
            cycleDays = 30,
            latestRecordDate = LocalDate.of(2027, 3, 1),
            yearlyDueMonth = 5,
            yearlyDueDay = 20,
        )
        assertEquals(LocalDate.of(2028, 5, 20), result)
    }

    @Test
    fun `nextDueDate with yearly Feb 29 clamps to Feb 28 in a non-leap year`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2027, 1, 1),
            cycleDays = 30,
            latestRecordDate = null,
            yearlyDueMonth = 2,
            yearlyDueDay = 29,
        )
        assertEquals(LocalDate.of(2027, 2, 28), result)
    }

    @Test
    fun `nextDueDate with yearly Feb 29 rolls forward to the next leap year once clamped date has passed`() {
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2027, 1, 1),
            cycleDays = 30,
            latestRecordDate = LocalDate.of(2027, 3, 1),
            yearlyDueMonth = 2,
            yearlyDueDay = 29,
        )
        assertEquals(LocalDate.of(2028, 2, 29), result)
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
