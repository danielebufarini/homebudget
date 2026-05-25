package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator
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
