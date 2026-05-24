package it.homebudget.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_expense
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.by_category
import homebudget.composeapp.generated.resources.by_date
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.delete_item_confirmation_message
import homebudget.composeapp.generated.resources.delete_recurring_item_confirmation_message
import homebudget.composeapp.generated.resources.full_month_names
import homebudget.composeapp.generated.resources.short_month_names
import homebudget.composeapp.generated.resources.unknown_category
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.monthBounds
import it.homebudget.app.database.Expense
import it.homebudget.app.getPlatform
import it.homebudget.app.localization.rememberCategoryNameResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

abstract class BaseGroupedExpensesScreen(
    private val year: Int,
    private val month: Int,
    private val searchQuery: String = ""
) : Screen {

    @Composable
    protected abstract fun screenTitle(monthName: String): String
    @Composable
    protected abstract fun emptyStateText(): String
    @Composable
    protected abstract fun expenseFallbackTitle(): String
    protected abstract fun includeExpense(expense: Expense): Boolean

    protected open fun centerAlignedTitle(): Boolean = false
    protected open fun groupsExpandedByDefault(): Boolean = false
    protected open fun includeCategory(categoryName: String): Boolean = true
    protected open fun canDeleteExpense(): Boolean = true
    protected open fun canAddExpense(): Boolean = false
    protected open fun showMonthNavigationControls(): Boolean = true

    @Composable
    protected open fun sectionHeaderContainerColor(): Color = Color.Transparent

    @Composable
    protected open fun sectionHeaderContentColor(): Color = MaterialTheme.colorScheme.onSurface

    @Composable
    protected open fun sectionHeaderTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge

    @Composable
    protected open fun sectionHeaderIconTint(): Color? = null

    @Composable
    protected open fun sectionHeaderChevronContainerColor(): Color? = null

    @Composable
    protected open fun sectionHeaderChevronContentColor(): Color? = null

    @Composable
    protected open fun monthNavigationDescriptor(): String? = null

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            showNavigationChrome = true,
            onBack = { navigator?.pop() },
            onAddExpense = {
                navigator?.push(
                    AddTransactionScreen(
                        initialKind = TransactionEditorKind.Expense
                    )
                )
            },
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
        onAddExpense: () -> Unit,
        onOpenExpense: (String) -> Unit
    ) {
        val repository: ExpenseRepository = koinInject()
        val isIos = remember { getPlatform().isIos }
        val scope = rememberCoroutineScope()
        val addExpenseLabel = stringResource(Res.string.add_expense)
        val backLabel = stringResource(Res.string.back)
        val byCategoryLabel = stringResource(Res.string.by_category)
        val byDateLabel = stringResource(Res.string.by_date)
        val currencySymbol = stringResource(Res.string.currency_symbol)
        val deleteLabel = stringResource(Res.string.delete)
        val deleteItemConfirmationMessageTemplate = stringResource(Res.string.delete_item_confirmation_message)
        val recurringExpenseDeleteMessageTemplate = stringResource(Res.string.delete_recurring_item_confirmation_message)
        val unknownCategoryLabel = stringResource(Res.string.unknown_category)
        val fullMonthNames = stringArrayResource(Res.array.full_month_names)
        val shortMonthNames = stringArrayResource(Res.array.short_month_names)
        val shortMonthNamesList = remember(shortMonthNames) { shortMonthNames.toList() }
        val resolveCategoryName = rememberCategoryNameResolver()
        val emptyStateText = emptyStateText()
        val expenseFallbackTitle = expenseFallbackTitle()
        val navigationDescriptor = monthNavigationDescriptor()
        var selectedMonth by remember(year, month) { mutableStateOf(MonthCursor(year, month)) }
        var groupingMode by remember { mutableStateOf(ExpenseGroupingMode.ByCategory) }
        var pendingExpenseDelete by remember { mutableStateOf<Expense?>(null) }
        val (monthStartMillis, monthEndMillis) = remember(selectedMonth) {
            monthBounds(selectedMonth.year, selectedMonth.month)
        }
        val searchMode = searchQuery.isNotBlank()
        val expensesFlow = remember(repository, monthStartMillis, monthEndMillis, searchMode) {
            if (searchMode) {
                repository.getAllExpenses()
            } else {
                repository.getExpensesBetween(monthStartMillis, monthEndMillis)
            }
        }
        val categoriesFlow = remember(repository) {
            repository.getAllCategories()
        }

        EnsureStarterCategoriesSeeded(repository)

        val groupedExpensesFlow = remember(
            expensesFlow,
            categoriesFlow,
            groupingMode,
            resolveCategoryName,
            unknownCategoryLabel,
            shortMonthNamesList,
            searchQuery,
            currencySymbol
        ) {
            combine(expensesFlow, categoriesFlow) { expenses, categories ->
                val categoriesById = categories.associateBy { it.id }
                buildGroupedExpensesState(
                    expenses = expenses,
                    categoriesById = categoriesById,
                    groupingMode = groupingMode,
                    includeExpense = ::includeExpense,
                    includeCategory = ::includeCategory,
                    resolveCategoryName = { category ->
                        resolveCategoryName(category.id, category.name)
                    },
                    unknownCategoryLabel = unknownCategoryLabel,
                    shortMonthNames = shortMonthNamesList,
                    searchQuery = searchQuery,
                    currencySymbol = currencySymbol
                )
            }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }
        val groupedExpensesState by groupedExpensesFlow.collectAsState(initial = emptyGroupedExpensesState())
        val groupedExpenses = groupedExpensesState.sections
        val totalAmount = groupedExpensesState.totalAmount
        val categoriesById = groupedExpensesState.categoriesById
        val deleteExpenseAction: ((String) -> Unit)? = if (canDeleteExpense()) {
            deleteAction@{ expenseId ->
                val expense = groupedExpensesState.visibleExpenses.find { it.id == expenseId }
                    ?: return@deleteAction
                pendingExpenseDelete = expense
            }
        } else {
            null
        }
        if (showNavigationChrome) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets.systemBars,
                topBar = {
                    GroupedExpensesTopBar(
                        selectedMonth = selectedMonth,
                        title = screenTitle(monthName(selectedMonth.month, fullMonthNames)),
                        navigationDescriptor = navigationDescriptor,
                        totalAmount = totalAmount,
                        currencySymbol = currencySymbol,
                        isIos = isIos,
                        canAddExpense = canAddExpense(),
                        showMonthNavigationControls = showMonthNavigationControls(),
                        backLabel = backLabel,
                        addExpenseLabel = addExpenseLabel,
                        onBack = onBack,
                        onAddExpense = onAddExpense,
                        onPreviousMonth = { selectedMonth = selectedMonth.previous() },
                        onNextMonth = { selectedMonth = selectedMonth.next() }
                    )
                }
            ) { padding ->
                val showFloatingBottomControls = !isIos
                val bottomControlClearance = if (showFloatingBottomControls) 88.dp else 0.dp
                val listContentPadding = edgeToEdgeListContentPadding(
                    scaffoldPadding = padding,
                    bottom = 16.dp + bottomControlClearance
                )
                val bottomControlsPadding = padding.calculateBottomPadding() + 16.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .monthSwipeNavigation(
                            enabled = showMonthNavigationControls(),
                            onPreviousMonth = { selectedMonth = selectedMonth.previous() },
                            onNextMonth = { selectedMonth = selectedMonth.next() }
                        )
                ) {
                    GroupedExpensesContent(
                        groupedExpenses = groupedExpenses,
                        categoriesById = categoriesById,
                        modifier = Modifier.fillMaxSize(),
                        groupingMode = groupingMode,
                        onGroupingModeChange = { groupingMode = it },
                        onOpenExpense = onOpenExpense,
                        onDeleteExpense = deleteExpenseAction,
                        emptyStateText = emptyStateText,
                        expenseFallbackTitle = expenseFallbackTitle,
                        currencySymbol = currencySymbol,
                        unknownCategoryLabel = unknownCategoryLabel,
                        resolveCategoryName = { category ->
                            resolveCategoryName(category.id, category.name)
                        },
                        byCategoryLabel = byCategoryLabel,
                        byDateLabel = byDateLabel,
                        groupsExpandedByDefault = groupsExpandedByDefault(),
                        sectionStyle = groupedExpenseSectionStyle(),
                        showGroupingControls = !showFloatingBottomControls,
                        listContentPadding = listContentPadding,
                        bottomControlsBottomPadding = bottomControlsPadding
                    )

                    if (showFloatingBottomControls) {
                        GroupingModeButtons(
                            groupingMode = groupingMode,
                            onGroupingModeChange = { groupingMode = it },
                            byCategoryLabel = byCategoryLabel,
                            byDateLabel = byDateLabel,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = bottomControlsPadding)
                        )
                    }
                }
            }
        } else {
            GroupedExpensesContent(
                groupedExpenses = groupedExpenses,
                categoriesById = categoriesById,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                groupingMode = groupingMode,
                onGroupingModeChange = { groupingMode = it },
                onOpenExpense = onOpenExpense,
                onDeleteExpense = deleteExpenseAction,
                emptyStateText = emptyStateText,
                expenseFallbackTitle = expenseFallbackTitle,
                currencySymbol = currencySymbol,
                unknownCategoryLabel = unknownCategoryLabel,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name)
                },
                byCategoryLabel = byCategoryLabel,
                byDateLabel = byDateLabel,
                groupsExpandedByDefault = groupsExpandedByDefault(),
                sectionStyle = groupedExpenseSectionStyle(),
                listContentPadding = PaddingValues(16.dp)
            )
        }

        pendingExpenseDelete?.let { expense ->
            val expenseDisplayName = groupedExpenseRowPresentation(
                expense = expense,
                categoriesById = categoriesById,
                isGroupedByDate = groupingMode == ExpenseGroupingMode.ByDate,
                expenseFallbackTitle = expenseFallbackTitle,
                unknownCategoryLabel = unknownCategoryLabel,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name)
                }
            ).title
            TransactionDeleteConfirmationDialog(
                itemDisplayName = expenseDisplayName,
                recurringSeriesId = expense.recurringSeriesId,
                deleteTitle = deleteLabel,
                deleteItemConfirmationMessageTemplate = deleteItemConfirmationMessageTemplate,
                recurringDeleteMessageTemplate = recurringExpenseDeleteMessageTemplate,
                onDeleteItem = {
                    pendingExpenseDelete = null
                    scope.launch {
                        repository.deleteExpense(expense.id)
                    }
                },
                onDeleteSeries = { seriesId ->
                    pendingExpenseDelete = null
                    scope.launch {
                        repository.deleteRecurringExpenseSeries(seriesId)
                    }
                },
                onDismiss = {
                    pendingExpenseDelete = null
                }
            )
        }
    }

    protected fun formatDateGroupTitle(
        date: kotlinx.datetime.LocalDate,
        shortMonthNames: List<String>
    ): String {
        return formatExpenseDateGroupTitle(date, shortMonthNames)
    }

    @Composable
    private fun groupedExpenseSectionStyle(): GroupedExpenseSectionStyle {
        return GroupedExpenseSectionStyle(
            containerColor = sectionHeaderContainerColor(),
            contentColor = sectionHeaderContentColor(),
            textStyle = sectionHeaderTextStyle(),
            iconTint = sectionHeaderIconTint(),
            chevronContainerColor = sectionHeaderChevronContainerColor(),
            chevronContentColor = sectionHeaderChevronContentColor()
        )
    }

    protected fun monthName(month: Int, fullMonthNames: List<String>): String {
        return fullMonthNames[month - 1]
    }
}
