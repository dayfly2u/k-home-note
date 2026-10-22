package com.khomenote.ui.home

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHistoryTest {

    @Test
    fun `가격과 구매처를 가운뎃점으로 잇는다`() {
        assertEquals("1,200,000원 · 쿠팡", formatHistorySubtitle(1_200_000, "쿠팡", null, "이력 기록"))
    }

    @Test
    fun `가격만 있거나 구매처만 있어도 그것만 보여준다`() {
        assertEquals("35,000원", formatHistorySubtitle(35_000, null, null, "이력 기록"))
        assertEquals("쿠팡", formatHistorySubtitle(null, "쿠팡", null, "이력 기록"))
    }

    @Test
    fun `구매처에 줄바꿈이 들어 있으면 앞의 빈 줄을 건너뛰고 첫 줄만 보여준다`() {
        val vendor = "\nPIF영어36만. 개념상상28만\n수학의힘55만. "
        assertEquals("1,200,000원 · PIF영어36만. 개념상상28만", formatHistorySubtitle(1_200_000, vendor, null, "이력 기록"))
    }

    @Test
    fun `구매처가 없으면 메모 첫 줄을 대신 보여준다`() {
        assertEquals("PIF영어:16만 개념상상:28만", formatHistorySubtitle(null, null, "PIF영어:16만 개념상상:28만\n수학의힘:55만", "이력 기록"))
    }

    @Test
    fun `구매처가 공백뿐이면 없는 것으로 보고 메모를 쓴다`() {
        assertEquals("메모", formatHistorySubtitle(null, "  \n ", "메모", "이력 기록"))
    }

    @Test
    fun `가격 구매처 메모가 모두 없으면 대체 문구를 보여준다`() {
        assertEquals("이력 기록", formatHistorySubtitle(null, null, null, "이력 기록"))
        assertEquals("완료", formatHistorySubtitle(null, "", " ", "완료"))
    }

    @Test
    fun `표시는 급한 점 청록 점 이력 원 순서다`() {
        val markers = dayMarkers(listOf(DueBand.WITHIN_MONTH, DueBand.OVERDUE), historyCount = 1)
        assertEquals(listOf(DayMarker.URGENT, DayMarker.UPCOMING, DayMarker.HISTORY), markers)
    }

    @Test
    fun `이력만 있는 날은 이력 원만 찍는다`() {
        assertEquals(listOf(DayMarker.HISTORY, DayMarker.HISTORY), dayMarkers(emptyList(), historyCount = 2))
    }

    @Test
    fun `표시는 최대 3개이고 예정이 이력보다 먼저 남는다`() {
        val markers = dayMarkers(List(2) { DueBand.LATER }, historyCount = 5)
        assertEquals(listOf(DayMarker.UPCOMING, DayMarker.UPCOMING, DayMarker.HISTORY), markers)
    }

    @Test
    fun `예정도 이력도 없는 날은 표시가 없다`() {
        assertEquals(emptyList<DayMarker>(), dayMarkers(emptyList(), historyCount = 0))
    }

    @Test
    fun `달을 옮겨 이번 달로 오면 오늘을 선택한다`() {
        val today = LocalDate.of(2026, 10, 22)
        val september = CalendarPosition(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 1))
        assertEquals(CalendarPosition(YearMonth.of(2026, 10), today), september.movedBy(1, today))
    }

    @Test
    fun `이번 달이 아닌 달로 옮기면 그 달 1일을 선택한다`() {
        val today = LocalDate.of(2026, 10, 22)
        val thisMonth = CalendarPosition(YearMonth.of(2026, 10), today)
        assertEquals(CalendarPosition(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 1)), thisMonth.movedBy(-1, today))
        assertEquals(CalendarPosition(YearMonth.of(2027, 1), LocalDate.of(2027, 1, 1)), thisMonth.movedBy(3, today))
    }
}
