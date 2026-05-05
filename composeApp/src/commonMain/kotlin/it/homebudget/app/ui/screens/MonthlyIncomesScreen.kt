package it.homebudget.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.*
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigIntegerOf
import it.homebudget.app.database.Income
import it.homebudget.app.localization.formatResourceArgs
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Instant

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
                    AddIncomeScreen(
                        initialYear = selectedYear,
                        initialMonth = selectedMonth
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
        val addIncomeLabel = stringResource(Res.string.add_income)
        val backLabel = stringResource(Res.string.back)
        val currencySymbol = stringResource(Res.string.currency_symbol)
        val deleteLabel = stringResource(Res.string.delete)
        val deleteItemConfirmationMessageTemplate = stringResource(Res.string.delete_item_confirmation_message)
        val recurringDeleteMessageTemplate = stringResource(Res.string.delete_recurring_item_confirmation_message)
        val incomeLabel = stringResource(Res.string.income)
        val noIncomeForMonthLabel = stringResource(Res.string.no_income_for_month)
        var selectedMonth by remember(initialMonth) { mutableStateOf(initialMonth) }
        var incomeToDelete by remember { mutableStateOf<Income?>(null) }
        var recurringIncomeToDelete by remember { mutableStateOf<Income?>(null) }
        val incomes by repository.getAllIncomes().collectAsState(initial = emptyList())

        val filteredIncomes: List<Income> = remember(incomes, selectedMonth) {
            incomes.filter { income ->
                val localDate = income.date.toLocalDate()
                localDate.year == selectedMonth.year && localDate.month.ordinal + 1 == selectedMonth.month
            }
        }
        val groupedIncomes: List<Pair<kotlinx.datetime.LocalDate, List<Income>>> = remember(filteredIncomes) {
            filteredIncomes
                .groupBy { it.date.toLocalDate() }
                .toList()
                .sortedByDescending { (_, items) -> items.maxOf { it.date } }
                .map { (date, items) -> date to items.sortedByDescending { it.date } }
        }
        val totalAmount = remember(filteredIncomes) {
            filteredIncomes.sumBigIntegerOf(Income::amount)
        }
        val deleteIncomeAction: (String) -> Unit = deleteAction@{ incomeId ->
            val income = filteredIncomes.find { it.id == incomeId } ?: return@deleteAction
            if (income.recurringSeriesId.isNullOrBlank()) {
                incomeToDelete = income
            } else {
                recurringIncomeToDelete = income
            }
        }

        val content: @Composable (PaddingValues) -> Unit = { padding ->
            if (groupedIncomes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PlatformCard {
                        Text(
                            text = noIncomeForMonthLabel,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for ((groupDate, incomesForDate) in groupedIncomes) {
                        item(key = groupDate.toString()) {
                            PlatformCard(contentPadding = PaddingValues(0.dp)) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formatExpenseDateGroupTitle(groupDate),
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = formatAmount(incomesForDate.sumBigIntegerOf(Income::amount), currencySymbol),
                                            textAlign = TextAlign.End
                                        )
                                    }

                                    HorizontalDivider()
                                    for (income in incomesForDate) {
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

        if (showNavigationChrome) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            MonthNavigationTitle(
                                selectedMonth = selectedMonth,
                                subtitle = "$incomeLabel • ${formatAmount(totalAmount, currencySymbol)}",
                                onPreviousMonth = { selectedMonth = selectedMonth.previous() },
                                onNextMonth = { selectedMonth = selectedMonth.next() }
                            )
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onBack) {
                                    Text(backLabel)
                                }
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (!isIos) {
                        FloatingActionButton(
                            onClick = { onAddIncome(selectedMonth.year, selectedMonth.month) }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = addIncomeLabel
                            )
                        }
                    }
                }
            ) { padding ->
                content(padding)
            }
        } else {
            content(PaddingValues(0.dp))
        }

        incomeToDelete?.let { income ->
            val incomeDisplayName = income.description?.ifBlank { incomeLabel } ?: incomeLabel
            DeleteConfirmationDialog(
                message = deleteItemConfirmationMessageTemplate.formatResourceArgs(incomeDisplayName),
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
            val incomeDisplayName = income.description?.ifBlank { incomeLabel } ?: incomeLabel
            RecurringSeriesActionDialog(
                title = deleteLabel,
                message = recurringDeleteMessageTemplate.formatResourceArgs(incomeDisplayName),
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
            subtitleText = formatExpenseDateGroupTitle(income.date.toLocalDate()),
            amountText = formatAmount(income.amount, currencySymbol),
            isRecurring = !income.recurringSeriesId.isNullOrBlank(),
            subtitleFontSizeOffsetSp = -2,
            onClick = { onOpenIncome(income.id) }
        )
    }
}

private fun Long.toLocalDate() = Instant.fromEpochMilliseconds(this)
    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    .date
