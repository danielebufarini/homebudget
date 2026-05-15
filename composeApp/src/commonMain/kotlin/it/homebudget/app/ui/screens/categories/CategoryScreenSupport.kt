package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator

@Composable
internal fun EnsureStarterCategoriesSeeded(repository: ExpenseRepository) {
    LaunchedEffect(repository) {
        repository.seedStarterCategoriesIfEmpty()
    }
}

internal fun buildCategoryId(): String = IdGenerator.newId("category")
