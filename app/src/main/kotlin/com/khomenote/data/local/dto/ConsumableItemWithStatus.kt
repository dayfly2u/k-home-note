package com.khomenote.data.local.dto

import androidx.room.Embedded
import com.khomenote.data.local.entity.AssetCategory
import com.khomenote.data.local.entity.ConsumableItemEntity
import java.time.LocalDate

data class ConsumableItemWithStatus(
    @Embedded val item: ConsumableItemEntity,
    val assetName: String,
    val assetCategory: AssetCategory,
    val latestRecordDate: LocalDate?,
)
