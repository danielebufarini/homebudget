package it.homebudget.app.ui.screens

import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.di.initKoin
import it.homebudget.app.localization.loadCategoryNameResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools

class IosCategoryItem(
    val id: String,
    val name: String,
    val iconKey: String,
    val isCustom: Boolean
)

class IosCategoriesSnapshot(
    val categories: List<IosCategoryItem>
)

class IosCategoriesController {
    private val scope = MainScope()
    private var updatesJob: Job? = null

    private val repository: ExpenseRepository by lazy {
        ensureIosCategoriesKoinStarted()
        KoinPlatformTools.defaultContext().get().get()
    }

    fun start(onUpdate: (IosCategoriesSnapshot) -> Unit) {
        if (updatesJob != null) {
            return
        }

        updatesJob = scope.launch {
            repository.insertDefaultCategoriesIfEmpty()
            repository.getAllCategories().collect { categories ->
                val resolveCategoryName = loadCategoryNameResolver()
                onUpdate(
                    IosCategoriesSnapshot(
                        categories = categories.map { category ->
                            IosCategoryItem(
                                id = category.id,
                                name = resolveCategoryName(category.id, category.name, category.isCustom),
                                iconKey = category.icon,
                                isCustom = category.isCustom == 1L
                            )
                        }
                    )
                )
            }
        }
    }

    fun stop() {
        updatesJob?.cancel()
        updatesJob = null
    }

    fun dispose() {
        stop()
        scope.cancel()
    }

    fun insertCategory(name: String, iconKey: String, onComplete: (Boolean) -> Unit) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onComplete(false)
            return
        }

        scope.launch {
            val success = runCatching {
                repository.insertCategory(
                    id = buildCustomCategoryId(),
                    name = trimmedName,
                    icon = iconKey,
                    isCustom = true
                )
            }.isSuccess
            onComplete(success)
        }
    }

    fun updateCategory(id: String, name: String, iconKey: String, onComplete: (Boolean) -> Unit) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            onComplete(false)
            return
        }

        scope.launch {
            val success = runCatching {
                repository.updateCategory(
                    id = id,
                    name = trimmedName,
                    icon = iconKey
                )
            }.isSuccess
            onComplete(success)
        }
    }

    fun deleteCategory(id: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            val success = runCatching {
                repository.deleteCategory(id)
            }.isSuccess
            onComplete(success)
        }
    }
}

private fun ensureIosCategoriesKoinStarted() {
    if (KoinPlatformTools.defaultContext().getOrNull() == null) {
        initKoin()
    }
}
