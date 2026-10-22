package com.khomenote.data.local.dto

import androidx.room.Embedded
import com.khomenote.data.local.entity.ReplacementRecordEntity

/** 주기성작업의 이력(교체/구매 기록) 한 건과, 그 작업·대상 이름·카테고리 — 홈 달력의 "한 일" 표시용. */
data class ReplacementRecordWithItem(
    @Embedded val record: ReplacementRecordEntity,
    val itemName: String,
    val assetName: String,
    val categoryId: Long,
)
