package com.khomenote.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DueDateCalculator {
    fun nextDueDate(itemCreatedAt: LocalDate, cycleDays: Int, latestRecordDate: LocalDate?): LocalDate =
        (latestRecordDate ?: itemCreatedAt).plusDays(cycleDays.toLong())

    fun daysUntilDue(nextDueDate: LocalDate, today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, nextDueDate)
}
