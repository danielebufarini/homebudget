package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.expense
import homebudget.composeapp.generated.resources.no_expenses_for_day
import homebudget.composeapp.generated.resources.short_month_names
import homebudget.composeapp.generated.resources.unknown_category
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigIntegerOf
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.getPlatform
import it.homebudget.app.localization.rememberCategoryNameResolver
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class DayExpensesScreen(
    private val year: Int,
    private val month: Int,
    private val day: Int
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            showNavigationChrome = true,
            onBack = { navigator?.pop() },
            onOpenExpense = { expenseId ->
                navigator?.push(AddExpenseScreen(expenseId))
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RouteContent(
        showNavigationChrome: Boolean,
        onBack: () -> Unit,
        onOpenExpense: (String) -> Unit
    ) {
        val repository: ExpenseRepository = koinInject()
        val isIos = remember { getPlatform().isIos }
        val backLabel = stringResource(Res.string.back)
        val currencySymbol = stringResource(Res.string.currency_symbol)
        val expenseFallbackTitle = stringResource(Res.string.expense)
        val emptyStateText = stringResource(Res.string.no_expenses_for_day)
        val unknownCategoryLabel = stringResource(Res.string.unknown_category)
        val shortMonthNames = stringArrayResource(Res.array.short_month_names)
        val resolveCategoryName = rememberCategoryNameResolver()
        val targetDate = remember(year, month, day) { LocalDate(year, month, day) }
        val title = remember(targetDate, shortMonthNames) {
            formatExpenseDateGroupTitle(targetDate, shortMonthNames.toList())
        }

        val expenses by repository.getAllExpenses().collectAsState(initial = emptyList())
        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val categoriesById = remember(categories) { categories.associateBy { it.id } }

        EnsureDefaultCategoriesInserted(repository)

        val filteredExpenses = remember(expenses, targetDate) {
            expenses
                .filter { epochMillisToLocalDate(it.date) == targetDate }
                .sortedByDescending(Expense::date)
        }
        val totalAmount = remember(filteredExpenses) {
            filteredExpenses.sumBigIntegerOf(Expense::amount)
        }

        val content: @Composable (PaddingValues) -> Unit = { padding ->
            DayExpensesList(
                expenses = filteredExpenses,
                categoriesById = categoriesById,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                emptyStateText = emptyStateText,
                expenseFallbackTitle = expenseFallbackTitle,
                currencySymbol = currencySymbol,
                unknownCategoryLabel = unknownCategoryLabel,
                onOpenExpense = onOpenExpense,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name, category.isCustom)
                }
            )
        }

        if (showNavigationChrome) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(title)
                                Text(
                                    text = formatAmount(totalAmount, currencySymbol),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onBack) {
                                    Text(backLabel)
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                content(padding)
            }
        } else {
            content(PaddingValues())
        }
    }
}

@Composable
private fun DayExpensesList(
    expenses: List<Expense>,
    categoriesById: Map<String, Category>,
    modifier: Modifier,
    emptyStateText: String,
    expenseFallbackTitle: String,
    currencySymbol: String,
    unknownCategoryLabel: String,
    onOpenExpense: (String) -> Unit,
    resolveCategoryName: (Category) -> String
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (expenses.isEmpty()) {
            item {
                PlatformCard {
                    Text(
                        text = emptyStateText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            return@LazyColumn
        }

        item {
            PlatformCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    expenses.forEach { expense ->
                        val row = groupedExpenseRowPresentation(
                            expense = expense,
                            categoriesById = categoriesById,
                            isGroupedByDate = true,
                            expenseFallbackTitle = expenseFallbackTitle,
                            unknownCategoryLabel = unknownCategoryLabel,
                            resolveCategoryName = resolveCategoryName
                        )
                        ExpenseListItemRow(
                            title = row.title,
                            subtitleText = row.subtitleText,
                            amountText = formatAmount(expense.amount, currencySymbol),
                            categoryColorKey = row.categoryColorKey,
                            categoryIconKey = row.categoryIconKey,
                            isRecurring = row.isRecurring,
                            onClick = { onOpenExpense(expense.id) }
                        )
                    }
                }
            }
        }
    }
}
