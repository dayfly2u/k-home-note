package com.khomenote.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object DueDateCalculator {
    /**
     * monthlyDueDay가 있으면 "매달 이 날짜", yearlyDueMonth/yearlyDueDay가 있으면 "매년 이 날짜"
     * 고정 주기로 계산하고, 둘 다 없으면 cycleDays 기반 간격으로 계산한다.
     *
     * latestRecordDate가 있으면(실제 구매/교체 이력이 존재하면) "이번 주기는 이미 처리했다"고 보고,
     * 매달/매년 고정 주기에서는 그 이력이 속한 달/해의 지정일이 아직 안 지났어도 무조건 다음 달/해로
     * 넘긴다 — 예를 들어 매달 25일 카드값을 20일에 미리 결제 기록을 남기면 다음 예정일이 곧바로
     * 다음 달 25일로 바뀐다. 아직 이력이 없어 itemCreatedAt을 기준으로 잡을 때는 기존처럼
     * base 이후 가장 가까운 지정일을 찾는다(이번 달 지정일이 안 지났으면 이번 달 그대로).
     */
    fun nextDueDate(
        itemCreatedAt: LocalDate,
        cycleDays: Int,
        latestRecordDate: LocalDate?,
        monthlyDueDay: Int? = null,
        yearlyDueMonth: Int? = null,
        yearlyDueDay: Int? = null,
    ): LocalDate {
        val hasRecord = latestRecordDate != null
        val base = latestRecordDate ?: itemCreatedAt
        return when {
            monthlyDueDay != null -> nextMonthlyDueDate(base, monthlyDueDay, forceNextPeriod = hasRecord)
            yearlyDueMonth != null && yearlyDueDay != null ->
                nextYearlyDueDate(base, yearlyDueMonth, yearlyDueDay, forceNextPeriod = hasRecord)
            else -> base.plusDays(cycleDays.toLong())
        }
    }

    // forceNextPeriod=false: base 이후로 가장 가까운 dayOfMonth를 찾는다(이번 달 지정일이 안 지났으면 이번 달 그대로).
    // forceNextPeriod=true: base가 속한 달의 지정일이 아직 안 지났어도 무조건 다음 달로 넘긴다.
    // 말일이 없는 달(예: 2월 30일)은 그 달의 마지막 날로 당긴다.
    private fun nextMonthlyDueDate(base: LocalDate, dayOfMonth: Int, forceNextPeriod: Boolean): LocalDate {
        if (!forceNextPeriod) {
            val thisMonth = base.withDayOfMonth(minOf(dayOfMonth, base.lengthOfMonth()))
            if (thisMonth.isAfter(base)) return thisMonth
        }
        val nextMonth = base.plusMonths(1)
        return nextMonth.withDayOfMonth(minOf(dayOfMonth, nextMonth.lengthOfMonth()))
    }

    // nextMonthlyDueDate와 같은 forceNextPeriod 규칙을 연 단위로 적용한다.
    // 윤년 2월 29일처럼 그 해에 없는 날짜는 그 달의 마지막 날로 당긴다(예: 평년에는 2월 28일).
    private fun nextYearlyDueDate(base: LocalDate, month: Int, dayOfMonth: Int, forceNextPeriod: Boolean): LocalDate {
        if (!forceNextPeriod) {
            val thisYearLength = YearMonth.of(base.year, month).lengthOfMonth()
            val thisYear = LocalDate.of(base.year, month, minOf(dayOfMonth, thisYearLength))
            if (thisYear.isAfter(base)) return thisYear
        }
        val nextYearLength = YearMonth.of(base.year + 1, month).lengthOfMonth()
        return LocalDate.of(base.year + 1, month, minOf(dayOfMonth, nextYearLength))
    }

    fun daysUntilDue(nextDueDate: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, nextDueDate)
}
