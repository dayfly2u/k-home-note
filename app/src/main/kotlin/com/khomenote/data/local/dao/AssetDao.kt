package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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

    @Query("SELECT * FROM assets ORDER BY sortOrder ASC, id ASC")
    fun getAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM assets WHERE id = :assetId")
    fun getById(assetId: Long): Flow<AssetEntity?>
}
