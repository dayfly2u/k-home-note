package com.khomenote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.khomenote.data.local.dao.AssetDao
import com.khomenote.data.local.dao.ConsumableItemDao
import com.khomenote.data.local.dao.ReplacementRecordDao
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.ReplacementRecordEntity

@Database(
    entities = [AssetEntity::class, ConsumableItemEntity::class, ReplacementRecordEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun consumableItemDao(): ConsumableItemDao
    abstract fun replacementRecordDao(): ReplacementRecordDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "k_home_note.db")
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}
