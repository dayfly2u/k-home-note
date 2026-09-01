package com.khomenote.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object DueDateCalculator {
    /**
     * monthlyDueDay가 있으면 "매달 이 날짜", yearlyDueMonth/yearlyDueDay가 있으면 "매년 이 날짜"
     * 고정 주기로 계산하고, 둘 다 없으면 cycleDays 기반 간격으로 계산한다.
     */
    fun nextDueDate(
        itemCreatedAt: LocalDate,
        cycleDays: Int,
        latestRecordDate: LocalDate?,
        monthlyDueDay: Int? = null,
        yearlyDueMonth: Int? = null,
        yearlyDueDay: Int? = null,
    ): LocalDate {
        val base = latestRecordDate ?: itemCreatedAt
        return when {
            monthlyDueDay != null -> nextMonthlyDueDate(base, monthlyDueDay)
            yearlyDueMonth != null && yearlyDueDay != null -> nextYearlyDueDate(base, yearlyDueMonth, yearlyDueDay)
            else -> base.plusDays(cycleDays.toLong())
        }
    }

    // base 이후로 가장 가까운 dayOfMonth를 찾는다. 말일이 없는 달(예: 2월 30일)은 그 달의 마지막 날로 당긴다.
    private fun nextMonthlyDueDate(base: LocalDate, dayOfMonth: Int): LocalDate {
        val thisMonth = base.withDayOfMonth(minOf(dayOfMonth, base.lengthOfMonth()))
        if (thisMonth.isAfter(base)) return thisMonth
        val nextMonth = base.plusMonths(1)
        return nextMonth.withDayOfMonth(minOf(dayOfMonth, nextMonth.lengthOfMonth()))
    }

    // base 이후로 가장 가까운 month/day를 찾는다. 윤년 2월 29일처럼 그 해에 없는 날짜는
    // 그 달의 마지막 날로 당긴다(예: 평년에는 2월 28일).
    private fun nextYearlyDueDate(base: LocalDate, month: Int, dayOfMonth: Int): LocalDate {
        val thisYearLength = YearMonth.of(base.year, month).lengthOfMonth()
        val thisYear = LocalDate.of(base.year, month, minOf(dayOfMonth, thisYearLength))
        if (thisYear.isAfter(base)) return thisYear
        val nextYearLength = YearMonth.of(base.year + 1, month).lengthOfMonth()
        return LocalDate.of(base.year + 1, month, minOf(dayOfMonth, nextYearLength))
    }

    fun daysUntilDue(nextDueDate: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, nextDueDate)
}
