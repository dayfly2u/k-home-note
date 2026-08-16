package com.khomenote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.khomenote.data.local.dao.AssetDao
import com.khomenote.data.local.dao.CategoryDao
import com.khomenote.data.local.dao.ConsumableItemDao
import com.khomenote.data.local.dao.ReplacementRecordDao
import com.khomenote.data.local.entity.AssetEntity
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.local.entity.ConsumableItemEntity
import com.khomenote.data.local.entity.ReplacementRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AssetEntity::class,
        ConsumableItemEntity::class,
        ReplacementRecordEntity::class,
        CategoryEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun consumableItemDao(): ConsumableItemDao
    abstract fun replacementRecordDao(): ReplacementRecordDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    lateinit var newInstance: AppDatabase
                    newInstance = Room.databaseBuilder(context, AppDatabase::class.java, "k_home_note.db")
                        .fallbackToDestructiveMigration(dropAllTables = true)
                        .addCallback(object : RoomDatabase.Callback() {
                            // DB 파일이 처음 만들어질 때(최초 설치 또는 destructive migration으로
                            // 테이블이 새로 생성될 때) 기본 카테고리 3개를 씨드한다.
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                CoroutineScope(Dispatchers.IO).launch {
                                    val categoryDao = newInstance.categoryDao()
                                    categoryDao.insert(CategoryEntity(name = "가전", emoji = "🔌", sortOrder = 0))
                                    categoryDao.insert(CategoryEntity(name = "자동차", emoji = "🚗", sortOrder = 1))
                                    categoryDao.insert(CategoryEntity(name = "카드", emoji = "💳", sortOrder = 2))
                                }
                            }
                        })
                        .build()
                    instance = newInstance
                    newInstance
                }
            }
    }
}
