package com.khomenote.data.repository

import com.khomenote.data.local.dao.AssetDao
import com.khomenote.data.local.entity.AssetEntity
import kotlinx.coroutines.flow.Flow

class AssetRepository(private val assetDao: AssetDao) {
    fun getAll(): Flow<List<AssetEntity>> = assetDao.getAll()
    fun getById(assetId: Long): Flow<AssetEntity?> = assetDao.getById(assetId)

    suspend fun save(asset: AssetEntity): Long =
        if (asset.id == 0L) assetDao.insert(asset) else { assetDao.update(asset); asset.id }

    suspend fun delete(asset: AssetEntity) = assetDao.delete(asset)
}
