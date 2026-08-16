package com.khomenote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: AssetCategory,
    val memo: String? = null,
    val createdAt: LocalDate = LocalDate.now(),
    val sortOrder: Int = 0,
)
