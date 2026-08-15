package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.khomenote.data.local.entity.ReplacementRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplacementRecordDao {
    @Insert
    suspend fun insert(record: ReplacementRecordEntity): Long

    @Delete
    suspend fun delete(record: ReplacementRecordEntity)

    @Query("SELECT * FROM replacement_records WHERE consumableItemId = :itemId ORDER BY date DESC")
    fun getByItem(itemId: Long): Flow<List<ReplacementRecordEntity>>
}
