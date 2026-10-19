package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.khomenote.data.local.dto.RepairRecordWithAsset
import com.khomenote.data.local.entity.RepairRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RepairRecordDao {
    @Insert
    suspend fun insert(record: RepairRecordEntity): Long

    @Update
    suspend fun update(record: RepairRecordEntity)

    @Delete
    suspend fun delete(record: RepairRecordEntity)

    @Query("SELECT * FROM repair_records WHERE assetId = :assetId ORDER BY date DESC, id DESC")
    fun getByAsset(assetId: Long): Flow<List<RepairRecordEntity>>

    // 모아보기 카드의 "일회성 N" 개수 계산용 — 대상 구분 없이 전부.
    @Query("SELECT * FROM repair_records")
    fun getAll(): Flow<List<RepairRecordEntity>>

    // reminderLeadDays가 있는(알림 받기를 켜둔) 일회성작업만 — 대시보드/알림 대상.
    @Query(
        """
        SELECT rr.*, a.name AS assetName, a.categoryId AS categoryId
        FROM repair_records rr
        INNER JOIN assets a ON a.id = rr.assetId
        WHERE rr.reminderLeadDays IS NOT NULL
        """
    )
    fun getPendingReminders(): Flow<List<RepairRecordWithAsset>>
}
