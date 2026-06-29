package it.danielebufarini.spesify.ui.screens.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import it.danielebufarini.spesify.data.CategoryManagementRepository
import it.danielebufarini.spesify.data.DashboardReadRepository
import it.danielebufarini.spesify.data.RecurringExpenseOverview
import it.danielebufarini.spesify.data.formatAmount
import it.danielebufarini.spesify.database.Category
import it.danielebufarini.spesify.database.Expense
import it.danielebufarini.spesify.localization.rememberCategoryNameResolver
import it.danielebufarini.spesify.ui.screens.collectAsFlowLoadState
import it.danielebufarini.spesify.ui.screens.expenses.AddExpenseScreen
import it.danielebufarini.spesify.ui.screens.expenses.ExpenseListItemRow
import it.danielebufarini.spesify.ui.screens.expenses.epochMillisToLocalDate
import it.danielebufarini.spesify.ui.screens.expenses.formatExpenseDateGroupTitle
import it.danielebufarini.spesify.ui.screens.platform.PlatformCard
import it.danielebufarini.spesify.ui.screens.platform.rememberIsIosPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import spesify.composeapp.generated.resources.Res
import spesify.composeapp.generated.resources.back
import spesify.composeapp.generated.resources.currency_symbol
import spesify.composeapp.generated.resources.monthly
import spesify.composeapp.generated.resources.next
import spesify.composeapp.generated.resources.no_recurring_expenses
import spesify.composeapp.generated.resources.no_recurring_expenses_description
import spesify.composeapp.generated.resources.per_month
import spesify.composeapp.generated.resources.recurring
import spesify.composeapp.generated.resources.recurring_expenses
import spesify.composeapp.generated.resources.shared
import spesify.composeapp.generated.resources.short_month_names
import spesify.composeapp.generated.resources.total_recurring
import spesify.composeapp.generated.resources.unknown_category

class RecurringExpensesScreen(
    private val year: Int,
    private val month: Int
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            showNavigationChrome = true,
            onBack = { navigator?.pop() },
            onOpenExpense = { expenseId ->
                navigator?.push(AddExpenseScreen(expenseId = expenseId))
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
        val dashboardReadRepository: DashboardReadRepository = koinInject()
        val categoryRepository: CategoryManagementRepository = koinInject()
        val isIos = rememberIsIosPlatform()
        val strings = rememberRecurringExpensesStrings()
        val resolveCategoryName = rememberCategoryNameResolver()
        val shortMonthNames = stringArrayResource(Res.array.short_month_names).toList()

        val dataFlow = remember(dashboardReadRepository, categoryRepository, year, month) {
            combine(
                dashboardReadRepository.getRecurringExpenseOverviewForMonth(year = year, month = month),
                categoryRepository.getAllCategories()
            ) { overview, categories ->
                RecurringExpensesUiState(
                    overview = overview,
                    categoriesById = categories.associateBy(Category::id)
                )
            }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }
        val loadState = dataFlow.collectAsFlowLoadState(
            initialValue = RecurringExpensesUiState(
                overview = RecurringExpenseOverview(
                    expenses = emptyList(),
                    totalAmount = 0L,
                    nextOccurrenceDate = null
                ),
                categoriesById = emptyMap()
            ),
            resetKey = "$year:$month"
        )

        if (!showNavigationChrome) {
            RecurringExpensesContent(
                uiState = loadState.value,
                isLoading = loadState.isLoading,
                strings = strings,
                shortMonthNames = shortMonthNames,
                scaffoldPadding = PaddingValues(0.dp),
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name)
                },
                onOpenExpense = onOpenExpense
            )
            return
        }

        Scaffold(
            containerColor = if (isIos) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.systemBars,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(strings.recurring) },
                    navigationIcon = {
                        if (isIos) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = strings.back
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            RecurringExpensesContent(
                uiState = loadState.value,
                isLoading = loadState.isLoading,
                strings = strings,
                shortMonthNames = shortMonthNames,
                scaffoldPadding = padding,
                resolveCategoryName = { category ->
                    resolveCategoryName(category.id, category.name)
                },
                onOpenExpense = onOpenExpense
            )
        }
    }
}

@Composable
private fun RecurringExpensesContent(
    uiState: RecurringExpensesUiState,
    isLoading: Boolean,
    strings: RecurringExpensesStrings,
    shortMonthNames: List<String>,
    scaffoldPadding: PaddingValues,
    resolveCategoryName: (Category) -> String,
    onOpenExpense: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RecurringSummaryCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            totalAmount = uiState.overview.totalAmount,
            strings = strings
        )

        PlatformCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            if (uiState.overview.expenses.isEmpty()) {
                RecurringEmptyState(
                    isLoading = isLoading,
                    strings = strings
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    itemsIndexed(
                        items = uiState.overview.expenses,
                        key = { _, expense -> expense.id }
                    ) { index, expense ->
                        RecurringExpenseRow(
                            expense = expense,
                            categoriesById = uiState.categoriesById,
                            strings = strings,
                            shortMonthNames = shortMonthNames,
                            resolveCategoryName = resolveCategoryName,
                            onOpenExpense = onOpenExpense
                        )

                        if (index < uiState.overview.expenses.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecurringSummaryCard(
    modifier: Modifier = Modifier,
    totalAmount: Long,
    strings: RecurringExpensesStrings
) {
    PlatformCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = strings.totalRecurring,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = strings.recurringExpenses,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${formatAmount(totalAmount, strings.currencySymbol)} / ${strings.perMonth}",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
private fun RecurringEmptyState(
    isLoading: Boolean,
    strings: RecurringExpensesStrings
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = if (isLoading) Alignment.Center else Alignment.TopStart
    ) {
        if (isLoading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
                Text(
                    text = strings.recurringExpenses,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            return@Box
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = strings.noRecurringExpenses,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = strings.noRecurringExpensesDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecurringExpenseRow(
    expense: Expense,
    categoriesById: Map<String, Category>,
    strings: RecurringExpensesStrings,
    shortMonthNames: List<String>,
    resolveCategoryName: (Category) -> String,
    onOpenExpense: (String) -> Unit
) {
    val category = categoriesById[expense.categoryId]
    val categoryName = category?.let(resolveCategoryName) ?: strings.unknownCategory
    val title = expense.description?.takeIf(String::isNotBlank) ?: categoryName
    val dateLabel = formatExpenseDateGroupTitle(
        date = epochMillisToLocalDate(expense.date),
        shortMonthNames = shortMonthNames
    )
    val details = buildList {
        add(categoryName)
        add(strings.monthly)
        add("${strings.next}: $dateLabel")
        if (expense.isShared != 0L) {
            add(strings.shared)
        }
    }.joinToString(" · ")

    ExpenseListItemRow(
        title = title,
        subtitleText = details,
        amountText = formatAmount(expense.amount, strings.currencySymbol),
        categoryIconKey = category?.icon,
        categoryColorKey = category?.id,
        isRecurring = true,
        onClick = { onOpenExpense(expense.id) }
    )
}

private data class RecurringExpensesUiState(
    val overview: RecurringExpenseOverview,
    val categoriesById: Map<String, Category>
)

private data class RecurringExpensesStrings(
    val back: String,
    val currencySymbol: String,
    val monthly: String,
    val next: String,
    val noRecurringExpenses: String,
    val noRecurringExpensesDescription: String,
    val perMonth: String,
    val recurring: String,
    val recurringExpenses: String,
    val shared: String,
    val totalRecurring: String,
    val unknownCategory: String
)

@Composable
private fun rememberRecurringExpensesStrings(): RecurringExpensesStrings =
    RecurringExpensesStrings(
        back = stringResource(Res.string.back),
        currencySymbol = stringResource(Res.string.currency_symbol),
        monthly = stringResource(Res.string.monthly),
        next = stringResource(Res.string.next),
        noRecurringExpenses = stringResource(Res.string.no_recurring_expenses),
        noRecurringExpensesDescription = stringResource(Res.string.no_recurring_expenses_description),
        perMonth = stringResource(Res.string.per_month),
        recurring = stringResource(Res.string.recurring),
        recurringExpenses = stringResource(Res.string.recurring_expenses),
        shared = stringResource(Res.string.shared),
        totalRecurring = stringResource(Res.string.total_recurring),
        unknownCategory = stringResource(Res.string.unknown_category)
    )
