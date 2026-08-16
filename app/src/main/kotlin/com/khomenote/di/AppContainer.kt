package com.khomenote.di

import android.content.Context
import com.khomenote.data.local.AppDatabase
import com.khomenote.data.local.entity.CategoryEntity
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.RepairRecordRepository
import com.khomenote.data.repository.ReplacementRecordRepository
import com.khomenote.photo.PhotoStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.getInstance(context)
    val assetRepository: AssetRepository = AssetRepository(database.assetDao())
    val consumableItemRepository: ConsumableItemRepository = ConsumableItemRepository(database.consumableItemDao())
    val replacementRecordRepository: ReplacementRecordRepository = ReplacementRecordRepository(database.replacementRecordDao())
    val categoryRepository: CategoryRepository = CategoryRepository(database.categoryDao())
    val repairRecordRepository: RepairRecordRepository = RepairRecordRepository(database.repairRecordDao())
    val photoStorage: PhotoStorage = PhotoStorage(context)

    init {
        // RoomDatabase.Callback.onCreate()는 DB 파일이 처음 만들어질 때만 한 번 불리고,
        // fallbackToDestructiveMigration으로 기존 파일 안의 테이블만 새로 만드는 경우에는
        // 다시 불리지 않는다 — 그래서 여기서 "카테고리가 비어있으면 채운다" 방식으로 시드한다.
        // AppContainer가 프로세스당 한 번만 생성되고, 비어있을 때만 채우므로 중복 삽입 걱정 없다.
        CoroutineScope(Dispatchers.IO).launch {
            if (categoryRepository.getAll().first().isEmpty()) {
                categoryRepository.save(CategoryEntity(name = "가전", emoji = "🔌", sortOrder = 0))
                categoryRepository.save(CategoryEntity(name = "자동차", emoji = "🚗", sortOrder = 1))
                categoryRepository.save(CategoryEntity(name = "카드", emoji = "💳", sortOrder = 2))
            }
        }
    }

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
