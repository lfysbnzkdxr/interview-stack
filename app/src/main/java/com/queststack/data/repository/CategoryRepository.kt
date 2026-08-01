package com.queststack.data.repository

import com.queststack.data.db.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun addCategory(name: String)
    suspend fun renameCategory(category: Category, newName: String)
    suspend fun deleteCategory(category: Category)
}
