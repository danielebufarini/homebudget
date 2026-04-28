package it.homebudget.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.data.*
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.Instant

class DashboardScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        DashboardRoute(
            showNavigationChrome = true,
            showFab = false,
            onOpenCategories = { navigator?.push(CategoriesScreen()) },
            onOpenAddExpense = { navigator?.push(AddExpenseScreen()) },
            onOpenMonthlyIncomes = { year, month ->
                navigator?.push(MonthlyIncomesScreen(year = year, month = month))
            },
            onOpenMonthlyExpenses = { year, month ->
                navigator?.push(MonthlyExpensesScreen(year = year, month = month))
            },
            onOpenSharedExpenses = { year, month ->
                navigator?.push(SharedExpensesScreen(year = year, month = month))
            },
            onOpenExpenseDetails = { expenseId, readOnly ->
                navigator?.push(AddExpenseScreen(expenseId = expenseId, readOnly = readOnly))
            },
            onOpenCategoryExpenses = { year, month, categoryName ->
                navigator?.push(
                    CategoryExpensesScreen(
                        year = year,
                        month = month,
                        categoryName = categoryName
                    )
                )
            }
        )
    }
}

@Composable
fun DashboardRoute(
    showNavigationChrome: Boolean,
    showFab: Boolean,
    onOpenCategories: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenMonthlyIncomes: (Int, Int) -> Unit,
    onOpenMonthlyExpenses: (Int, Int) -> Unit,
    onOpenSharedExpenses: (Int, Int) -> Unit,
    onOpenExpenseDetails: (String, Boolean) -> Unit,
    onOpenCategoryExpenses: (Int, Int, String) -> Unit
) {
    val repository: ExpenseRepository = koinInject()
    val expenses by repository.getAllExpenses().collectAsState(initial = emptyList())
    val incomes by repository.getAllIncomes().collectAsState(initial = emptyList())
    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    val categoriesById = remember(categories) { categories.associateBy { it.id } }
    var selectedMonth by remember { mutableStateOf(currentMonthCursor()) }

    LaunchedEffect(repository) {
        repository.insertDefaultCategoriesIfEmpty()
    }

    val dashboardData = remember(expenses, incomes, categoriesById) {
        buildDashboardDataCache(expenses, incomes, categoriesById)
    }

    val summary = remember(dashboardData, selectedMonth) {
        dashboardData.monthlySummaries[selectedMonth] ?: emptyMonthlySummary()
    }

    val chartState = remember(dashboardData, selectedMonth) {
        buildCashFlowChartState(
            expenseTotalsByMonth = dashboardData.monthlyExpenseTotalsByMonth,
            incomeTotalsByMonth = dashboardData.monthlyIncomeTotalsByMonth,
            selectedMonth = selectedMonth
        )
    }

    val dashboardBody: @Composable (Modifier) -> Unit = { modifier ->
        DashboardBody(
            modifier = modifier,
            showMonthHeaderCard = !showNavigationChrome,
            selectedMonth = selectedMonth,
            summary = summary,
            chartState = chartState,
            onPreviousMonth = { selectedMonth = selectedMonth.previous() },
            onNextMonth = { selectedMonth = selectedMonth.next() },
            onOpenMonthlyIncomes = {
                onOpenMonthlyIncomes(selectedMonth.year, selectedMonth.month)
            },
            onOpenMonthlyExpenses = {
                onOpenMonthlyExpenses(selectedMonth.year, selectedMonth.month)
            },
            onOpenSharedExpenses = {
                onOpenSharedExpenses(selectedMonth.year, selectedMonth.month)
            },
            onOpenExpenseDetails = { expenseId ->
                onOpenExpenseDetails(expenseId, false)
            },
            onOpenCategoryExpenses = { categoryName ->
                onOpenCategoryExpenses(selectedMonth.year, selectedMonth.month, categoryName)
            }
        )
    }

    if (showNavigationChrome) {
        DashboardScreenScaffold(
            selectedMonth = selectedMonth,
            totalAmount = summary.totalAmount,
            showFab = showFab,
            onOpenCategories = onOpenCategories,
            onOpenAddExpense = onOpenAddExpense,
            onPreviousMonth = { selectedMonth = selectedMonth.previous() },
            onNextMonth = { selectedMonth = selectedMonth.next() }
        ) { modifier ->
            dashboardBody(modifier)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            dashboardBody(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )

            if (showFab) {
                if (rememberIsIosPlatform()) {
                    FloatingActionButton(
                        onClick = onOpenAddExpense,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Text("+")
                    }
                } else {
                    FloatingActionButton(
                        onClick = onOpenAddExpense,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        shape = CircleShape,
                        containerColor = androidAccentButtonContainerColor(),
                        contentColor = androidAccentButtonContentColor()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add expense"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreenScaffold(
    selectedMonth: MonthCursor,
    totalAmount: BigInteger,
    showFab: Boolean,
    onOpenCategories: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val isIos = rememberIsIosPlatform()
    var showNavigationRail by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        DashboardMonthHeader(
                            selectedMonth = selectedMonth,
                            totalAmount = totalAmount,
                            onPreviousMonth = onPreviousMonth,
                            onNextMonth = onNextMonth
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (!isIos) {
                                    showNavigationRail = true
                                }
                            }
                        ) {
                            Text("≡", style = MaterialTheme.typography.titleLarge)
                        }
                    },
                    actions = {
                        DashboardVoiceExpenseAction()
                    }
                )
            },
            floatingActionButton = {
                if (showFab) {
                    if (isIos) {
                        FloatingActionButton(onClick = onOpenAddExpense) {
                            Text("+")
                        }
                    } else {
                        FloatingActionButton(
                            onClick = onOpenAddExpense,
                            shape = CircleShape,
                            containerColor = androidAccentButtonContainerColor(),
                            contentColor = androidAccentButtonContentColor()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add expense"
                            )
                        }
                    }
                }
            }
        ) { padding ->
            content(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            )
        }

        if (!isIos && showNavigationRail) {
            AndroidNavigationRailOverlay(
                selectedDestination = AndroidNavigationDestination.Dashboard,
                onDismiss = { showNavigationRail = false },
                onOpenDashboard = {},
                onOpenCategories = onOpenCategories
            )
        }
    }
}

@Composable
private fun DashboardBody(
    modifier: Modifier,
    showMonthHeaderCard: Boolean,
    selectedMonth: MonthCursor,
    summary: MonthlySummary,
    chartState: LineChartState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenMonthlyIncomes: () -> Unit,
    onOpenMonthlyExpenses: () -> Unit,
    onOpenSharedExpenses: () -> Unit,
    onOpenExpenseDetails: (String) -> Unit,
    onOpenCategoryExpenses: (String) -> Unit
) {
    Column(modifier = modifier) {
        if (showMonthHeaderCard) {
            DashboardMonthHeaderCard(
                selectedMonth = selectedMonth,
                totalAmount = summary.totalAmount,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth
            )

            Spacer(Modifier.height(16.dp))
        }

        ExpenseSummary(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClick = onOpenMonthlyExpenses),
            summary = summary,
            onIncomeClick = onOpenMonthlyIncomes,
            onSharedClick = onOpenSharedExpenses,
            onHighestDayClick = {
                summary.highestExpenseId?.let(onOpenExpenseDetails)
            },
            onTopCategoryClick = {
                if (summary.topCategory != "-") {
                    onOpenCategoryExpenses(summary.topCategory)
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        DashboardCharts(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            lineChartState = chartState,
            categoryTotals = summary.categoryTotals
        )
    }
}

@Composable
private fun DashboardMonthHeaderCard(
    selectedMonth: MonthCursor,
    totalAmount: BigInteger,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    PlatformCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            DashboardMonthHeader(
                selectedMonth = selectedMonth,
                totalAmount = totalAmount,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth
            )
        }
    }
}

@Composable
private fun DashboardMonthHeader(
    selectedMonth: MonthCursor,
    totalAmount: BigInteger,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    MonthNavigationTitle(
        selectedMonth = selectedMonth,
        subtitle = formatAmount(totalAmount),
        onPreviousMonth = onPreviousMonth,
        onNextMonth = onNextMonth
    )
}

@Composable
private fun ExpenseSummary(
    modifier: Modifier,
    summary: MonthlySummary,
    onIncomeClick: () -> Unit,
    onSharedClick: () -> Unit,
    onHighestDayClick: () -> Unit,
    onTopCategoryClick: () -> Unit
) {
    val metrics = listOf(
        SummaryMetricUi(
            label = "Expenses",
            value = summary.expenseCount.toString(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        SummaryMetricUi(
            label = "Shared",
            value = formatAmount(summary.sharedAmount),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            onClick = onSharedClick
        ),
        SummaryMetricUi(
            label = "Income",
            value = formatAmount(summary.incomeAmount),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            onClick = onIncomeClick
        ),
        SummaryMetricUi(
            label = "Top Category",
            value = summary.topCategory,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            onClick = onTopCategoryClick
        ),
        SummaryMetricUi(
            label = "Highest Day",
            value = summary.highestDayLabel,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            trailingValue = formatAmount(summary.highestDayAmount),
            onClick = onHighestDayClick
        )
    )

    PlatformCard(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Monthly Summary",
                style = MaterialTheme.typography.titleLarge
            )

            if (summary.expenseCount == 0) {
                Text(
                    text = "No expenses for this month",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            metrics.chunked(2).forEach { rowMetrics ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (rowMetrics.size == 1) {
                        val item = rowMetrics.single()
                        SummaryMetric(
                            modifier = Modifier.fillMaxWidth(),
                            label = item.label,
                            value = item.value,
                            containerColor = item.containerColor,
                            contentColor = item.contentColor,
                            trailingValue = item.trailingValue,
                            onClick = item.onClick
                        )
                    } else {
                        rowMetrics.forEach { item ->
                            SummaryMetric(
                                modifier = Modifier.weight(1f),
                                label = item.label,
                                value = item.value,
                                containerColor = item.containerColor,
                                contentColor = item.contentColor,
                                trailingValue = item.trailingValue,
                                onClick = item.onClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(
    modifier: Modifier,
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    trailingValue: String? = null,
    onClick: (() -> Unit)? = null
) {
    val isIos = rememberIsIosPlatform()

    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = if (isIos) {
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isIos) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.8f)
            )
            if (trailingValue == null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = trailingValue,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardCharts(
    modifier: Modifier,
    lineChartState: LineChartState,
    categoryTotals: List<CategoryTotal>
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val pageTitles = listOf("Cash Flow", "Expenses by category")

    PlatformCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pagerState.pageCount) { index ->
                    val selected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .size(10.dp)
                    )
                }
            }

            Text(
                text = pageTitles[pagerState.currentPage],
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> LineChartPage(state = lineChartState)
                    else -> CategoryBreakdownPage(categoryTotals = categoryTotals)
                }
            }
        }
    }
}

@Composable
private fun LineChartPage(
    state: LineChartState
) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val xAxisLabelBandHeight = 28.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.series.isEmpty()) {
            Text(
                text = "No expenses in this period",
                style = MaterialTheme.typography.bodyLarge
            )
            return@Column
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 8.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    state.yAxisLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(xAxisLabelBandHeight))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val topInset = 8.dp.toPx()
                        val plotHeight = (height - topInset).coerceAtLeast(1f)
                        val maxValue = state.maxValue.coerceAtLeast(1.0)
                        val lineWidth = 2.75.dp.toPx()

                        listOf(0f, 0.5f, 1f).forEach { marker ->
                            val y = topInset + (plotHeight - (plotHeight * marker))
                            drawLine(
                                color = outlineVariant,
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        repeat(state.pointCount) { index ->
                            val x = if (state.pointCount == 1) {
                                width / 2f
                            } else {
                                width * index / (state.pointCount - 1).toFloat()
                            }
                            drawLine(
                                color = outlineVariant,
                                start = Offset(x, 0f),
                                end = Offset(x, height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        state.series.forEach { series ->
                            val path = Path()
                            series.values.forEachIndexed { index, value ->
                                val x = if (state.pointCount == 1) {
                                    width / 2f
                                } else {
                                    width * index / (state.pointCount - 1).toFloat()
                                }
                                val y = topInset + (plotHeight - ((value / maxValue).toFloat() * plotHeight))
                                if (index == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                            }

                            drawPath(
                                path = path,
                                color = series.color,
                                style = Stroke(width = lineWidth, cap = StrokeCap.Round)
                            )

                            series.values.forEachIndexed { index, value ->
                                if (series.markerDays.contains(index)) {
                                    val x = if (state.pointCount == 1) {
                                        width / 2f
                                    } else {
                                        width * index / (state.pointCount - 1).toFloat()
                                    }
                                    val y = topInset + (plotHeight - ((value / maxValue).toFloat() * plotHeight))
                                    drawCircle(
                                        color = series.color,
                                        radius = 5.75.dp.toPx(),
                                        center = Offset(x, y)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(xAxisLabelBandHeight),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    state.xAxisLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }

        if (state.series.size > 1) {
            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.series.forEach { series ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                            .size(10.dp)
                            .background(series.color, CircleShape)
                        )
                        Text(
                            text = series.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdownPage(categoryTotals: List<CategoryTotal>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (categoryTotals.isEmpty()) {
            Text(
                text = "No expenses for this month",
                style = MaterialTheme.typography.bodyLarge
            )
            return@Column
        }

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            categoryTotals.forEach { categoryTotal ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(categoryTotal.color, CircleShape)
                            )
                            Text(categoryTotal.name, style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(
                            formatAmount(categoryTotal.amount),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(999.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(categoryTotal.fraction.toFloat())
                                .background(categoryTotal.color, RoundedCornerShape(999.dp))
                        )
                    }
                }
            }
        }
    }
}

private fun buildMonthlySummary(
    expenses: List<Expense>,
    incomes: List<Income>,
    categoriesById: Map<String, Category>
): MonthlySummary {
    val incomeAmount = incomes.map { it.amount }.sumBigInteger()

    if (expenses.isEmpty()) {
        return emptyMonthlySummary().copy(incomeAmount = incomeAmount)
    }

    val totalAmount = expenses.map { it.amount }.sumBigInteger()
    val sharedAmount = expenses.filter { it.isShared == 1L }.map { it.amount }.sumBigInteger()
    val categoryGroups = expenses.groupBy { categoryName(it.categoryId, categoriesById) }
    val topCategory = categoryGroups.maxByOrNull { (_, list) -> list.map { it.amount }.sumBigInteger() }?.key ?: "-"
    val highestDay = expenses
        .groupBy { it.date.dayOfMonth() }
        .maxByOrNull { (_, list) -> list.map { it.amount }.sumBigInteger() }
    val highestExpense = highestDay?.value?.maxByOrNull { it.amount }

    val palette = chartPalette()
    val categoryTotals = categoryGroups
        .toList()
        .sortedByDescending { (_, list) -> list.map { it.amount }.sumBigInteger() }
        .mapIndexed { index, (name, groupedExpenses) ->
            val amount = groupedExpenses.map { it.amount }.sumBigInteger()
            CategoryTotal(
                name = name,
                amount = amount,
                fraction = amount.toDisplayDouble() / totalAmount.toDisplayDouble().coerceAtLeast(0.01),
                color = palette[index % palette.size]
            )
        }

    return MonthlySummary(
        totalAmount = totalAmount,
        expenseCount = expenses.size,
        incomeAmount = incomeAmount,
        sharedAmount = sharedAmount,
        averageAmount = averageAmount(totalAmount, expenses.size),
        topCategory = topCategory,
        highestDayLabel = highestDay?.value?.firstOrNull()?.date?.toDayNameAndNumber() ?: "-",
        highestDayAmount = highestDay?.value?.map { it.amount }?.sumBigInteger() ?: BigInteger.ZERO,
        highestExpenseId = highestExpense?.id,
        categoryTotals = categoryTotals
    )
}

private fun emptyMonthlySummary(): MonthlySummary {
    return MonthlySummary(
        totalAmount = BigInteger.ZERO,
        expenseCount = 0,
        incomeAmount = BigInteger.ZERO,
        sharedAmount = BigInteger.ZERO,
        averageAmount = BigInteger.ZERO,
        topCategory = "-",
        highestDayLabel = "-",
        highestDayAmount = BigInteger.ZERO,
        highestExpenseId = null,
        categoryTotals = emptyList()
    )
}

private fun buildDashboardDataCache(
    expenses: List<Expense>,
    incomes: List<Income>,
    categoriesById: Map<String, Category>
): DashboardDataCache {
    if (expenses.isEmpty() && incomes.isEmpty()) {
        return DashboardDataCache(
            monthlySummaries = emptyMap(),
            monthlyExpenseTotalsByMonth = emptyMap(),
            monthlyIncomeTotalsByMonth = emptyMap()
        )
    }

    val expensesByMonth = expenses.groupBy { it.date.asMonthCursor() }
    val incomesByMonth = incomes.groupBy { it.date.asMonthCursor() }
    val allMonths = expensesByMonth.keys + incomesByMonth.keys
    return DashboardDataCache(
        monthlySummaries = allMonths.associateWith { month ->
            buildMonthlySummary(
                expenses = expensesByMonth[month].orEmpty(),
                incomes = incomesByMonth[month].orEmpty(),
                categoriesById = categoriesById
            )
        },
        monthlyExpenseTotalsByMonth = expensesByMonth.mapValues { (_, monthExpenses) ->
            monthExpenses.map { it.amount }.sumBigInteger().toDisplayDouble()
        },
        monthlyIncomeTotalsByMonth = incomesByMonth.mapValues { (_, monthIncomes) ->
            monthIncomes.map { it.amount }.sumBigInteger().toDisplayDouble()
        }
    )
}

private fun buildCashFlowChartState(
    expenseTotalsByMonth: Map<MonthCursor, Double>,
    incomeTotalsByMonth: Map<MonthCursor, Double>,
    selectedMonth: MonthCursor
): LineChartState {
    val months = selectedMonth.trailingMonths(count = 6)

    if (expenseTotalsByMonth.isEmpty() && incomeTotalsByMonth.isEmpty()) {
        return LineChartState(
            pointCount = months.size,
            maxValue = 0.0,
            xAxisLabels = months.map { it.shortLabel() },
            yAxisLabels = listOf("0", "0", "0"),
            series = emptyList()
        )
    }

    val expenseValues = months.map { month ->
        expenseTotalsByMonth[month] ?: 0.0
    }
    val expenseMarkerDays = months.mapIndexedNotNull { index, month ->
        index.takeIf { expenseTotalsByMonth[month] != null }
    }.toSet()
    val incomeValues = months.map { month ->
        incomeTotalsByMonth[month] ?: 0.0
    }
    val incomeMarkerDays = months.mapIndexedNotNull { index, month ->
        index.takeIf { incomeTotalsByMonth[month] != null }
    }.toSet()

    val maxValue = maxOf(
        expenseValues.maxOrNull() ?: 0.0,
        incomeValues.maxOrNull() ?: 0.0,
        1.0
    )

    return LineChartState(
        pointCount = months.size,
        maxValue = maxValue,
        xAxisLabels = months.map { it.shortLabel() },
        yAxisLabels = listOf(
            formatAxisAmount(maxValue),
            formatAxisAmount(maxValue / 2),
            formatAxisAmount(0.0)
        ),
        series = listOf(
            LineSeries(
                label = "Expenses",
                color = Color(0xFFC62828),
                values = expenseValues,
                markerDays = expenseMarkerDays
            ),
            LineSeries(
                label = "Income",
                color = Color(0xFF5BC98A),
                values = incomeValues,
                markerDays = incomeMarkerDays
            )
        )
    )
}

private fun chartPalette(): List<Color> = listOf(
    Color(0xFF006874),
    Color(0xFF8C4A60),
    Color(0xFF2F6A3B),
    Color(0xFF525E7D),
    Color(0xFF9A3412),
    Color(0xFF7A5C00)
)

private fun Long.toDayNameAndNumber(): String {
    val date = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    val dayName = date.dayOfWeek.name.lowercase()
        .replaceFirstChar { it.uppercase() }
    return "$dayName ${date.day}"
}

private fun categoryName(categoryId: String, categoriesById: Map<String, Category>): String {
    return categoriesById[categoryId]?.name ?: "Unknown category"
}

private fun Long.dayOfMonth(): Int {
    return Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .day
}

private fun Long.asMonthCursor(): MonthCursor {
    val date = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return MonthCursor(date.year, date.month.ordinal + 1)
}

private fun currentMonthCursor(): MonthCursor {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return MonthCursor(now.year, now.month.ordinal + 1)
}

private fun formatAxisAmount(amount: Double): String {
    val rounded = floor(amount + 0.5).toInt()
    return rounded.toString()
}

private data class MonthlySummary(
    val totalAmount: BigInteger,
    val expenseCount: Int,
    val incomeAmount: BigInteger,
    val sharedAmount: BigInteger,
    val averageAmount: BigInteger,
    val topCategory: String,
    val highestDayLabel: String,
    val highestDayAmount: BigInteger,
    val highestExpenseId: String?,
    val categoryTotals: List<CategoryTotal>
)

private data class DashboardDataCache(
    val monthlySummaries: Map<MonthCursor, MonthlySummary>,
    val monthlyExpenseTotalsByMonth: Map<MonthCursor, Double>,
    val monthlyIncomeTotalsByMonth: Map<MonthCursor, Double>
)

private data class CategoryTotal(
    val name: String,
    val amount: BigInteger,
    val fraction: Double,
    val color: Color
)

private data class SummaryMetricUi(
    val label: String,
    val value: String,
    val containerColor: Color,
    val contentColor: Color,
    val trailingValue: String? = null,
    val onClick: (() -> Unit)? = null
)

private data class LineChartState(
    val pointCount: Int,
    val maxValue: Double,
    val xAxisLabels: List<String>,
    val yAxisLabels: List<String>,
    val series: List<LineSeries>
)

private data class LineSeries(
    val label: String,
    val color: Color,
    val values: List<Double>,
    val markerDays: Set<Int> = emptySet()
)
