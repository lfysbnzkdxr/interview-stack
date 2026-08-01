package com.queststack.data

import com.queststack.data.db.Category
import com.queststack.data.db.CategoryDao
import kotlinx.coroutines.flow.first

object Seed {
    suspend fun seedCategories(categoryDao: CategoryDao) {
        val isEmpty = categoryDao.observeAll().first().isEmpty()
        if (isEmpty) {
            val names = listOf("Android", "Kotlin", "算法", "系统", "网络")
            names.forEachIndexed { index, name ->
                categoryDao.insert(Category(name = name, sortOrder = index))
            }
        }
    }
}
