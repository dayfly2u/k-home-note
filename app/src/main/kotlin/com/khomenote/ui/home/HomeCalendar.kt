package com.khomenote.ui.home

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 홈 화면 보기 방식 — 목록(D-day 구간별) 또는 월간 달력. */
enum class HomeViewMode { LIST, CALENDAR }

/** 일요일부터 시작하는 달력 칸. 달이 시작하기 전 빈 칸은 null, 마지막 주도 7칸이 되도록 null로 채운다. */
internal fun monthCells(month: YearMonth): List<LocalDate?> {
    val leadingBlanks = month.atDay(1).dayOfWeek.value % 7 // MONDAY=1 .. SUNDAY=7 -> 일요일이 0칸
    val cells = MutableList<LocalDate?>(leadingBlanks) { null }
    for (day in 1..month.lengthOfMonth()) cells.add(month.atDay(day))
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

/**
 * 날짜 칸 밑에 찍는 점 — 그날 작업들의 D-day 구간을 받아, 지남·알림 기간(주황)이면 true / 그 외(청록)이면 false.
 * 급한 것(주황)이 앞에 오고 최대 3개까지만 찍는다.
 */
internal fun dotUrgencies(bands: List<DueBand>): List<Boolean> =
    bands.map { it == DueBand.OVERDUE || it == DueBand.DUE_SOON }.sortedByDescending { it }.take(3)

private val MONTH_TITLE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
private val DAY_TITLE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

internal fun monthTitle(month: YearMonth): String = month.format(MONTH_TITLE_FORMAT)

internal fun dayTitle(date: LocalDate): String = date.format(DAY_TITLE_FORMAT)

/** 선택한 날짜 제목 옆의 "D-4" / "오늘" / "D+3". */
internal fun relativeDayText(daysUntil: Long): String = when {
    daysUntil == 0L -> "오늘"
    daysUntil > 0 -> "D-$daysUntil"
    else -> "D+${-daysUntil}"
}
