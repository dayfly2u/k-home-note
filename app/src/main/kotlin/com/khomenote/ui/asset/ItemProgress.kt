package com.khomenote.ui.asset

import com.khomenote.data.local.entity.ConsumableItemEntity

/**
 * 대상 상세의 주기성작업 진행 막대 길이 계산용 "한 주기의 길이(일)".
 * 매달/매년 고정 모드는 직전 예정일을 따로 저장하지 않으므로 평균 길이(30일/365일)로 근사한다.
 */
internal fun cycleLengthDays(item: ConsumableItemEntity): Int = when {
    item.monthlyDueDay != null -> 30
    item.yearlyDueMonth != null -> 365
    else -> item.cycleDays.coerceAtLeast(1)
}

/** 한 주기 중 지난 비율(0~1). 예정일이 지났으면 1, 방금 완료해서 주기가 다 남았으면 0. */
internal fun progressFraction(daysUntilDue: Long, cycleLengthDays: Int): Float {
    val total = cycleLengthDays.coerceAtLeast(1)
    return ((total - daysUntilDue).toFloat() / total).coerceIn(0f, 1f)
}
