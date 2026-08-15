package com.khomenote.data.repository

import com.khomenote.data.local.dao.ConsumableItemDao
import com.khomenote.data.local.dto.ConsumableItemWithStatus
import com.khomenote.data.local.entity.ConsumableItemEntity
import kotlinx.coroutines.flow.Flow

class ConsumableItemRepository(private val consumableItemDao: ConsumableItemDao) {
    fun getById(itemId: Long): Flow<ConsumableItemEntity?> = consumableItemDao.getById(itemId)
    fun getByAsset(assetId: Long): Flow<List<ConsumableItemEntity>> = consumableItemDao.getByAsset(assetId)
    fun getAllWithStatus(): Flow<List<ConsumableItemWithStatus>> = consumableItemDao.getAllWithStatus()
    fun getWithStatusById(itemId: Long): Flow<ConsumableItemWithStatus?> = consumableItemDao.getWithStatusById(itemId)

    suspend fun save(item: ConsumableItemEntity): Long =
        if (item.id == 0L) consumableItemDao.insert(item) else { consumableItemDao.update(item); item.id }

    suspend fun delete(item: ConsumableItemEntity) = consumableItemDao.delete(item)
}
