package com.khomenote.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "consumable_items",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["assetId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("assetId")],
)
data class ConsumableItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val name: String,
    val cycleDays: Int,
    val reminderLeadDays: Int = 7,
    val memo: String? = null,
    val createdAt: LocalDate = LocalDate.now(),
    val lastNotifiedForDate: LocalDate? = null,
    val sortOrder: Int = 0,
)
