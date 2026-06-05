package it.danielebufarini.homebudget.ui.screens.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import it.danielebufarini.homebudget.data.CategoryManagementRepository
import it.danielebufarini.homebudget.data.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun EnsureStarterCategoriesSeeded(repository: CategoryManagementRepository) {
    EnsureStarterCategoriesSeeded(seedKey = repository) {
        repository.seedStarterCategoriesIfEmpty()
    }
}

@Composable
private fun EnsureStarterCategoriesSeeded(seedKey: Any, seedStarterCategoriesIfEmpty: suspend () -> Unit) {
    LaunchedEffect(seedKey) {
        withContext(Dispatchers.Default) {
            seedStarterCategoriesIfEmpty()
        }
    }
}

internal fun buildCategoryId(): String = IdGenerator.newId("category")
