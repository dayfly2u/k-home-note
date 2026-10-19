package com.khomenote.ui.home

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeCalendarTest {

    @Test
    fun `2026년 10월은 목요일 시작이라 앞에 빈 칸 4개 그리고 35칸이다`() {
        val cells = monthCells(YearMonth.of(2026, 10))
        assertEquals(35, cells.size)
        assertEquals(List(4) { null }, cells.take(4))
        assertEquals(LocalDate.of(2026, 10, 1), cells[4])
        assertEquals(LocalDate.of(2026, 10, 31), cells.last())
    }

    @Test
    fun `일요일에 시작하는 28일짜리 달은 정확히 28칸이다`() {
        val cells = monthCells(YearMonth.of(2026, 2))
        assertEquals(28, cells.size)
        assertEquals(LocalDate.of(2026, 2, 1), cells.first())
        assertEquals(LocalDate.of(2026, 2, 28), cells.last())
    }

    @Test
    fun `마지막 주는 빈 칸으로 7칸을 채운다`() {
        val cells = monthCells(YearMonth.of(2026, 11))
        assertEquals(35, cells.size)
        assertEquals(LocalDate.of(2026, 11, 30), cells[29])
        assertNull(cells[30])
        assertNull(cells.last())
    }

    @Test
    fun `달력 칸 수는 항상 7의 배수다`() {
        for (month in 1..12) {
            assertEquals(0, monthCells(YearMonth.of(2027, month)).size % 7)
        }
    }

    @Test
    fun `지남과 알림 기간은 주황 그 외는 청록 점이고 급한 것이 앞에 온다`() {
        val dots = dotUrgencies(listOf(DueBand.WITHIN_MONTH, DueBand.OVERDUE, DueBand.LATER, DueBand.DUE_SOON))
        assertEquals(listOf(true, true, false), dots)
    }

    @Test
    fun `점은 최대 3개까지만 찍는다`() {
        assertEquals(3, dotUrgencies(List(5) { DueBand.DUE_SOON }).size)
    }

    @Test
    fun `작업이 없는 날은 점이 없다`() {
        assertEquals(emptyList<Boolean>(), dotUrgencies(emptyList()))
    }

    @Test
    fun `월 제목과 날짜 제목 표기`() {
        assertEquals("2026년 10월", monthTitle(YearMonth.of(2026, 10)))
        assertEquals("10월 22일 (목)", dayTitle(LocalDate.of(2026, 10, 22)))
    }

    @Test
    fun `상대 일수 표기`() {
        assertEquals("D-4", relativeDayText(4))
        assertEquals("오늘", relativeDayText(0))
        assertEquals("D+3", relativeDayText(-3))
    }
}
