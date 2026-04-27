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
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigInteger
import it.homebudget.app.database.Income
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
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
            showNavigationChrome = true,
            onBack = { navigator?.pop() },
            onAddIncome = {
                navigator?.push(
                    AddIncomeScreen(
                        initialYear = year,
                        initialMonth = month
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
        showNavigationChrome: Boolean,
        onBack: () -> Unit,
        onAddIncome: () -> Unit,
        onOpenIncome: (String) -> Unit
    ) {
        val repository: ExpenseRepository = koinInject()
        val isIos = rememberIsIosPlatform()
        val scope = rememberCoroutineScope()
        val incomes by repository.getAllIncomes().collectAsState(initial = emptyList())

        val filteredIncomes: List<Income> = remember(incomes, year, month) {
            incomes.filter { income ->
                val localDate = income.date.toLocalDate()
                localDate.year == year && localDate.month.ordinal + 1 == month
            }
        }
        val groupedIncomes: List<Pair<String, List<Income>>> = remember(filteredIncomes) {
            filteredIncomes
                .groupBy { it.date.toLocalDate() }
                .toList()
                .sortedByDescending { (_, items) -> items.maxOf { it.date } }
                .map { (date, items) ->
                    formatExpenseDateGroupTitle(date) to items.sortedByDescending { it.date }
                }
        }
        val totalAmount = remember(filteredIncomes) {
            filteredIncomes.map { it.amount }.sumBigInteger()
        }
        val deleteIncomeAction = { incomeId: String ->
            scope.launch { repository.deleteIncome(incomeId) }
            Unit
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
                            text = "No income for this month",
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
                    for ((groupTitle, incomesForDate) in groupedIncomes) {
                        item(key = groupTitle) {
                            PlatformCard(contentPadding = PaddingValues(0.dp)) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    androidx.compose.foundation.layout.Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = groupTitle,
                                            modifier = Modifier.weight(1f),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = formatAmount(incomesForDate.map { it.amount }.sumBigInteger()),
                                            textAlign = TextAlign.End
                                        )
                                    }

                                    HorizontalDivider()
                                    for (income in incomesForDate) {
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

        if (showNavigationChrome) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text("${monthName(month)} Income")
                                Text(
                                    text = formatAmount(totalAmount),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onBack) {
                                    Text("Back")
                                }
                            }
                        }
                    )
                },
                floatingActionButton = {
                    if (!isIos) {
                        FloatingActionButton(
                            onClick = onAddIncome,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            containerColor = androidAccentButtonContainerColor(),
                            contentColor = androidAccentButtonContentColor()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add income"
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthlyIncomeRow(
    income: Income,
    onOpenIncome: (String) -> Unit,
    onDeleteIncome: (String) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance ->
            distance * 0.35f
        }
    )
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDeleteIncome(income.id)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            DeleteExpenseBackground()
        }
    ) {
        ExpenseListItemRow(
            title = income.description?.ifBlank { "Income" } ?: "Income",
            subtitleText = formatExpenseDateGroupTitle(income.date.toLocalDate()),
            amountText = formatAmount(income.amount),
            subtitleFontSizeOffsetSp = -2,
            onClick = { onOpenIncome(income.id) }
        )
    }
}

private fun monthName(month: Int): String {
    return listOf(
        "January",
        "February",
        "March",
        "April",
        "May",
        "June",
        "July",
        "August",
        "September",
        "October",
        "November",
        "December"
    )[month - 1]
}

private fun Long.toLocalDate() = Instant.fromEpochMilliseconds(this)
    .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    .date
