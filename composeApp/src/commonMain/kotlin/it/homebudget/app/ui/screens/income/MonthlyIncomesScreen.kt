package it.homebudget.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_income
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.delete
import homebudget.composeapp.generated.resources.delete_item_confirmation_message
import homebudget.composeapp.generated.resources.delete_recurring_item_confirmation_message
import homebudget.composeapp.generated.resources.income
import homebudget.composeapp.generated.resources.no_income_for_month
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.monthBounds
import it.homebudget.app.database.Income
import it.homebudget.app.localization.formatResourceArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private data class MonthlyIncomeStrings(
    val addIncome: String,
    val back: String,
    val currencySymbol: String,
    val delete: String,
    val deleteItemConfirmationMessageTemplate: String,
    val recurringDeleteMessageTemplate: String,
    val income: String,
    val noIncomeForMonth: String
)

@Composable
private fun rememberMonthlyIncomeStrings(): MonthlyIncomeStrings =
    MonthlyIncomeStrings(
        addIncome = stringResource(Res.string.add_income),
        back = stringResource(Res.string.back),
        currencySymbol = stringResource(Res.string.currency_symbol),
        delete = stringResource(Res.string.delete),
        deleteItemConfirmationMessageTemplate = stringResource(Res.string.delete_item_confirmation_message),
        recurringDeleteMessageTemplate = stringResource(Res.string.delete_recurring_item_confirmation_message),
        income = stringResource(Res.string.income),
        noIncomeForMonth = stringResource(Res.string.no_income_for_month)
    )

class MonthlyIncomesScreen(
    private val year: Int,
    private val month: Int
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(
            initialMonth = MonthCursor(year, month),
            showNavigationChrome = true,
            onBack = { navigator?.pop() },
            onAddIncome = { selectedYear, selectedMonth ->
                navigator?.push(
                    AddTransactionScreen(
                        initialKind = TransactionEditorKind.Income,
                        initialIncomeYear = selectedYear,
                        initialIncomeMonth = selectedMonth
                    )
                )
            },
            onOpenIncome = { incomeId ->
                navigator?.push(AddIncomeScreen(incomeId))
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RouteContent(
        initialMonth: MonthCursor,
        showNavigationChrome: Boolean,
        onBack: () -> Unit,
        onAddIncome: (Int, Int) -> Unit,
        onOpenIncome: (String) -> Unit
    ) {
        val repository: ExpenseRepository = koinInject()
        val isIos = rememberIsIosPlatform()
        val scope = rememberCoroutineScope()
        val strings = rememberMonthlyIncomeStrings()
        var selectedMonth by remember(initialMonth) { mutableStateOf(initialMonth) }
        var incomeToDelete by remember { mutableStateOf<Income?>(null) }
        var recurringIncomeToDelete by remember { mutableStateOf<Income?>(null) }
        val (monthStartMillis, monthEndMillis) = remember(selectedMonth) {
            monthBounds(selectedMonth.year, selectedMonth.month)
        }
        val incomesFlow = remember(repository, monthStartMillis, monthEndMillis) {
            repository.getIncomesBetween(monthStartMillis, monthEndMillis)
        }
        val groupedIncomesFlow = remember(incomesFlow) {
            incomesFlow
                .map(::buildGroupedIncomesState)
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
        }
        val groupedIncomesState by groupedIncomesFlow.collectAsState(initial = emptyGroupedIncomesState())
        val groupedIncomes = groupedIncomesState.sections
        val totalAmount = groupedIncomesState.totalAmount
        val deleteIncomeAction: (String) -> Unit = deleteAction@{ incomeId ->
            val income = groupedIncomesState.visibleIncomes.find { it.id == incomeId }
                ?: return@deleteAction
            if (income.recurringSeriesId.isNullOrBlank()) {
                incomeToDelete = income
            } else {
                recurringIncomeToDelete = income
            }
        }
        val content: @Composable (PaddingValues) -> Unit = { padding ->
            val listContentPadding = edgeToEdgeListContentPadding(scaffoldPadding = padding)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (groupedIncomes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(listContentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        PlatformCard {
                            Text(
                                text = strings.noIncomeForMonth,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = listContentPadding,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (section in groupedIncomes) {
                            item(key = section.key) {
                                val sectionId = section.key
                                val expanded = expandedState.getOrPut(sectionId) { true }
                                val chevronRotation by animateFloatAsState(
                                    targetValue = if (expanded) 180f else 0f,
                                    label = "MonthlyIncomeSectionChevronRotation"
                                )
                                PlatformCard(contentPadding = PaddingValues(0.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        expandedState[sectionId] = !expanded
                                                    }
                                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = formatExpenseDateGroupTitle(section.date),
                                                    modifier = Modifier.weight(1f),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Text(
                                                    text = formatAmount(section.totalAmount, strings.currencySymbol),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    textAlign = TextAlign.End
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                ExpandableSectionChevron(
                                                    rotation = chevronRotation,
                                                    containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f),
                                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                        }

                                        if (expanded) {
                                            HorizontalDivider()
                                            for (income in section.incomes) {
                                                key(income.id) {
                                                    MonthlyIncomeRow(
                                                        income = income,
                                                        onOpenIncome = onOpenIncome,
                                                        onDeleteIncome = deleteIncomeAction
                                                    )
                                                    HorizontalDivider()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showNavigationChrome) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets.systemBars,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            MonthNavigationTitle(
                                selectedMonth = selectedMonth,
                                subtitle = "${strings.income} • ${formatAmount(totalAmount, strings.currencySymbol)}",
                                onPreviousMonth = { selectedMonth = selectedMonth.previous() },
                                onNextMonth = { selectedMonth = selectedMonth.next() }
                            )
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onBack) {
                                    Text(strings.back)
                                }
                            }
                        },
                        actions = {
                            if (!isIos) {
                                BottomTransactionQuickActions(
                                    addContentDescription = strings.addIncome,
                                    onAddTransaction = { onAddIncome(selectedMonth.year, selectedMonth.month) },
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                content(padding)
            }
        } else {
            content(PaddingValues(0.dp))
        }

        incomeToDelete?.let { income ->
            val incomeDisplayName = income.description?.ifBlank { strings.income } ?: strings.income
            DeleteConfirmationDialog(
                message = strings.deleteItemConfirmationMessageTemplate.formatResourceArgs(incomeDisplayName),
                onDelete = {
                    incomeToDelete = null
                    scope.launch {
                        repository.deleteIncome(income.id)
                    }
                },
                onDismiss = {
                    incomeToDelete = null
                }
            )
        }

        recurringIncomeToDelete?.let { income ->
            val incomeDisplayName = income.description?.ifBlank { strings.income } ?: strings.income
            RecurringSeriesActionDialog(
                title = strings.delete,
                message = strings.recurringDeleteMessageTemplate.formatResourceArgs(incomeDisplayName),
                onThisInstanceOnly = {
                    recurringIncomeToDelete = null
                    scope.launch {
                        repository.deleteIncome(income.id)
                    }
                },
                onWholeSeries = {
                    recurringIncomeToDelete = null
                    scope.launch {
                        repository.deleteRecurringIncomeSeries(income.recurringSeriesId.orEmpty())
                    }
                },
                onDismiss = {
                    recurringIncomeToDelete = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthlyIncomeRow(
    income: Income,
    onOpenIncome: (String) -> Unit,
    onDeleteIncome: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val currencySymbol = stringResource(Res.string.currency_symbol)
    val incomeLabel = stringResource(Res.string.income)
    val currentOnDeleteIncome by rememberUpdatedState(onDeleteIncome)
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance ->
            distance * 0.35f
        }
    )
    val handleDismiss = remember(income.id, dismissState, scope) {
        { dismissValue: SwipeToDismissBoxValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                currentOnDeleteIncome(income.id)
                scope.launch {
                    dismissState.reset()
                }
            }
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = handleDismiss,
        backgroundContent = {
            DeleteExpenseBackground()
        }
    ) {
        ExpenseListItemRow(
            title = income.description?.ifBlank { incomeLabel } ?: incomeLabel,
            subtitleText = formatExpenseDateGroupTitle(epochMillisToLocalDate(income.date)),
            amountText = formatAmount(income.amount, currencySymbol),
            isRecurring = !income.recurringSeriesId.isNullOrBlank(),
            subtitleFontSizeOffsetSp = -2,
            onClick = { onOpenIncome(income.id) }
        )
    }
}
