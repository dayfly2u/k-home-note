package com.khomenote.data.local.dto

import androidx.room.Embedded
import com.khomenote.data.local.entity.RepairRecordEntity

data class RepairRecordWithAsset(
    @Embedded val record: RepairRecordEntity,
    val assetName: String,
    val categoryId: Long,
)
