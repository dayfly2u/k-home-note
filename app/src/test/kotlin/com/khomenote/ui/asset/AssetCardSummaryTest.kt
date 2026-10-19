package com.khomenote.ui.asset

import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.RepairRecordEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssetCardSummaryTest {
    private val today = LocalDate.of(2026, 10, 18)

    private fun status(assetId: Long, name: String, cycleDays: Int, lastDone: LocalDate?, lead: Int = 7) =
        ConsumableItemWithStatus(
            item = ConsumableItemEntity(
                id = name.hashCode().toLong(), assetId = assetId, name = name, cycleDays = cycleDays,
                reminderLeadDays = lead, createdAt = LocalDate.of(2025, 1, 1),
            ),
            assetName = "대상", categoryId = 1, latestRecordDate = lastDone,
        )

    private fun repair(assetId: Long, name: String, date: LocalDate, lead: Int?) =
        RepairRecordEntity(assetId = assetId, name = name, date = date, reminderLeadDays = lead)

    @Test
    fun `다른 대상의 작업은 세지 않는다`() {
        val statuses = listOf(status(1, "A", 30, today), status(2, "B", 30, today))
        val summary = summarizeAsset(1, statuses, emptyList(), today)
        assertEquals(1, summary.recurringCount)
    }

    @Test
    fun `가장 가까운 작업은 남은 일수가 가장 작은 것이다`() {
        // 엔진오일: 2026-10-09에 했고 180일 주기 -> 다음은 2027-04-07 (D-171). 필터: 2026-09-20 + 30일 = 2026-10-20 (D-2).
        val statuses = listOf(
            status(1, "엔진오일", 180, LocalDate.of(2026, 10, 9)),
            status(1, "필터", 30, LocalDate.of(2026, 9, 20)),
        )
        val nearest = summarizeAsset(1, statuses, emptyList(), today).nearest
        assertEquals("필터", nearest?.name)
        assertEquals(2L, nearest?.daysUntilDue)
    }

    @Test
    fun `지난 작업도 가장 가까운 작업이 된다`() {
        val statuses = listOf(status(1, "필터", 30, LocalDate.of(2026, 9, 1)))
        val nearest = summarizeAsset(1, statuses, emptyList(), today).nearest
        assertEquals(-17L, nearest?.daysUntilDue)
    }

    @Test
    fun `알림이 켜진 일회성작업만 가장 가까운 작업 후보가 된다`() {
        val records = listOf(
            repair(1, "끝난 점검", LocalDate.of(2026, 10, 19), lead = null),
            repair(1, "예정 점검", LocalDate.of(2026, 10, 25), lead = 7),
        )
        val summary = summarizeAsset(1, emptyList(), records, today)
        assertEquals(2, summary.oneTimeCount)
        assertEquals("예정 점검", summary.nearest?.name)
        assertEquals(7L, summary.nearest?.daysUntilDue)
    }

    @Test
    fun `알림 켜진 작업이 없으면 가장 가까운 작업은 없다`() {
        val records = listOf(repair(1, "끝난 점검", today, lead = null))
        assertNull(summarizeAsset(1, emptyList(), records, today).nearest)
    }

    @Test
    fun `개수 표기는 0개인 쪽을 생략한다`() {
        assertEquals("주기성 4 · 일회성 3", cardCountsLabel(AssetCardSummary(4, 3, null)))
        assertEquals("주기성 2", cardCountsLabel(AssetCardSummary(2, 0, null)))
        assertEquals("일회성 1", cardCountsLabel(AssetCardSummary(0, 1, null)))
        assertEquals("작업 없음", cardCountsLabel(AssetCardSummary.EMPTY))
    }
}
