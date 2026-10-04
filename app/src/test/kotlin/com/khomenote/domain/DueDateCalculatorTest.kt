package com.khomenote.domain

import com.github.usingsky.calendar.KoreanLunarCalendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// 라이브러리 자체(음력<->양력 환산 정확성)를 다시 검증하는 게 아니라, DueDateCalculator가
// forceNextPeriod/연도 탐색을 올바르게 배선했는지 확인하는 것이 목적이라, 기대값도
// 같은 라이브러리로 직접 계산한다.
private fun solarFor(lunarYear: Int, lunarMonth: Int, lunarDay: Int): LocalDate {
    val calendar = KoreanLunarCalendar.getInstance()
    check(calendar.setLunarDate(lunarYear, lunarMonth, lunarDay, false))
    return LocalDate.of(calendar.solarYear, calendar.solarMonth, calendar.solarDay)
}

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
    fun `nextDueDate with monthly fixed day only advances one cycle when processed over a month late`() {
        // 실사용 버그: 9/30 지정일을 놓쳐서 10/4에 처리한 경우, 다음 예정일은 10/30이어야 하는데
        // 예전 코드는 base(10/4)의 다음 달(11월)로 넘어가 11/30을 반환했다(한 주기를 더 건너뜀).
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 1, 1),
            cycleDays = 30,
            latestRecordDate = LocalDate.of(2026, 10, 4),
            monthlyDueDay = 30,
        )
        assertEquals(LocalDate.of(2026, 10, 30), result)
    }

    @Test
    fun `nextDueDate with monthly fixed day advances one cycle the same way when processed a few days early`() {
        // 위 케이스와 같은 지정일(30일)을 이번엔 지정일보다 며칠 일찍(9/25) 처리한 경우에도
        // 똑같이 가장 가까운 지정일(9/30)을 처리한 것으로 보고 다음 예정일은 10/30이어야 한다.
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 1, 1),
            cycleDays = 30,
            latestRecordDate = LocalDate.of(2026, 9, 25),
            monthlyDueDay = 30,
        )
        assertEquals(LocalDate.of(2026, 10, 30), result)
    }

    @Test
    fun `nextDueDate with yearly fixed date only advances one cycle when processing crosses into the next calendar year`() {
        // 지정일(12/30)을 놓쳐서 다음 해 1/4에 처리한 경우 — base의 "연도"만 보고 base.year+1로
        // 넘기면 2028년이 되어버리지만, 실제로 처리한 건 2026/12/30 하나뿐이므로 2027/12/30이어야 한다.
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2020, 1, 1),
            cycleDays = 30,
            latestRecordDate = LocalDate.of(2027, 1, 4),
            yearlyDueMonth = 12,
            yearlyDueDay = 30,
        )
        assertEquals(LocalDate.of(2027, 12, 30), result)
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
    fun `nextDueDate with lunar yearly date returns this year's solar date when no record exists and it is still ahead`() {
        // 음력 3월 15일을 아주 이른 base(그 해 1월 1일)에서 찾으면 아직 안 지났으니 올해 그대로.
        val expected = solarFor(2027, 3, 15)
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2027, 1, 1),
            cycleDays = 30,
            latestRecordDate = null,
            yearlyDueMonth = 3,
            yearlyDueDay = 15,
            yearlyDueIsLunar = true,
        )
        assertEquals(expected, result)
    }

    @Test
    fun `nextDueDate with lunar yearly date rolls over to next lunar year once this year's solar date has passed`() {
        val thisYear = solarFor(2027, 3, 15)
        val nextYear = solarFor(2028, 3, 15)
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2027, 1, 1),
            cycleDays = 30,
            latestRecordDate = thisYear.plusDays(1),
            yearlyDueMonth = 3,
            yearlyDueDay = 15,
            yearlyDueIsLunar = true,
        )
        assertEquals(nextYear, result)
    }

    @Test
    fun `nextDueDate with lunar yearly date advances to next lunar year once a record exists, even if this year's date has not passed yet`() {
        val expected = solarFor(2028, 3, 15)
        val result = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2026, 1, 1),
            cycleDays = 30,
            // 올해(2027) 음력 3월 15일의 훨씬 이전에 미리 이력을 남긴 경우.
            latestRecordDate = LocalDate.of(2027, 1, 5),
            yearlyDueMonth = 3,
            yearlyDueDay = 15,
            yearlyDueIsLunar = true,
        )
        assertEquals(expected, result)
    }

    @Test
    fun `nextDueDate with lunar yearly date differs from treating the same month day as solar`() {
        // 음력과 양력을 같은 월/일로 착각해서 그냥 양력 계산 경로로 흘러들어가지 않는지 확인.
        val lunarResult = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2027, 1, 1),
            cycleDays = 30,
            latestRecordDate = null,
            yearlyDueMonth = 8,
            yearlyDueDay = 15,
            yearlyDueIsLunar = true,
        )
        val solarResult = DueDateCalculator.nextDueDate(
            itemCreatedAt = LocalDate.of(2027, 1, 1),
            cycleDays = 30,
            latestRecordDate = null,
            yearlyDueMonth = 8,
            yearlyDueDay = 15,
            yearlyDueIsLunar = false,
        )
        assertTrue(lunarResult != solarResult)
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
