package com.queststack.data.repository

import com.queststack.data.db.Category
import com.queststack.data.db.CategoryDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll()

    override suspend fun addCategory(name: String) {
        val maxSortOrder = categoryDao.observeAll().first().maxOfOrNull { it.sortOrder } ?: -1
        categoryDao.insert(Category(name = name, sortOrder = maxSortOrder + 1))
    }

    override suspend fun renameCategory(category: Category, newName: String) {
        categoryDao.update(category.copy(name = newName))
    }

    override suspend fun deleteCategory(category: Category) {
        if (categoryDao.countQuestions(category.id) > 0) {
            throw IllegalStateException("分类下还有题目")
        }
        categoryDao.delete(category)
    }
}
