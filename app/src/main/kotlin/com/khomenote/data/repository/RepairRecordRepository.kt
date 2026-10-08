package com.khomenote.data.repository

import com.khomenote.data.local.dao.RepairRecordDao
import com.khomenote.data.local.dto.RepairRecordWithAsset
import com.khomenote.data.local.entity.RepairRecordEntity
import kotlinx.coroutines.flow.Flow

class RepairRecordRepository(private val repairRecordDao: RepairRecordDao) {
    fun getByAsset(assetId: Long): Flow<List<RepairRecordEntity>> = repairRecordDao.getByAsset(assetId)

    fun getPendingReminders(): Flow<List<RepairRecordWithAsset>> = repairRecordDao.getPendingReminders()

    suspend fun save(record: RepairRecordEntity): Long =
        if (record.id == 0L) repairRecordDao.insert(record) else { repairRecordDao.update(record); record.id }

    suspend fun delete(record: RepairRecordEntity) = repairRecordDao.delete(record)
}
