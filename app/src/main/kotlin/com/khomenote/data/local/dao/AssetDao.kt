package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.khomenote.data.local.dto.AssetWithCategory
import com.khomenote.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    @Insert
    suspend fun insert(asset: AssetEntity): Long

    @Update
    suspend fun update(asset: AssetEntity)

    @Update
    suspend fun updateAll(assets: List<AssetEntity>)

    @Delete
    suspend fun delete(asset: AssetEntity)

    @Query("SELECT * FROM assets WHERE id = :assetId")
    fun getById(assetId: Long): Flow<AssetEntity?>

    @Query("SELECT * FROM assets ORDER BY sortOrder ASC, id ASC")
    fun getAll(): Flow<List<AssetEntity>>

    @Query(
        """
        SELECT
            a.*,
            c.name AS categoryName,
            c.emoji AS categoryEmoji
        FROM assets a
        INNER JOIN categories c ON c.id = a.categoryId
        ORDER BY a.sortOrder ASC, a.id ASC
        """
    )
    fun getAllWithCategory(): Flow<List<AssetWithCategory>>
}
