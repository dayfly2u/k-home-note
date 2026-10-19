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
    fun getWithStatusByAsset(assetId: Long): Flow<List<ConsumableItemWithStatus>> =
        consumableItemDao.getWithStatusByAsset(assetId)

    suspend fun save(item: ConsumableItemEntity): Long =
        if (item.id == 0L) consumableItemDao.insert(item) else { consumableItemDao.update(item); item.id }

    suspend fun delete(item: ConsumableItemEntity) = consumableItemDao.delete(item)

    /** [orderedItems]가 화면에 보여줄 새 순서 — 각 항목의 sortOrder를 그 위치(index)로 다시 매겨서 저장한다. */
    suspend fun reorder(orderedItems: List<ConsumableItemEntity>) =
        consumableItemDao.updateAll(orderedItems.mapIndexed { index, item -> item.copy(sortOrder = index) })
}
