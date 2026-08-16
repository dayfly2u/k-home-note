package com.khomenote.di

import android.content.Context
import com.khomenote.data.local.AppDatabase
import com.khomenote.data.repository.AssetRepository
import com.khomenote.data.repository.CategoryRepository
import com.khomenote.data.repository.ConsumableItemRepository
import com.khomenote.data.repository.ReplacementRecordRepository
import com.khomenote.photo.PhotoStorage

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.getInstance(context)
    val assetRepository: AssetRepository = AssetRepository(database.assetDao())
    val consumableItemRepository: ConsumableItemRepository = ConsumableItemRepository(database.consumableItemDao())
    val replacementRecordRepository: ReplacementRecordRepository = ReplacementRecordRepository(database.replacementRecordDao())
    val categoryRepository: CategoryRepository = CategoryRepository(database.categoryDao())
    val photoStorage: PhotoStorage = PhotoStorage(context)

    companion object {
        @Volatile private var instance: AppContainer? = null

        fun getInstance(context: Context): AppContainer =
            instance ?: synchronized(this) {
                instance ?: AppContainer(context.applicationContext).also { instance = it }
            }
    }
}
