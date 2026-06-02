package it.danielebufarini.homebudget.ui.screens.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import it.danielebufarini.homebudget.data.ExpenseRepository
import it.danielebufarini.homebudget.data.IdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun EnsureStarterCategoriesSeeded(repository: ExpenseRepository) {
    LaunchedEffect(repository) {
        withContext(Dispatchers.Default) {
            repository.seedStarterCategoriesIfEmpty()
        }
    }
}

internal fun buildCategoryId(): String = IdGenerator.newId("category")
