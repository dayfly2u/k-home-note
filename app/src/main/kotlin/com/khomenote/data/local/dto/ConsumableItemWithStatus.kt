package com.khomenote.data.local.dto

import androidx.room.Embedded
import com.khomenote.data.local.entity.ConsumableItemEntity
import java.time.LocalDate

data class ConsumableItemWithStatus(
    @Embedded val item: ConsumableItemEntity,
    val assetName: String,
    val categoryId: Long,
    val latestRecordDate: LocalDate?,
)
