package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import it.homebudget.app.data.ExpenseRepository
import kotlin.random.Random
import kotlin.time.Clock

@Composable
internal fun EnsureDefaultCategoriesInserted(repository: ExpenseRepository) {
    LaunchedEffect(repository) {
        repository.insertDefaultCategoriesIfEmpty()
    }
}

internal fun buildCustomCategoryId(): String {
    return "custom_${Clock.System.now().toEpochMilliseconds()}_${Random.nextLong()}"
}
