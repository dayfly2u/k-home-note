package com.khomenote.ui.home

import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.RepairRecordEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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

internal fun <T> List<Pair<DueBand, T>>.groupedByBand(): List<Pair<DueBand, List<T>>> {
    val byBand = groupBy({ it.first }, { it.second })
    return DueBand.entries.mapNotNull { band ->
        val items = byBand[band] ?: return@mapNotNull null
        if (items.isEmpty()) null else band to items
    }
}

fun <T : HomeRowUiModel> List<T>.groupedBySection(): List<Pair<DueBand, List<T>>> =
    sortedBy { it.dueDate }
        .map { dueBandFor(it.daysUntilDue, it.reminderLeadDays) to it }
        .groupedByBand()

private val KOREAN_MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일")

/**
 * 홈 목록 행의 둘째 줄에 쓰는 주기 설명. AssetDetailScreen.formatCycle()은 "1yr 3m"처럼 자산
 * 상세 화면에서 소모품 이름 옆에 붙이는 짧은 영문 축약형이라, 여기서는 완전한 한국어 문장으로
 * 새로 만든다(둘은 쓰이는 화면이 달라서 같은 함수를 억지로 재사용하지 않는다).
 */
fun formatRecurringSubtitle(item: ConsumableItemEntity, latestRecordDate: LocalDate?): String {
    val cycleLabel = formatCycleLabel(item)
    if (item.monthlyDueDay != null || item.yearlyDueMonth != null) return cycleLabel
    val lastDoneLabel = latestRecordDate?.let { " · 지난번 ${it.format(KOREAN_MONTH_DAY)}" } ?: ""
    return "$cycleLabel$lastDoneLabel"
}

/** "매달 25일" / "매년 11월 14일(음력)" / "6개월마다" — 주기 방식에 맞는 한국어 주기 표기(홈 둘째 줄, 대상 상세 공통). */
fun formatCycleLabel(item: ConsumableItemEntity): String {
    item.monthlyDueDay?.let { return "매달 ${it}일" }
    item.yearlyDueMonth?.let { month ->
        val lunarSuffix = if (item.isYearlyLunar == true) "(음력)" else ""
        return "매년 ${month}월 ${item.yearlyDueDay}일$lunarSuffix"
    }
    return formatIntervalKorean(item.cycleDays)
}

// cycleDays를 "1년마다"/"6개월마다"/"40일마다"처럼 깔끔하게 나눠지는 가장 큰 단위 하나로 표현한다.
// 365일의 배수면 년, 30일의 배수면 개월, 그 외에는 일 단위로 그대로 보여준다.
internal fun formatIntervalKorean(cycleDays: Int): String = when {
    cycleDays > 0 && cycleDays % 365 == 0 -> "${cycleDays / 365}년마다"
    cycleDays > 0 && cycleDays % 30 == 0 -> "${cycleDays / 30}개월마다"
    else -> "${cycleDays}일마다"
}

fun formatOneTimeSubtitle(record: RepairRecordEntity): String {
    val dateLabel = record.date.format(KOREAN_MONTH_DAY)
    val memoPreview = record.memo?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    return "$dateLabel$memoPreview"
}
