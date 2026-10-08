package com.khomenote.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 소모품 교체 주기와 무관한 일회성작업 — 예정된 일정(알림 받기 켜짐)이거나, 이미 끝난 일을
 * 그냥 기록만 해두는 로그(알림 받기 꺼짐)다.
 *
 * reminderLeadDays가 null이면 알림 대상이 아니다 — 대시보드/알림에 전혀 나타나지 않고
 * 과거 기록 그대로 자산 상세의 "일회성작업" 목록에만 남는다(기존 정비이력과 동일한 동작).
 * reminderLeadDays가 있으면 "예정된 일" 취급 — 대시보드에 표시되고 소모품과 같은 방식(리드타임을
 * 절반씩 줄인 여러 시점)으로 알림이 온다. 처리를 끝냈으면 이 값을 다시 비워서(알림 받기 끄기)
 * 대시보드/알림 목록에서 빠지게 한다 — 소모품처럼 "다음 주기"로 자동으로 넘어가지 않는다(일회성).
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
    val reminderLeadDays: Int? = null,
    val lastNotifiedForDate: LocalDate? = null,
    val lastNotifiedMilestoneDays: Int? = null,
)
