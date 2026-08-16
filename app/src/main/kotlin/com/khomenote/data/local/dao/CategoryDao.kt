package com.khomenote.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.khomenote.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun getAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM assets WHERE categoryId = :categoryId")
    suspend fun countAssetsUsing(categoryId: Long): Int
}
