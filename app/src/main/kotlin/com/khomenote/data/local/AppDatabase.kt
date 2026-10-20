package com.khomenote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.khomenote.data.local.dao.AssetDao
import com.khomenote.data.local.dao.CategoryDao
import com.khomenote.data.local.dao.ConsumableItemDao
import com.khomenote.data.local.dao.RepairRecordDao
import com.khomenote.data.local.dao.ReplacementRecordDao
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.RepairRecordEntity
import com.khomenote.data.local.entity.ReplacementRecordEntity

@Database(
    entities = [
        AssetEntity::class,
        ConsumableItemEntity::class,
        ReplacementRecordEntity::class,
        CategoryEntity::class,
        RepairRecordEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun consumableItemDao(): ConsumableItemDao
    abstract fun replacementRecordDao(): ReplacementRecordDao
    abstract fun categoryDao(): CategoryDao
    abstract fun repairRecordDao(): RepairRecordDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "k_home_note.db")
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .build()
                    .also { instance = it }
            }

        /**
         * 백업/복원 시 DB 파일을 직접 다루기 전에 열려 있는 연결을 닫는다 —
         * 다음 [getInstance] 호출에서 새로 열린다.
         */
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}
