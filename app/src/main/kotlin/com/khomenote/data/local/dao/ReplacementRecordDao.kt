package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.khomenote.data.local.entity.ReplacementRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplacementRecordDao {
    @Insert
    suspend fun insert(record: ReplacementRecordEntity): Long

    @Update
    suspend fun update(record: ReplacementRecordEntity)

    @Delete
    suspend fun delete(record: ReplacementRecordEntity)

    @Query("SELECT * FROM replacement_records WHERE consumableItemId = :itemId ORDER BY date DESC")
    fun getByItem(itemId: Long): Flow<List<ReplacementRecordEntity>>
}
