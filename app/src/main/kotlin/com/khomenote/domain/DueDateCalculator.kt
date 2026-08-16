package com.khomenote.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DueDateCalculator {
    /**
     * monthlyDueDay가 있으면 "매달 이 날짜" 고정 주기로, 없으면 cycleDays 기반 간격으로 계산한다.
     */
    fun nextDueDate(
        itemCreatedAt: LocalDate,
        cycleDays: Int,
        latestRecordDate: LocalDate?,
        monthlyDueDay: Int? = null,
    ): LocalDate {
        val base = latestRecordDate ?: itemCreatedAt
        return if (monthlyDueDay != null) {
            nextMonthlyDueDate(base, monthlyDueDay)
        } else {
            base.plusDays(cycleDays.toLong())
        }
    }

    // base 이후로 가장 가까운 dayOfMonth를 찾는다. 말일이 없는 달(예: 2월 30일)은 그 달의 마지막 날로 당긴다.
    private fun nextMonthlyDueDate(base: LocalDate, dayOfMonth: Int): LocalDate {
        val thisMonth = base.withDayOfMonth(minOf(dayOfMonth, base.lengthOfMonth()))
        if (thisMonth.isAfter(base)) return thisMonth
        val nextMonth = base.plusMonths(1)
        return nextMonth.withDayOfMonth(minOf(dayOfMonth, nextMonth.lengthOfMonth()))
    }

    fun daysUntilDue(nextDueDate: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, nextDueDate)
}
