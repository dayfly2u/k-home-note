package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.ConsumableItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsumableItemDao {
    @Insert
    suspend fun insert(item: ConsumableItemEntity): Long

    @Update
    suspend fun update(item: ConsumableItemEntity)

    @Update
    suspend fun updateAll(items: List<ConsumableItemEntity>)

    @Delete
    suspend fun delete(item: ConsumableItemEntity)

    @Query("SELECT * FROM consumable_items WHERE id = :itemId")
    fun getById(itemId: Long): Flow<ConsumableItemEntity?>

    @Query("SELECT * FROM consumable_items WHERE assetId = :assetId ORDER BY sortOrder ASC, id ASC")
    fun getByAsset(assetId: Long): Flow<List<ConsumableItemEntity>>

    @Query(
        """
        SELECT
            ci.*,
            a.name AS assetName,
            a.categoryId AS categoryId,
            MAX(rr.date) AS latestRecordDate
        FROM consumable_items ci
        INNER JOIN assets a ON a.id = ci.assetId
        LEFT JOIN replacement_records rr ON rr.consumableItemId = ci.id
        GROUP BY ci.id
        ORDER BY ci.name ASC
        """
    )
    fun getAllWithStatus(): Flow<List<ConsumableItemWithStatus>>

    @Query(
        """
        SELECT
            ci.*,
            a.name AS assetName,
            a.categoryId AS categoryId,
            MAX(rr.date) AS latestRecordDate
        FROM consumable_items ci
        INNER JOIN assets a ON a.id = ci.assetId
        LEFT JOIN replacement_records rr ON rr.consumableItemId = ci.id
        WHERE ci.id = :itemId
        GROUP BY ci.id
        """
    )
    fun getWithStatusById(itemId: Long): Flow<ConsumableItemWithStatus?>

    // 대상 상세 화면용 — 표시 순서(sortOrder)를 그대로 유지하면서 다음 예정일 계산에 쓸 최근 완료일도 함께 가져온다.
    @Query(
        """
        SELECT
            ci.*,
            a.name AS assetName,
            a.categoryId AS categoryId,
            MAX(rr.date) AS latestRecordDate
        FROM consumable_items ci
        INNER JOIN assets a ON a.id = ci.assetId
        LEFT JOIN replacement_records rr ON rr.consumableItemId = ci.id
        WHERE ci.assetId = :assetId
        GROUP BY ci.id
        ORDER BY ci.sortOrder ASC, ci.id ASC
        """
    )
    fun getWithStatusByAsset(assetId: Long): Flow<List<ConsumableItemWithStatus>>
}
