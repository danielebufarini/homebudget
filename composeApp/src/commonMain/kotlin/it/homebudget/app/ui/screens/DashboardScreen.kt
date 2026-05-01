package it.homebudget.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Menu
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
import it.homebudget.app.localization.LocalStrings
import it.homebudget.app.localization.Strings
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

private val chartPalette: List<Color> = listOf(
    Color(0xFF006874),
    Color(0xFF8C4A60),
    Color(0xFF2F6A3B),
    Color(0xFF525E7D),
    Color(0xFF9A3412),
    Color(0xFF7A5C00)
)

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
    val strings = LocalStrings.current

    LaunchedEffect(repository) {
        repository.insertDefaultCategoriesIfEmpty()
    }

    val dashboardData = remember(expenses, incomes, categoriesById, strings) {
        buildDashboardDataCache(expenses, incomes, categoriesById, strings)
    }

    val summary = remember(dashboardData, selectedMonth) {
        dashboardData.monthlySummaries[selectedMonth] ?: emptyMonthlySummary()
    }

    val chartState = remember(dashboardData, selectedMonth, strings) {
        buildCashFlowChartState(
            expenseTotalsByMonth = dashboardData.monthlyExpenseTotalsByMonth,
            incomeTotalsByMonth = dashboardData.monthlyIncomeTotalsByMonth,
            selectedMonth = selectedMonth,
            strings = strings
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
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = strings.addExpense
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
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val importCsvLauncher = rememberCsvImportLauncher { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val exportCsvLauncher = rememberCsvExportLauncher { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    var showNavigationRail by remember { mutableStateOf(false) }
    val strings = LocalStrings.current

    Box(modifier = Modifier.fillMaxSize()) {
        importCsvLauncher.Render()
        exportCsvLauncher.Render()

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
                        IconButton(onClick = { if (!isIos) showNavigationRail = true }) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = strings.dashboard
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { navigator?.push(CalendarExpensesScreen()) }) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = strings.calendar
                            )
                        }
                        DashboardVoiceExpenseAction()
                    }
                )
            },
            floatingActionButton = {
                if (showFab) {
                    if (isIos) {
                        FloatingActionButton(onClick = onOpenAddExpense) { Text("+") }
                    } else {
                        FloatingActionButton(onClick = onOpenAddExpense) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = strings.addExpense
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
                onOpenCalendar = { navigator?.push(CalendarExpensesScreen()) },
                onOpenCategories = onOpenCategories,
                onImportCsv = { importCsvLauncher.open() },
                onExportCsv = { exportCsvLauncher.open() }
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
                .fillMaxWidth()
                .clickable(onClick = onOpenMonthlyExpenses),
            summary = summary,
            onIncomeClick = onOpenMonthlyIncomes,
            onSharedClick = onOpenSharedExpenses,
            onHighestDayClick = { summary.highestExpenseId?.let(onOpenExpenseDetails) },
            onTopCategoryClick = {
                if (summary.topCategory != "-") onOpenCategoryExpenses(summary.topCategory)
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
    PlatformCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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
    val isIos = rememberIsIosPlatform()
    val strings = LocalStrings.current

    val colorScheme = MaterialTheme.colorScheme
    val metricsRows = remember(summary, strings, colorScheme) {
        listOf(
            SummaryMetricUi(
                label = strings.expenses,
                value = summary.expenseCount.toString(),
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer
            ),
            SummaryMetricUi(
                label = strings.shared,
                value = formatAmount(summary.sharedAmount),
                containerColor = colorScheme.secondaryContainer,
                contentColor = colorScheme.onSecondaryContainer,
                onClick = onSharedClick
            ),
            SummaryMetricUi(
                label = strings.income,
                value = formatAmount(summary.incomeAmount),
                containerColor = colorScheme.tertiaryContainer,
                contentColor = colorScheme.onTertiaryContainer,
                onClick = onIncomeClick
            ),
            SummaryMetricUi(
                label = strings.topCategory,
                value = summary.topCategory,
                containerColor = colorScheme.errorContainer,
                contentColor = colorScheme.onErrorContainer,
                onClick = onTopCategoryClick
            ),
            SummaryMetricUi(
                label = strings.highestDay,
                value = summary.highestDayLabel,
                containerColor = colorScheme.surfaceVariant,
                contentColor = colorScheme.onSurfaceVariant,
                trailingValue = formatAmount(summary.highestDayAmount),
                onClick = onHighestDayClick
            )
        ).chunked(2)
    }

    PlatformCard(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = if (isIos) 14.dp else 16.dp
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (isIos) 12.dp else 14.dp)
        ) {
            Text(
                text = strings.monthlySummary,
                style = MaterialTheme.typography.titleLarge
            )

            if (summary.expenseCount == 0) {
                Text(
                    text = strings.noExpensesForMonth,
                    style = if (isIos) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.bodyLarge
                )
            }

            metricsRows.forEach { rowMetrics ->
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
        border = if (isIos) BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isIos) 0.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isIos) 3.dp else 4.dp)
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
                    Spacer(modifier = Modifier.width(if (isIos) 10.dp else 12.dp))
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
    val strings = LocalStrings.current

    val pageTitles = remember(strings) {
        listOf(strings.cashFlow, strings.expensesByCategory)
    }

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
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .background(
                                color = if (pagerState.currentPage == index)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.outlineVariant,
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

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> LineChartPage(state = lineChartState)
                    else -> CategoryBreakdownPage(categoryTotals = categoryTotals)
                }
            }
        }
    }
}

@Composable
private fun LineChartPage(state: LineChartState) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val xAxisLabelBandHeight = 28.dp
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.series.isEmpty()) {
            Text(text = strings.noExpensesInPeriod, style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Y-axis labels
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 8.dp)
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

            // Chart area
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val topInset = 8.dp.toPx()
                        val plotHeight = (height - topInset).coerceAtLeast(1f)
                        val maxValue = state.maxValue.coerceAtLeast(1.0)
                        val lineWidth = 2.75.dp.toPx()
                        val gridStroke = 1.dp.toPx()

                        fun xFor(index: Int): Float =
                            if (state.pointCount == 1) width / 2f
                            else width * index / (state.pointCount - 1).toFloat()

                        // Horizontal grid lines
                        listOf(0f, 0.5f, 1f).forEach { marker ->
                            val y = topInset + plotHeight * (1f - marker)
                            drawLine(
                                color = outlineVariant,
                                start = Offset(0f, y),
                                end = Offset(width, y),
                                strokeWidth = gridStroke
                            )
                        }

                        // Vertical grid lines
                        repeat(state.pointCount) { index ->
                            val x = xFor(index)
                            drawLine(
                                color = outlineVariant,
                                start = Offset(x, 0f),
                                end = Offset(x, height),
                                strokeWidth = gridStroke
                            )
                        }

                        state.series.forEach { series ->
                            val points = series.values.mapIndexed { index, value ->
                                Offset(
                                    x = xFor(index),
                                    y = topInset + plotHeight -
                                            (value / maxValue).toFloat() * plotHeight
                                )
                            }

                            val path = Path().apply {
                                points.forEachIndexed { index, offset ->
                                    if (index == 0) moveTo(offset.x, offset.y)
                                    else lineTo(offset.x, offset.y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = series.color,
                                style = Stroke(width = lineWidth, cap = StrokeCap.Round)
                            )

                            points.forEachIndexed { index, offset ->
                                if (index in series.markerDays) {
                                    drawCircle(
                                        color = series.color,
                                        radius = 5.75.dp.toPx(),
                                        center = offset
                                    )
                                }
                            }
                        }
                    }
                }

                // X-axis labels
                Row(
                    modifier = Modifier.fillMaxWidth().height(xAxisLabelBandHeight),
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
    val strings = LocalStrings.current

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (categoryTotals.isEmpty()) {
            Text(text = strings.noExpensesForMonth, style = MaterialTheme.typography.bodyLarge)
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
                        Text(formatAmount(categoryTotal.amount), style = MaterialTheme.typography.labelLarge)
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
    categoriesById: Map<String, Category>,
    strings: Strings
): MonthlySummary {
    val incomeAmount = incomes.sumBigIntegerOf(Income::amount)

    if (expenses.isEmpty()) {
        return emptyMonthlySummary().copy(incomeAmount = incomeAmount)
    }

    val totalAmount = expenses.sumBigIntegerOf(Expense::amount)
    val sharedAmount = expenses.sumBigIntegerOf { expense ->
        if (expense.isShared == 1L) expense.amount else BigInteger.ZERO
    }
    val categoryGroups = expenses.groupBy { categoryName(it.categoryId, categoriesById, strings) }
    val categoryAmounts = categoryGroups.mapValues { (_, groupedExpenses) ->
        groupedExpenses.sumBigIntegerOf(Expense::amount)
    }
    val topCategory = categoryAmounts.maxByOrNull { (_, amount) -> amount }?.key ?: "-"
    val dayGroups = expenses.groupBy { it.date.epochDayOfMonth() }
    val dayAmounts = dayGroups.mapValues { (_, dayExpenses) ->
        dayExpenses.sumBigIntegerOf(Expense::amount)
    }
    val highestDay = dayAmounts.maxByOrNull { (_, amount) -> amount }
    val highestDayExpenses = highestDay?.key?.let(dayGroups::get).orEmpty()
    val highestExpense = highestDayExpenses.maxByOrNull { it.amount }

    val categoryTotals = categoryAmounts
        .toList()
        .sortedByDescending { (_, amount) -> amount }
        .mapIndexed { index, (name, amount) ->
            CategoryTotal(
                name = name,
                amount = amount,
                fraction = amount.toDisplayDouble() / totalAmount.toDisplayDouble().coerceAtLeast(0.01),
                color = chartPalette[index % chartPalette.size]
            )
        }

    return MonthlySummary(
        totalAmount = totalAmount,
        expenseCount = expenses.size,
        incomeAmount = incomeAmount,
        sharedAmount = sharedAmount,
        averageAmount = averageAmount(totalAmount, expenses.size),
        topCategory = topCategory,
        highestDayLabel = highestDayExpenses.firstOrNull()?.date?.toEpochDayLabel(strings) ?: "-",
        highestDayAmount = highestDay?.value ?: BigInteger.ZERO,
        highestExpenseId = highestExpense?.id,
        categoryTotals = categoryTotals
    )
}

private fun emptyMonthlySummary() = MonthlySummary(
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

private fun buildDashboardDataCache(
    expenses: List<Expense>,
    incomes: List<Income>,
    categoriesById: Map<String, Category>,
    strings: Strings
): DashboardDataCache {
    if (expenses.isEmpty() && incomes.isEmpty()) {
        return DashboardDataCache(
            monthlySummaries = emptyMap(),
            monthlyExpenseTotalsByMonth = emptyMap(),
            monthlyIncomeTotalsByMonth = emptyMap()
        )
    }

    val expensesByMonth = expenses.groupBy { it.date.toMonthCursor() }
    val incomesByMonth = incomes.groupBy { it.date.toMonthCursor() }
    val allMonths = expensesByMonth.keys + incomesByMonth.keys

    return DashboardDataCache(
        monthlySummaries = allMonths.associateWith { month ->
            buildMonthlySummary(
                expenses = expensesByMonth[month].orEmpty(),
                incomes = incomesByMonth[month].orEmpty(),
                categoriesById = categoriesById,
                strings = strings
            )
        },
        monthlyExpenseTotalsByMonth = expensesByMonth.mapValues { (_, monthExpenses) ->
            monthExpenses.sumBigIntegerOf(Expense::amount).toDisplayDouble()
        },
        monthlyIncomeTotalsByMonth = incomesByMonth.mapValues { (_, monthIncomes) ->
            monthIncomes.sumBigIntegerOf(Income::amount).toDisplayDouble()
        }
    )
}

private fun buildCashFlowChartState(
    expenseTotalsByMonth: Map<MonthCursor, Double>,
    incomeTotalsByMonth: Map<MonthCursor, Double>,
    selectedMonth: MonthCursor,
    strings: Strings
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

    val expenseValues = months.map { month -> expenseTotalsByMonth[month] ?: 0.0 }
    val incomeValues = months.map { month -> incomeTotalsByMonth[month] ?: 0.0 }

    val expenseMarkerDays = buildSet {
        months.forEachIndexed { index, month ->
            if (expenseTotalsByMonth[month] != null) add(index)
        }
    }
    val incomeMarkerDays = buildSet {
        months.forEachIndexed { index, month ->
            if (incomeTotalsByMonth[month] != null) add(index)
        }
    }

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
                label = strings.expenses,
                color = Color(0xFFC62828),
                values = expenseValues,
                markerDays = expenseMarkerDays
            ),
            LineSeries(
                label = strings.income,
                color = Color(0xFF5BC98A),
                values = incomeValues,
                markerDays = incomeMarkerDays
            )
        )
    )
}

private fun formatAxisAmount(amount: Double): String = amount.roundToInt().toString()

private fun Long.toEpochDayLabel(strings: Strings): String {
    val date = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${strings.weekdayNames(date.dayOfWeek)} ${date.day}"
}

private fun Long.epochDayOfMonth(): Int =
    Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
        .day

private fun Long.toMonthCursor(): MonthCursor {
    val date = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return MonthCursor(date.year, date.month.ordinal + 1)
}

private fun currentMonthCursor(): MonthCursor {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return MonthCursor(now.year, now.month.ordinal + 1)
}

private fun categoryName(
    categoryId: String,
    categoriesById: Map<String, Category>,
    strings: Strings
): String = categoriesById[categoryId]
    ?.let { strings.categoryName(it.id, it.name, it.isCustom) }
    ?: strings.unknownCategory

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

private class SummaryMetricUi(
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
