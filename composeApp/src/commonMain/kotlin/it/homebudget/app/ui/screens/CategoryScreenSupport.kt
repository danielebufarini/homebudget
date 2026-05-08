package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.IdGenerator

@Composable
internal fun EnsureDefaultCategoriesInserted(repository: ExpenseRepository) {
    LaunchedEffect(repository) {
        repository.insertDefaultCategoriesIfEmpty()
    }
}

internal fun buildCustomCategoryId(): String = IdGenerator.newId("custom-category")
