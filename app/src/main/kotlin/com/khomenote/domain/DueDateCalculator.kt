package com.khomenote.domain

import com.github.usingsky.calendar.KoreanLunarCalendar
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object DueDateCalculator {
    /**
     * monthlyDueDay가 있으면 "매달 이 날짜", yearlyDueMonth/yearlyDueDay가 있으면 "매년 이 날짜"
     * 고정 주기로 계산하고, 둘 다 없으면 cycleDays 기반 간격으로 계산한다. yearlyDueIsLunar가 true면
     * yearlyDueMonth/yearlyDueDay를 음력으로 해석해서 매년 그 해의 양력 날짜로 환산한다(생일/제사용).
     *
     * latestRecordDate가 있으면(실제 구매/교체 이력이 존재하면) 그 이력이 "가장 가까운 지정일 occurrence를
     * 처리한 것"이라고 보고, 그 occurrence의 다음 occurrence로 넘어간다 — 지정일보다 며칠 일찍 처리했든
     * (예: 30일 지정인데 25일에 처리) 늦게 처리했든(예: 30일을 놓쳐서 다음 달 4일에 처리) 똑같이 그
     * 가장 가까운 지정일 하나를 처리한 것으로 보고 "그다음" occurrence가 다음 예정일이 된다. 아직 이력이
     * 없어 itemCreatedAt을 기준으로 잡을 때는 기존처럼 base 이후 가장 가까운 지정일을 찾는다(이번 달
     * 지정일이 안 지났으면 이번 달 그대로).
     */
    fun nextDueDate(
        itemCreatedAt: LocalDate,
        cycleDays: Int,
        latestRecordDate: LocalDate?,
        monthlyDueDay: Int? = null,
        yearlyDueMonth: Int? = null,
        yearlyDueDay: Int? = null,
        yearlyDueIsLunar: Boolean = false,
    ): LocalDate {
        val hasRecord = latestRecordDate != null
        val base = latestRecordDate ?: itemCreatedAt
        return when {
            monthlyDueDay != null -> nextMonthlyDueDate(base, monthlyDueDay, forceNextPeriod = hasRecord)
            yearlyDueMonth != null && yearlyDueDay != null && yearlyDueIsLunar ->
                nextYearlyLunarDueDate(base, yearlyDueMonth, yearlyDueDay, forceNextPeriod = hasRecord)
            yearlyDueMonth != null && yearlyDueDay != null ->
                nextYearlyDueDate(base, yearlyDueMonth, yearlyDueDay, forceNextPeriod = hasRecord)
            else -> base.plusDays(cycleDays.toLong())
        }
    }

    // forceNextPeriod=false: base 이후로 가장 가까운 dayOfMonth를 찾는다(이번 달 지정일이 안 지났으면 이번 달 그대로).
    // forceNextPeriod=true: base와 가장 가까운(이전 달이든 다음 달이든) occurrence를 "처리한 것"으로 보고
    // 그 바로 다음 occurrence로 넘어간다 — base가 속한 달의 지정일만 보고 무조건 base+1달로 넘기면,
    // 이미 한 달 넘게 밀려서 처리한 경우(예: 9/30 지정일을 10/4에 처리) 10/30이 아니라 11/30으로
    // 두 달치를 건너뛰는 버그가 생긴다.
    // 말일이 없는 달(예: 2월 30일)은 그 달의 마지막 날로 당긴다.
    private fun nextMonthlyDueDate(base: LocalDate, dayOfMonth: Int, forceNextPeriod: Boolean): LocalDate {
        val thisMonth = clampedMonthlyDate(base, dayOfMonth)
        if (!forceNextPeriod) {
            if (thisMonth.isAfter(base)) return thisMonth
            return clampedMonthlyDate(base.plusMonths(1), dayOfMonth)
        }
        val fulfilled = nearestMonthlyOccurrence(base, dayOfMonth, thisMonth)
        return clampedMonthlyDate(fulfilled.plusMonths(1), dayOfMonth)
    }

    private fun clampedMonthlyDate(monthAnchor: LocalDate, dayOfMonth: Int): LocalDate =
        monthAnchor.withDayOfMonth(minOf(dayOfMonth, monthAnchor.lengthOfMonth()))

    // base와 가장 가까운 지정일 occurrence(이번 달 것 또는 그 양옆 달 것 중 더 가까운 쪽)를 찾는다.
    private fun nearestMonthlyOccurrence(base: LocalDate, dayOfMonth: Int, thisMonth: LocalDate): LocalDate {
        val adjacent = if (thisMonth.isAfter(base)) {
            clampedMonthlyDate(base.minusMonths(1), dayOfMonth)
        } else {
            clampedMonthlyDate(base.plusMonths(1), dayOfMonth)
        }
        return if (absDays(base, thisMonth) <= absDays(base, adjacent)) thisMonth else adjacent
    }

    // nextMonthlyDueDate와 같은 forceNextPeriod 규칙을 연 단위로 적용한다.
    // 윤년 2월 29일처럼 그 해에 없는 날짜는 그 달의 마지막 날로 당긴다(예: 평년에는 2월 28일).
    private fun nextYearlyDueDate(base: LocalDate, month: Int, dayOfMonth: Int, forceNextPeriod: Boolean): LocalDate {
        val thisYear = clampedYearlyDate(base.year, month, dayOfMonth)
        if (!forceNextPeriod) {
            if (thisYear.isAfter(base)) return thisYear
            return clampedYearlyDate(base.year + 1, month, dayOfMonth)
        }
        val fulfilled = nearestYearlyOccurrence(base, month, dayOfMonth, thisYear)
        return clampedYearlyDate(fulfilled.year + 1, month, dayOfMonth)
    }

    private fun clampedYearlyDate(year: Int, month: Int, dayOfMonth: Int): LocalDate {
        val length = YearMonth.of(year, month).lengthOfMonth()
        return LocalDate.of(year, month, minOf(dayOfMonth, length))
    }

    // base와 가장 가까운 지정일 occurrence(올해 것 또는 그 양옆 해 것 중 더 가까운 쪽)를 찾는다.
    private fun nearestYearlyOccurrence(base: LocalDate, month: Int, dayOfMonth: Int, thisYear: LocalDate): LocalDate {
        val adjacent = if (thisYear.isAfter(base)) {
            clampedYearlyDate(base.year - 1, month, dayOfMonth)
        } else {
            clampedYearlyDate(base.year + 1, month, dayOfMonth)
        }
        return if (absDays(base, thisYear) <= absDays(base, adjacent)) thisYear else adjacent
    }

    // nextYearlyDueDate와 같은 forceNextPeriod 규칙을 음력 월/일 기준으로 적용한다.
    // 라이브러리가 지원하는 범위(음력 1000~2050년)를 벗어나는 극단적인 경우를 대비해 몇 해 더 찾아본다.
    private fun nextYearlyLunarDueDate(base: LocalDate, lunarMonth: Int, lunarDay: Int, forceNextPeriod: Boolean): LocalDate {
        val thisYear = lunarToSolar(base.year, lunarMonth, lunarDay)
        if (!forceNextPeriod) {
            if (thisYear != null && thisYear.isAfter(base)) return thisYear
            return nextValidLunarOccurrence(base.year, lunarMonth, lunarDay)
        }
        val fulfilledYear = nearestLunarOccurrenceYear(base, thisYear, lunarMonth, lunarDay)
        return nextValidLunarOccurrence(fulfilledYear, lunarMonth, lunarDay)
    }

    // base와 가장 가까운 음력 지정일 occurrence가 속한 "해"를 찾는다(올해 것 또는 그 양옆 해 것 중 더 가까운 쪽).
    private fun nearestLunarOccurrenceYear(base: LocalDate, thisYear: LocalDate?, lunarMonth: Int, lunarDay: Int): Int {
        if (thisYear == null) return base.year
        val adjacentYear = if (thisYear.isAfter(base)) base.year - 1 else base.year + 1
        val adjacent = lunarToSolar(adjacentYear, lunarMonth, lunarDay) ?: return base.year
        return if (absDays(base, thisYear) <= absDays(base, adjacent)) base.year else adjacentYear
    }

    // fulfilledYear 다음 해부터 유효한 음력 환산이 될 때까지 찾는다(라이브러리 지원 범위를 벗어나는
    // 극단적인 경우에 대한 대비).
    private fun nextValidLunarOccurrence(fulfilledYear: Int, lunarMonth: Int, lunarDay: Int): LocalDate {
        for (yearOffset in 1..5) {
            lunarToSolar(fulfilledYear + yearOffset, lunarMonth, lunarDay)?.let { return it }
        }
        // 라이브러리 지원 범위를 벗어난 극단적인 경우의 최후 대비책 — 정확한 음력 환산 대신 대략적인 날짜.
        return LocalDate.of(fulfilledYear + 1, lunarMonth.coerceIn(1, 12), 1)
    }

    // 그 해 음력 lunarMonth가 lunarDay만큼 길지 않으면(작은달) 그 달의 마지막 날로 당긴다
    // (양력 말일 클램프와 동일한 방식). isIntercalation(윤달)은 항상 false로 취급한다.
    private fun lunarToSolar(lunarYear: Int, lunarMonth: Int, lunarDay: Int): LocalDate? {
        val calendar = KoreanLunarCalendar.getInstance()
        synchronized(calendar) {
            var day = lunarDay
            while (day >= 1) {
                if (calendar.setLunarDate(lunarYear, lunarMonth, day, false)) {
                    return LocalDate.of(calendar.solarYear, calendar.solarMonth, calendar.solarDay)
                }
                day--
            }
        }
        return null
    }

    private fun absDays(a: LocalDate, b: LocalDate): Long = ChronoUnit.DAYS.between(a, b).let { if (it < 0) -it else it }

    fun daysUntilDue(nextDueDate: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, nextDueDate)
}
