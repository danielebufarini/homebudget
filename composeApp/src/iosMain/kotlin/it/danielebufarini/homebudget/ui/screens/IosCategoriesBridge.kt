package it.danielebufarini.homebudget.ui.screens

import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.database.CATEGORY_TYPE_EXPENSE
import it.danielebufarini.homebudget.di.initKoin
import it.danielebufarini.homebudget.localization.loadCategoryNameResolver
import it.danielebufarini.homebudget.ui.screens.categories.buildCategoryId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools

class IosCategoryItem(
    val id: String,
    val name: String,
    val iconKey: String
)

class IosCategoriesSnapshot(
    val categories: List<IosCategoryItem>
)

class IosBooleanResult(
    val isSuccess: Boolean
)

class IosCategoriesController {
    private val repository: ExpenseRepository by lazy {
        ensureIosCategoriesKoinStarted()
        KoinPlatformTools.defaultContext().get().get()
    }

    fun snapshots() = snapshotsForCategoryType(CATEGORY_TYPE_EXPENSE)

    fun snapshotsForCategoryType(categoryType: String) = flow {
        repository.seedStarterCategoriesIfEmpty()
        emitAll(
            repository.getAllCategories()
                .map { categories ->
                    val resolveCategoryName = loadCategoryNameResolver()
                    val selectableCategories = categories
                        .asSequence()
                        .filter { category ->
                            category.categoryType == categoryType && category.isArchived != 1L
                        }
                        .map { category ->
                            IosCategoryItem(
                                id = category.id,
                                name = resolveCategoryName(category.id, category.name),
                                iconKey = category.icon
                            )
                        }
                        .toList()
                    IosCategoriesSnapshot(categories = selectableCategories)
                }
        )
    }.flowOn(Dispatchers.Default)

    suspend fun insertCategory(name: String, iconKey: String): IosBooleanResult {
        return IosBooleanResult(insertCategoryAndReturnId(name, iconKey) != null)
    }

    suspend fun insertCategoryAndReturnId(
        name: String,
        iconKey: String
    ): String? {
        return insertCategoryAndReturnIdForCategoryType(name, iconKey, CATEGORY_TYPE_EXPENSE)
    }

    suspend fun insertCategoryAndReturnIdForCategoryType(
        name: String,
        iconKey: String,
        categoryType: String
    ): String? {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return null
        }

        val categoryId = buildCategoryId()
        val success = withContext(Dispatchers.Default) {
            runCatching {
                repository.insertCategory(
                    id = categoryId,
                    name = trimmedName,
                    icon = iconKey,
                    categoryType = categoryType
                )
            }.isSuccess
        }
        return if (success) categoryId else null
    }

    suspend fun updateCategory(id: String, name: String, iconKey: String): IosBooleanResult {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return IosBooleanResult(false)
        }

        val success = withContext(Dispatchers.Default) {
            runCatching {
                repository.updateCategory(
                    id = id,
                    name = trimmedName,
                    icon = iconKey
                )
            }.isSuccess
        }
        return IosBooleanResult(success)
    }

    suspend fun deleteCategory(id: String): IosBooleanResult {
        val success = withContext(Dispatchers.Default) {
            runCatching {
                repository.deleteCategory(id)
            }.isSuccess
        }
        return IosBooleanResult(success)
    }
}

private fun ensureIosCategoriesKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}
