package com.khomenote.ui.home

/**
 * D-day 배지 색(dDayStyle)과 홈 화면 섹션 분류가 항상 같은 기준을 쓰도록 공유하는 구간.
 * 기준은 redesign-brief.md 1장 표(= 기존 v0.4.12 기준)와 동일하다 — 숫자를 바꾸면 이 파일만 고치면
 * 배지 색과 섹션 분류 양쪽에 다 반영된다.
 */
enum class DueBand(val sectionTitle: String) {
    OVERDUE("지났어요"),
    DUE_SOON("챙길 때"),
    WITHIN_MONTH("한 달 안"),
    WITHIN_3_MONTHS("석 달 안"),
    LATER("나중에"),
}

fun dueBandFor(daysUntilDue: Long, reminderLeadDays: Int): DueBand = when {
    daysUntilDue < 0 -> DueBand.OVERDUE
    daysUntilDue <= reminderLeadDays -> DueBand.DUE_SOON
    daysUntilDue <= 31 -> DueBand.WITHIN_MONTH
    daysUntilDue <= 100 -> DueBand.WITHIN_3_MONTHS
    else -> DueBand.LATER
}

data class HomeSummaryCounts(val overdue: Int, val dueSoon: Int, val withinMonth: Int)

fun List<HomeRowUiModel>.summaryCounts(): HomeSummaryCounts =
    map { dueBandFor(it.daysUntilDue, it.reminderLeadDays) }.toSummaryCounts()

internal fun List<DueBand>.toSummaryCounts(): HomeSummaryCounts = HomeSummaryCounts(
    overdue = count { it == DueBand.OVERDUE },
    dueSoon = count { it == DueBand.DUE_SOON },
    withinMonth = count { it == DueBand.WITHIN_MONTH },
)
