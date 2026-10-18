package com.khomenote.ui.home

import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.RepairRecordEntity
import java.time.LocalDate
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

class HomeGroupedByBandTest {

    @Test
    fun `groupedByBand orders groups by DueBand enum order and skips empty bands`() {
        val input = listOf(DueBand.WITHIN_MONTH to "a", DueBand.OVERDUE to "b")

        val grouped = input.groupedByBand()

        assertEquals(listOf(DueBand.OVERDUE, DueBand.WITHIN_MONTH), grouped.map { it.first })
        assertEquals(listOf("b"), grouped[0].second)
        assertEquals(listOf("a"), grouped[1].second)
    }

    @Test
    fun `groupedByBand keeps insertion order within a group`() {
        val input = listOf(DueBand.WITHIN_MONTH to "later", DueBand.WITHIN_MONTH to "sooner")

        val grouped = input.groupedByBand()

        assertEquals(1, grouped.size)
        assertEquals(listOf("later", "sooner"), grouped.first().second)
    }

    @Test
    fun `groupedByBand returns an empty list for an empty input`() {
        assertEquals(emptyList<Pair<DueBand, List<String>>>(), emptyList<Pair<DueBand, String>>().groupedByBand())
    }
}

class HomeSubtitleTest {

    @Test
    fun `formatRecurringSubtitle for monthly fixed day matches AssetDetailScreen wording`() {
        val item = ConsumableItemEntity(assetId = 1, name = "카드값", cycleDays = 30, monthlyDueDay = 25)
        assertEquals("매달 25일", formatRecurringSubtitle(item, latestRecordDate = null))
    }

    @Test
    fun `formatRecurringSubtitle for yearly fixed day includes lunar suffix when set`() {
        val item = ConsumableItemEntity(
            assetId = 1, name = "생일", cycleDays = 30,
            yearlyDueMonth = 11, yearlyDueDay = 14, isYearlyLunar = true,
        )
        assertEquals("매년 11월 14일(음력)", formatRecurringSubtitle(item, latestRecordDate = null))
    }

    @Test
    fun `formatRecurringSubtitle for interval mode shows the cycle in Korean units`() {
        val item = ConsumableItemEntity(assetId = 1, name = "엔진오일", cycleDays = 180)
        assertEquals("6개월마다", formatRecurringSubtitle(item, latestRecordDate = null))
    }

    @Test
    fun `formatRecurringSubtitle for interval mode appends the last done date when it exists`() {
        val item = ConsumableItemEntity(assetId = 1, name = "엔진오일", cycleDays = 180)
        val result = formatRecurringSubtitle(item, latestRecordDate = LocalDate.of(2026, 4, 30))
        assertEquals("6개월마다 · 지난번 4월 30일", result)
    }

    @Test
    fun `formatRecurringSubtitle for an interval that does not divide evenly falls back to days`() {
        val item = ConsumableItemEntity(assetId = 1, name = "소모품", cycleDays = 40)
        assertEquals("40일마다", formatRecurringSubtitle(item, latestRecordDate = null))
    }

    @Test
    fun `formatOneTimeSubtitle shows the date and a memo preview`() {
        val record = RepairRecordEntity(assetId = 1, name = "보일러 점검", date = LocalDate.of(2026, 10, 20), memo = "전 담당자에게 확인")
        assertEquals("10월 20일 · 전 담당자에게 확인", formatOneTimeSubtitle(record))
    }

    @Test
    fun `formatOneTimeSubtitle shows only the date when there is no memo`() {
        val record = RepairRecordEntity(assetId = 1, name = "보일러 점검", date = LocalDate.of(2026, 10, 20))
        assertEquals("10월 20일", formatOneTimeSubtitle(record))
    }
}
