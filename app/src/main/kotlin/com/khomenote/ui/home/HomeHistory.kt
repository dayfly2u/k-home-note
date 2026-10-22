package com.khomenote.ui.home

import com.khomenote.data.local.dto.RepairRecordWithAsset
import com.khomenote.data.local.dto.ReplacementRecordWithItem
import java.time.LocalDate

/** 달력 "한 일"의 종류 — 주기성작업의 이력(교체/구매 기록) 또는 끝낸 일회성작업. */
enum class HistoryKind { RECURRING, ONE_TIME }

/**
 * 홈 달력의 "한 일" 한 줄. [targetId]는 눌렀을 때 열 화면을 정한다 —
 * RECURRING이면 주기성작업 id(주기성작업 상세), ONE_TIME이면 대상 id(대상 상세).
 */
data class HistoryRowUiModel(
    val date: LocalDate,
    val title: String,
    val subtitle: String,
    val categoryId: Long,
    val kind: HistoryKind,
    val targetId: Long,
)

/**
 * 한 일 줄의 둘째 줄: "1,200,000원 · 쿠팡". 구매처가 없으면 메모 첫 줄을 대신 쓰고, 구매처/메모의 줄바꿈은
 * 첫 줄만 보여준다(예전 버전에서 여러 줄로 적어 둔 기록). 아무것도 없으면 [fallback].
 */
internal fun formatHistorySubtitle(price: Long?, vendor: String?, memo: String?, fallback: String): String {
    val parts = listOfNotNull(
        price?.let { "%,d원".format(it) },
        firstNonBlankLine(vendor) ?: firstNonBlankLine(memo),
    )
    return if (parts.isEmpty()) fallback else parts.joinToString(" · ")
}

private fun firstNonBlankLine(text: String?): String? =
    text?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

internal fun ReplacementRecordWithItem.toHistoryRow() = HistoryRowUiModel(
    date = record.date,
    title = "$assetName · $itemName",
    subtitle = formatHistorySubtitle(record.price, record.vendor, record.memo, fallback = "이력 기록"),
    categoryId = categoryId,
    kind = HistoryKind.RECURRING,
    targetId = record.consumableItemId,
)

internal fun RepairRecordWithAsset.toHistoryRow() = HistoryRowUiModel(
    date = record.date,
    title = "$assetName · ${record.name}",
    subtitle = formatHistorySubtitle(record.price, record.vendor, record.memo, fallback = "완료"),
    categoryId = categoryId,
    kind = HistoryKind.ONE_TIME,
    targetId = record.assetId,
)

/** 날짜 칸 밑 표시 — URGENT·UPCOMING은 채운 점(예정), HISTORY는 속이 빈 작은 원(지난 이력). */
internal enum class DayMarker { URGENT, UPCOMING, HISTORY }

/** 예정(급한 것 먼저) 다음에 이력 순으로 최대 3개까지만 찍는다. */
internal fun dayMarkers(bands: List<DueBand>, historyCount: Int): List<DayMarker> =
    (dotUrgencies(bands).map { if (it) DayMarker.URGENT else DayMarker.UPCOMING } + List(historyCount) { DayMarker.HISTORY })
        .take(3)
