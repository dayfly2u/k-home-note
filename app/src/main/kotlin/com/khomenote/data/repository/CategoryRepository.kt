package com.khomenote.data.repository

import com.khomenote.data.local.dao.CategoryDao
import com.khomenote.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CategoryRepository(private val categoryDao: CategoryDao) {
    fun getAll(): Flow<List<CategoryEntity>> = categoryDao.getAll()

    suspend fun save(category: CategoryEntity): Long =
        if (category.id == 0L) categoryDao.insert(category) else { categoryDao.update(category); category.id }

    suspend fun delete(category: CategoryEntity) = categoryDao.delete(category)

    suspend fun isInUse(categoryId: Long): Boolean = categoryDao.countAssetsUsing(categoryId) > 0

    /** [orderedCategories]가 화면에 보여줄 새 순서 — 각 항목의 sortOrder를 그 위치(index)로 다시 매겨서 저장한다. */
    suspend fun reorder(orderedCategories: List<CategoryEntity>) =
        categoryDao.updateAll(orderedCategories.mapIndexed { index, category -> category.copy(sortOrder = index) })

    /** [candidate]가 [excludingId]를 제외한 기존 카테고리와 대소문자 무시하고 겹치지 않으면 true. */
    suspend fun isNameAvailable(candidate: String, excludingId: Long? = null): Boolean {
        val trimmed = candidate.trim()
        if (trimmed.isEmpty()) return false
        return getAll().first().none { it.id != excludingId && it.name.equals(trimmed, ignoreCase = true) }
    }
}
