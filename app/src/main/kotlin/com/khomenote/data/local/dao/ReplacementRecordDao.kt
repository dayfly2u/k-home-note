package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.khomenote.data.local.dto.ReplacementRecordWithItem
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

    // 홈 달력의 "한 일" — 모든 주기성작업의 이력을 작업·대상 이름과 함께.
    @Query(
        """
        SELECT r.*, ci.name AS itemName, a.name AS assetName, a.categoryId AS categoryId
        FROM replacement_records r
        INNER JOIN consumable_items ci ON ci.id = r.consumableItemId
        INNER JOIN assets a ON a.id = ci.assetId
        ORDER BY r.date DESC, r.id DESC
        """
    )
    fun getAllWithItem(): Flow<List<ReplacementRecordWithItem>>
}
