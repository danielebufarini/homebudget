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
import homebudget.composeapp.generated.resources.*
import it.homebudget.app.data.*
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.database.Income
import it.homebudget.app.localization.rememberCategoryNameResolver
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
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
    val addExpenseLabel = stringResource(Res.string.add_expense)

    EnsureDefaultCategoriesInserted(repository)

    val dashboardData = remember(expenses, incomes) {
        buildDashboardDataCache(expenses, incomes)
    }

    val summary = remember(dashboardData, selectedMonth) {
        dashboardData.monthlySummaries[selectedMonth] ?: emptyMonthlySummary()
    }

    val chartState = remember(dashboardData, selectedMonth) {
        buildCashFlowChartState(
            expenseTotalsByMonth = dashboardData.monthlyExpenseTotalsByMonth,
            incomeTotalsByMonth = dashboardData.monthlyIncomeTotalsByMonth,
            selectedMonth = selectedMonth,
        )
    }

    val dashboardBody: @Composable (Modifier) -> Unit = { modifier ->
        DashboardBody(
            modifier = modifier,
            showMonthHeaderCard = !showNavigationChrome,
            selectedMonth = selectedMonth,
            summary = summary,
            chartState = chartState,
            categoriesById = categoriesById,
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
                            contentDescription = addExpenseLabel
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
    val addExpenseLabel = stringResource(Res.string.add_expense)
    val calendarLabel = stringResource(Res.string.calendar)
    val dashboardLabel = stringResource(Res.string.dashboard)

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
                                contentDescription = dashboardLabel
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { navigator?.push(CalendarExpensesScreen()) }) {
                            Icon(
                                imageVector = Icons.Filled.CalendarMonth,
                                contentDescription = calendarLabel
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
                                contentDescription = addExpenseLabel
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
    categoriesById: Map<String, Category>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenMonthlyIncomes: () -> Unit,
    onOpenMonthlyExpenses: () -> Unit,
    onOpenSharedExpenses: () -> Unit,
    onOpenExpenseDetails: (String) -> Unit,
    onOpenCategoryExpenses: (String) -> Unit
) {
    val resolveCategoryName = rememberCategoryNameResolver()

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
            categoriesById = categoriesById,
            onIncomeClick = onOpenMonthlyIncomes,
            onSharedClick = onOpenSharedExpenses,
            onHighestDayClick = { summary.highestExpenseId?.let(onOpenExpenseDetails) },
            onTopCategoryClick = {
                summary.topCategoryId
                    ?.let { categoriesById[it] }
                    ?.let { onOpenCategoryExpenses(resolveCategoryName(it.id, it.name, it.isCustom)) }
            }
        )

        Spacer(Modifier.height(16.dp))

        DashboardCharts(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            lineChartState = chartState,
            categoryTotals = summary.categoryTotals,
            categoriesById = categoriesById
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
    val currencySymbol = stringResource(Res.string.currency_symbol)
    MonthNavigationTitle(
        selectedMonth = selectedMonth,
        subtitle = formatAmount(totalAmount, currencySymbol),
        onPreviousMonth = onPreviousMonth,
        onNextMonth = onNextMonth
    )
}

@Composable
private fun ExpenseSummary(
    modifier: Modifier,
    summary: MonthlySummary,
    categoriesById: Map<String, Category>,
    onIncomeClick: () -> Unit,
    onSharedClick: () -> Unit,
    onHighestDayClick: () -> Unit,
    onTopCategoryClick: () -> Unit
) {
    val isIos = rememberIsIosPlatform()
    val currencySymbol = stringResource(Res.string.currency_symbol)
    val expensesLabel = stringResource(Res.string.expenses)
    val sharedLabel = stringResource(Res.string.shared)
    val incomeLabel = stringResource(Res.string.income)
    val topCategoryLabel = stringResource(Res.string.top_category)
    val highestDayLabel = stringResource(Res.string.highest_day)
    val monthlySummaryLabel = stringResource(Res.string.monthly_summary)
    val noExpensesForMonthLabel = stringResource(Res.string.no_expenses_for_month)
    val unknownCategoryLabel = stringResource(Res.string.unknown_category)
    val weekdayNames = stringArrayResource(Res.array.full_weekday_names)
    val resolveCategoryName = rememberCategoryNameResolver()
    val topCategoryIconKey = remember(summary.topCategoryId, categoriesById) {
        summary.topCategoryId
            ?.let(categoriesById::get)
            ?.icon
    }
    val topCategoryValue = remember(summary.topCategoryId, categoriesById, unknownCategoryLabel, resolveCategoryName) {
        summary.topCategoryId
            ?.let(categoriesById::get)
            ?.let { resolveCategoryName(it.id, it.name, it.isCustom) }
            ?: "-"
    }
    val highestDayValue = remember(summary.highestDayEpochMillis, weekdayNames) {
        summary.highestDayEpochMillis?.toEpochDayLabel(weekdayNames) ?: "-"
    }

    val colorScheme = MaterialTheme.colorScheme
    val metricsRows = remember(
        summary,
        topCategoryValue,
        highestDayValue,
        currencySymbol,
        expensesLabel,
        sharedLabel,
        incomeLabel,
        topCategoryLabel,
        highestDayLabel,
        colorScheme,
    ) {
        listOf(
            SummaryMetricUi(
                label = expensesLabel,
                value = summary.expenseCount.toString(),
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer
            ),
            SummaryMetricUi(
                label = sharedLabel,
                value = formatAmount(summary.sharedAmount, currencySymbol),
                containerColor = colorScheme.secondaryContainer,
                contentColor = colorScheme.onSecondaryContainer,
                onClick = onSharedClick
            ),
            SummaryMetricUi(
                label = incomeLabel,
                value = formatAmount(summary.incomeAmount, currencySymbol),
                containerColor = colorScheme.tertiaryContainer,
                contentColor = colorScheme.onTertiaryContainer,
                onClick = onIncomeClick
            ),
            SummaryMetricUi(
                label = topCategoryLabel,
                value = topCategoryValue,
                valueIconColorKey = summary.topCategoryId,
                valueIconKey = topCategoryIconKey,
                containerColor = colorScheme.errorContainer,
                contentColor = colorScheme.onErrorContainer,
                onClick = onTopCategoryClick
            ),
            SummaryMetricUi(
                label = highestDayLabel,
                value = highestDayValue,
                containerColor = colorScheme.surfaceVariant,
                contentColor = colorScheme.onSurfaceVariant,
                trailingValue = formatAmount(summary.highestDayAmount, currencySymbol),
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
                text = monthlySummaryLabel,
                style = MaterialTheme.typography.titleLarge
            )

            if (summary.expenseCount == 0) {
                Text(
                    text = noExpensesForMonthLabel,
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
                            valueIconColorKey = item.valueIconColorKey,
                            valueIconKey = item.valueIconKey,
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
                                valueIconColorKey = item.valueIconColorKey,
                                valueIconKey = item.valueIconKey,
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
    valueIconColorKey: String?,
    valueIconKey: String?,
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
                if (valueIconKey == null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    CategoryLabel(
                        iconKey = valueIconKey,
                        colorKey = valueIconColorKey,
                        text = value,
                        textStyle = MaterialTheme.typography.titleMedium,
                        textColor = contentColor,
                        maxLines = 1
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (valueIconKey == null) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        CategoryLabel(
                            iconKey = valueIconKey,
                            colorKey = valueIconColorKey,
                            text = value,
                            modifier = Modifier.fillMaxWidth(0.62f),
                            textStyle = MaterialTheme.typography.titleMedium,
                            textColor = contentColor,
                            maxLines = 1
                        )
                    }
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
    categoryTotals: List<CategoryTotal>,
    categoriesById: Map<String, Category>
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val cashFlowLabel = stringResource(Res.string.cash_flow)
    val expensesByCategoryLabel = stringResource(Res.string.expenses_by_category)

    val pageTitles = remember(cashFlowLabel, expensesByCategoryLabel) {
        listOf(cashFlowLabel, expensesByCategoryLabel)
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
                    else -> CategoryBreakdownPage(
                        categoryTotals = categoryTotals,
                        categoriesById = categoriesById
                    )
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
    val noExpensesInPeriodLabel = stringResource(Res.string.no_expenses_in_period)
    val expensesLabel = stringResource(Res.string.expenses)
    val incomeLabel = stringResource(Res.string.income)
    val xAxisLabels = remember(state.months) { state.months }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.series.isEmpty()) {
            Text(text = noExpensesInPeriodLabel, style = MaterialTheme.typography.bodyLarge)
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
                    xAxisLabels.forEach { month ->
                        Text(
                            text = month.shortLabel(),
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
                            text = when (series.kind) {
                                ChartSeriesKind.Expenses -> expensesLabel
                                ChartSeriesKind.Income -> incomeLabel
                            },
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
private fun CategoryBreakdownPage(
    categoryTotals: List<CategoryTotal>,
    categoriesById: Map<String, Category>
) {
    val currencySymbol = stringResource(Res.string.currency_symbol)
    val noExpensesForMonthLabel = stringResource(Res.string.no_expenses_for_month)
    val unknownCategoryLabel = stringResource(Res.string.unknown_category)
    val resolveCategoryName = rememberCategoryNameResolver()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (categoryTotals.isEmpty()) {
            Text(text = noExpensesForMonthLabel, style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            categoryTotals.forEach { categoryTotal ->
                val categoryName = categoryTotal.categoryId
                    ?.let(categoriesById::get)
                    ?.let { resolveCategoryName(it.id, it.name, it.isCustom) }
                    ?: unknownCategoryLabel
                val categoryIconKey = categoryTotal.categoryId
                    ?.let(categoriesById::get)
                    ?.icon
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
                            CategoryLabel(
                                iconKey = categoryIconKey,
                                colorKey = categoryTotal.categoryId,
                                text = categoryName,
                                textStyle = MaterialTheme.typography.bodyLarge,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        Text(formatAmount(categoryTotal.amount, currencySymbol), style = MaterialTheme.typography.labelLarge)
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
): MonthlySummary {
    val incomeAmount = incomes.sumBigIntegerOf(Income::amount)

    if (expenses.isEmpty()) {
        return emptyMonthlySummary().copy(incomeAmount = incomeAmount)
    }

    val totalAmount = expenses.sumBigIntegerOf(Expense::amount)
    val sharedAmount = expenses.sumBigIntegerOf { expense ->
        if (expense.isShared == 1L) expense.amount else BigInteger.ZERO
    }
    val categoryGroups = expenses.groupBy(Expense::categoryId)
    val categoryAmounts = categoryGroups.mapValues { (_, groupedExpenses) ->
        groupedExpenses.sumBigIntegerOf(Expense::amount)
    }
    val topCategoryId = categoryAmounts.maxByOrNull { (_, amount) -> amount }?.key
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
        .mapIndexed { index, (categoryId, amount) ->
            CategoryTotal(
                categoryId = categoryId,
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
        topCategoryId = topCategoryId,
        highestDayEpochMillis = highestDayExpenses.firstOrNull()?.date,
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
    topCategoryId = null,
    highestDayEpochMillis = null,
    highestDayAmount = BigInteger.ZERO,
    highestExpenseId = null,
    categoryTotals = emptyList()
)

private fun buildDashboardDataCache(
    expenses: List<Expense>,
    incomes: List<Income>,
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
): LineChartState {
    val months = selectedMonth.trailingMonths(count = 6)

    if (expenseTotalsByMonth.isEmpty() && incomeTotalsByMonth.isEmpty()) {
        return LineChartState(
            pointCount = months.size,
            maxValue = 0.0,
            months = months,
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
        months = months,
        yAxisLabels = listOf(
            formatAxisAmount(maxValue),
            formatAxisAmount(maxValue / 2),
            formatAxisAmount(0.0)
        ),
        series = listOf(
            LineSeries(
                kind = ChartSeriesKind.Expenses,
                color = Color(0xFFC62828),
                values = expenseValues,
                markerDays = expenseMarkerDays
            ),
            LineSeries(
                kind = ChartSeriesKind.Income,
                color = Color(0xFF5BC98A),
                values = incomeValues,
                markerDays = incomeMarkerDays
            )
        )
    )
}

private fun formatAxisAmount(amount: Double): String = amount.roundToInt().toString()

private fun Long.toEpochDayLabel(weekdayNames: List<String>): String {
    val date = Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${weekdayNames[date.dayOfWeek.ordinal]} ${date.day}"
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

private data class MonthlySummary(
    val totalAmount: BigInteger,
    val expenseCount: Int,
    val incomeAmount: BigInteger,
    val sharedAmount: BigInteger,
    val averageAmount: BigInteger,
    val topCategoryId: String?,
    val highestDayEpochMillis: Long?,
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
    val categoryId: String?,
    val amount: BigInteger,
    val fraction: Double,
    val color: Color
)

private class SummaryMetricUi(
    val label: String,
    val value: String,
    val valueIconColorKey: String? = null,
    val valueIconKey: String? = null,
    val containerColor: Color,
    val contentColor: Color,
    val trailingValue: String? = null,
    val onClick: (() -> Unit)? = null
)

private data class LineChartState(
    val pointCount: Int,
    val maxValue: Double,
    val months: List<MonthCursor>,
    val yAxisLabels: List<String>,
    val series: List<LineSeries>
)

private enum class ChartSeriesKind {
    Expenses,
    Income
}

private data class LineSeries(
    val kind: ChartSeriesKind,
    val color: Color,
    val values: List<Double>,
    val markerDays: Set<Int> = emptySet()
)
