package com.khomenote.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 소모품 교체 주기와 무관하게, 검사/점검 중 발견된 문제를 고친 일회성 정비 이력.
 * 알림/주기 개념이 없다 — 그냥 "언제, 뭘, 얼마에 고쳤다"만 남긴다.
 */
@Entity(
    tableName = "repair_records",
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
data class RepairRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val name: String,
    val date: LocalDate,
    val price: Long? = null,
    val memo: String? = null,
)
