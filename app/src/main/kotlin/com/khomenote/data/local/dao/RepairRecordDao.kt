package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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
}
