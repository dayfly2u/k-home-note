package com.khomenote.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

// HomeRowUiModel은 sealed interface라 테스트 소스셋에서 가짜 구현체를 만들 수 없다(다른 모듈 취급).
// 그래서 개수 계산 로직은 DueBand 목록을 받는 internal 함수(toSummaryCounts)로 검증한다.
class HomeSummaryCountsTest {

    @Test
    fun `toSummaryCounts counts each band separately`() {
        val bands = listOf(
            dueBandFor(-1, 7),   // OVERDUE
            dueBandFor(0, 7),    // DUE_SOON
            dueBandFor(5, 7),    // DUE_SOON
            dueBandFor(20, 7),   // WITHIN_MONTH
            dueBandFor(60, 7),   // WITHIN_3_MONTHS
        )
        val counts = bands.toSummaryCounts()
        assertEquals(1, counts.overdue)
        assertEquals(2, counts.dueSoon)
        assertEquals(1, counts.withinMonth)
    }

    @Test
    fun `summaryCounts is all zero for an empty list`() {
        val counts = emptyList<HomeRowUiModel>().summaryCounts()
        assertEquals(HomeSummaryCounts(0, 0, 0), counts)
    }
}

class HomeSummaryTest {

    @Test
    fun `dueBandFor returns OVERDUE when days until due is negative`() {
        assertEquals(DueBand.OVERDUE, dueBandFor(daysUntilDue = -1, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns DUE_SOON on the due day itself`() {
        assertEquals(DueBand.DUE_SOON, dueBandFor(daysUntilDue = 0, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns DUE_SOON when within the item's own reminder lead days`() {
        assertEquals(DueBand.DUE_SOON, dueBandFor(daysUntilDue = 7, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns WITHIN_MONTH just past the reminder lead days but within 31 days`() {
        assertEquals(DueBand.WITHIN_MONTH, dueBandFor(daysUntilDue = 8, reminderLeadDays = 7))
        assertEquals(DueBand.WITHIN_MONTH, dueBandFor(daysUntilDue = 31, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns WITHIN_3_MONTHS between 32 and 100 days`() {
        assertEquals(DueBand.WITHIN_3_MONTHS, dueBandFor(daysUntilDue = 32, reminderLeadDays = 7))
        assertEquals(DueBand.WITHIN_3_MONTHS, dueBandFor(daysUntilDue = 100, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor returns LATER beyond 100 days`() {
        assertEquals(DueBand.LATER, dueBandFor(daysUntilDue = 101, reminderLeadDays = 7))
    }

    @Test
    fun `dueBandFor with a long reminder lead days can cover the whole WITHIN_MONTH range`() {
        // 소모품의 reminderLeadDays가 31보다 크면(예: 40일 전 알림), DUE_SOON이 WITHIN_MONTH보다 먼저 와야 한다.
        assertEquals(DueBand.DUE_SOON, dueBandFor(daysUntilDue = 40, reminderLeadDays = 40))
    }

    @Test
    fun `dueBandFor boundary matches the previously hardcoded dDayStyle thresholds`() {
        // HomeScreen.dDayStyle이 이 경계값들로 색을 나눴던 것과 정확히 같아야 한다(v0.4.12 이후 그대로).
        assertEquals(DueBand.OVERDUE, dueBandFor(-1, 7))
        assertEquals(DueBand.DUE_SOON, dueBandFor(0, 7))
        assertEquals(DueBand.DUE_SOON, dueBandFor(7, 7))
        assertEquals(DueBand.WITHIN_MONTH, dueBandFor(8, 7))
        assertEquals(DueBand.WITHIN_MONTH, dueBandFor(31, 0))
        assertEquals(DueBand.WITHIN_3_MONTHS, dueBandFor(32, 0))
        assertEquals(DueBand.WITHIN_3_MONTHS, dueBandFor(100, 0))
        assertEquals(DueBand.LATER, dueBandFor(101, 0))
    }
}
