package com.khomenote.data.local.dto

import androidx.room.Embedded
import com.khomenote.data.local.entity.AssetEntity

data class AssetWithCategory(
    @Embedded val asset: AssetEntity,
    val categoryName: String,
    val categoryEmoji: String,
)
