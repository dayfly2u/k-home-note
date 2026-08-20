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
    // null이면 cycleDays 기반 주기(간격), 값이 있으면 "매달 이 날짜" 고정 주기 — 두 방식은 배타적이다.
    val monthlyDueDay: Int? = null,
    val reminderLeadDays: Int = 7,
    val memo: String? = null,
    val createdAt: LocalDate = LocalDate.now(),
    // 알림은 reminderLeadDays를 절반씩 줄인 여러 시점(예: 14일 전 -> 14,7,3,1일 전)마다 온다.
    // lastNotifiedForDate는 "그 알림들이 어느 교체 주기(다음 예정일)에 대한 것인지", lastNotifiedMilestoneDays는
    // "그 주기 안에서 가장 최근에 보낸 시점(며칠 전)"을 기록해서 같은 시점에 중복 알림이 안 가게 한다.
    val lastNotifiedForDate: LocalDate? = null,
    val lastNotifiedMilestoneDays: Int? = null,
    val sortOrder: Int = 0,
)
