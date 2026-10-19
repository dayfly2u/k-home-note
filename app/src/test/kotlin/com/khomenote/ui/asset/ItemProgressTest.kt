package com.khomenote.ui.asset

import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.ui.home.formatCycleLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemProgressTest {

    @Test
    fun `예정일이 오늘이면 진행률 100퍼센트`() {
        assertEquals(1f, progressFraction(daysUntilDue = 0, cycleLengthDays = 180), 0.0001f)
    }

    @Test
    fun `예정일이 지났으면 100퍼센트를 넘지 않는다`() {
        assertEquals(1f, progressFraction(daysUntilDue = -10, cycleLengthDays = 180), 0.0001f)
    }

    @Test
    fun `주기가 다 남았으면 0퍼센트`() {
        assertEquals(0f, progressFraction(daysUntilDue = 180, cycleLengthDays = 180), 0.0001f)
    }

    @Test
    fun `주기보다 더 남았어도 0퍼센트 아래로 내려가지 않는다`() {
        assertEquals(0f, progressFraction(daysUntilDue = 400, cycleLengthDays = 180), 0.0001f)
    }

    @Test
    fun `절반 지나면 50퍼센트`() {
        assertEquals(0.5f, progressFraction(daysUntilDue = 90, cycleLengthDays = 180), 0.0001f)
    }

    @Test
    fun `주기 길이가 0이어도 0으로 나누지 않는다`() {
        assertEquals(1f, progressFraction(daysUntilDue = 0, cycleLengthDays = 0), 0.0001f)
    }

    @Test
    fun `간격 주기는 cycleDays를 그대로 쓴다`() {
        val item = ConsumableItemEntity(assetId = 1, name = "엔진오일", cycleDays = 180)
        assertEquals(180, cycleLengthDays(item))
    }

    @Test
    fun `매달 고정은 30일로 근사한다`() {
        val item = ConsumableItemEntity(assetId = 1, name = "카드값", cycleDays = 30, monthlyDueDay = 25)
        assertEquals(30, cycleLengthDays(item))
    }

    @Test
    fun `매년 고정은 365일로 근사한다`() {
        val item = ConsumableItemEntity(
            assetId = 1, name = "생일", cycleDays = 30, yearlyDueMonth = 11, yearlyDueDay = 14,
        )
        assertEquals(365, cycleLengthDays(item))
    }

    @Test
    fun `주기 표기는 홈 둘째 줄과 같은 문구를 쓴다`() {
        val item = ConsumableItemEntity(assetId = 1, name = "엔진오일", cycleDays = 180)
        assertEquals("6개월마다", formatCycleLabel(item))
    }
}
