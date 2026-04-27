package it.homebudget.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense

@Composable
internal actual fun AndroidGroupedExpensesRecyclerView(
    groupedExpenses: List<Pair<String, List<Expense>>>,
    categoriesById: Map<String, Category>,
    isGroupedByDate: Boolean,
    modifier: Modifier,
    emptyStateText: String,
    expenseFallbackTitle: String,
    groupsExpandedByDefault: Boolean,
    onOpenExpense: (String) -> Unit,
    onDeleteExpense: ((String) -> Unit)?
) = Unit

@Composable
internal actual fun AndroidCategoriesRecyclerView(
    categories: List<Category>,
    modifier: Modifier
) = Unit
