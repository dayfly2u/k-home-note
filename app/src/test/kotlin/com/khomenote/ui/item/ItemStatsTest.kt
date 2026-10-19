package com.khomenote.ui.item

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemStatsTest {

    @Test
    fun `기록이 없거나 하나면 평균 간격이 없다`() {
        assertNull(averageIntervalDays(emptyList()))
        assertNull(averageIntervalDays(listOf(LocalDate.of(2026, 4, 30))))
    }

    @Test
    fun `두 기록 사이 간격이 그대로 평균이다`() {
        val avg = averageIntervalDays(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
        assertEquals(30.0, avg!!, 0.0001)
    }

    @Test
    fun `여러 기록의 평균 간격은 처음과 마지막 사이를 간격 수로 나눈다`() {
        // 0일, 10일, 40일 -> 간격 10, 30 -> 평균 20
        val dates = listOf(LocalDate.of(2026, 2, 10), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11))
        assertEquals(20.0, averageIntervalDays(dates)!!, 0.0001)
    }

    @Test
    fun `평균 간격이 45일 미만이면 일 단위로 표시한다`() {
        assertEquals(StatValue("30", "일"), formatAverageInterval(30.0))
    }

    @Test
    fun `평균 간격이 45일 이상이면 개월 단위로 표시한다`() {
        assertEquals(StatValue("5.9", "개월"), formatAverageInterval(177.0))
    }

    @Test
    fun `평균 간격이 없으면 대시로 표시한다`() {
        assertEquals(StatValue("-", ""), formatAverageInterval(null))
    }

    @Test
    fun `1만원 이상 누적 비용은 만원 단위로 표시한다`() {
        assertEquals(StatValue("33.8", "만원"), formatTotalCost(338_000))
    }

    @Test
    fun `1만원 미만 누적 비용은 원 단위로 표시한다`() {
        assertEquals(StatValue("9,000", "원"), formatTotalCost(9_000))
    }

    @Test
    fun `가격이 적힌 기록이 없으면 대시로 표시한다`() {
        assertEquals(StatValue("-", ""), formatTotalCost(null))
    }
}
