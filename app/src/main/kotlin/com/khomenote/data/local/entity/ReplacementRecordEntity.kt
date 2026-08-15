package com.khomenote.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "replacement_records",
    foreignKeys = [
        ForeignKey(
            entity = ConsumableItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["consumableItemId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("consumableItemId")],
)
data class ReplacementRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val consumableItemId: Long,
    val date: LocalDate,
    val price: Long? = null,
    val vendor: String? = null,
    val photoUri: String? = null,
    val memo: String? = null,
)
