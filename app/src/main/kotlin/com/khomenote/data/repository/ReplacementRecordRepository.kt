package com.khomenote.data.repository

import com.khomenote.data.local.dao.ReplacementRecordDao
import com.khomenote.data.local.entity.ReplacementRecordEntity
import kotlinx.coroutines.flow.Flow

class ReplacementRecordRepository(private val replacementRecordDao: ReplacementRecordDao) {
    fun getByItem(itemId: Long): Flow<List<ReplacementRecordEntity>> = replacementRecordDao.getByItem(itemId)
    suspend fun add(record: ReplacementRecordEntity): Long = replacementRecordDao.insert(record)
    suspend fun update(record: ReplacementRecordEntity) = replacementRecordDao.update(record)
    suspend fun delete(record: ReplacementRecordEntity) = replacementRecordDao.delete(record)
}
