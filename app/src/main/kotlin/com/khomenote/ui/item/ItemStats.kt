package com.khomenote.ui.item

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** 숫자와 단위를 따로 그리기 위한 쌍 — 시안은 숫자는 Outfit 20sp, 단위는 12sp로 다르게 보여준다. */
internal data class StatValue(val number: String, val unit: String)

private val EMPTY_STAT = StatValue("-", "")

/** 기록 날짜들 사이의 평균 간격(일). 연속한 기록 간격의 평균 = (마지막 − 처음) / (개수 − 1). 기록이 2개 미만이면 null. */
internal fun averageIntervalDays(dates: List<LocalDate>): Double? {
    if (dates.size < 2) return null
    val sorted = dates.sorted()
    return ChronoUnit.DAYS.between(sorted.first(), sorted.last()).toDouble() / (sorted.size - 1)
}

/** 45일 미만은 일 단위, 그 이상은 30일 = 1개월로 환산해 소수 첫째 자리까지. */
internal fun formatAverageInterval(averageDays: Double?): StatValue = when {
    averageDays == null -> EMPTY_STAT
    averageDays < 45 -> StatValue(averageDays.roundToInt().toString(), "일")
    else -> StatValue(String.format(Locale.US, "%.1f", averageDays / 30), "개월")
}

/** 1만원 이상은 "33.8" + "만원", 그 미만은 "9,000" + "원". 가격이 적힌 기록이 하나도 없으면 "-". */
internal fun formatTotalCost(total: Long?): StatValue = when {
    total == null -> EMPTY_STAT
    total >= 10_000 -> StatValue(String.format(Locale.US, "%.1f", total / 10_000.0), "만원")
    else -> StatValue(String.format(Locale.US, "%,d", total), "원")
}
