package com.khomenote.data.repository

import com.khomenote.data.local.dao.AssetDao
import com.khomenote.data.local.dto.AssetWithCategory
import com.khomenote.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

class AssetRepository(private val assetDao: AssetDao) {
    fun getAll(): Flow<List<AssetEntity>> = assetDao.getAll()
    fun getAllWithCategory(): Flow<List<AssetWithCategory>> = assetDao.getAllWithCategory()
    fun getById(assetId: Long): Flow<AssetEntity?> = assetDao.getById(assetId)

    suspend fun save(asset: AssetEntity): Long =
        if (asset.id == 0L) assetDao.insert(asset) else { assetDao.update(asset); asset.id }

    suspend fun delete(asset: AssetEntity) = assetDao.delete(asset)

    /** [orderedAssets]가 화면에 보여줄 새 순서 — 각 항목의 sortOrder를 그 위치(index)로 다시 매겨서 저장한다. */
    suspend fun reorder(orderedAssets: List<AssetEntity>) =
        assetDao.updateAll(orderedAssets.mapIndexed { index, asset -> asset.copy(sortOrder = index) })
}
