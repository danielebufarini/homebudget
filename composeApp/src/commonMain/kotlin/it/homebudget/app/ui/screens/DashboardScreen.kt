package it.homebudget.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.BigInteger.Companion.ZERO
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_expense
import homebudget.composeapp.generated.resources.cash_flow
import homebudget.composeapp.generated.resources.currency_symbol
import homebudget.composeapp.generated.resources.dashboard
import homebudget.composeapp.generated.resources.difference
import homebudget.composeapp.generated.resources.expenses
import homebudget.composeapp.generated.resources.expenses_by_category
import homebudget.composeapp.generated.resources.full_weekday_names
import homebudget.composeapp.generated.resources.highest_day
import homebudget.composeapp.generated.resources.income
import homebudget.composeapp.generated.resources.monthly_summary
import homebudget.composeapp.generated.resources.no_expenses_for_month
import homebudget.composeapp.generated.resources.no_expenses_in_period
import homebudget.composeapp.generated.resources.shared
import homebudget.composeapp.generated.resources.short_month_names
import homebudget.composeapp.generated.resources.top_category
import homebudget.composeapp.generated.resources.unknown_category
import it.homebudget.app.data.DashboardCashFlow
import it.homebudget.app.data.DashboardCategoryTotal
import it.homebudget.app.data.DashboardMonthSummary
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.toDisplayDouble
import it.homebudget.app.database.Category
import it.homebudget.app.localization.rememberCategoryNameResolver
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Clock

private val chartPalette: List<Color> = listOf(
    Color(0xFF006874),
    Color(0xFF8C4A60),
    Color(0xFF2F6A3B),
    Color(0xFF525E7D),
    Color(0xFF9A3412),
    Color(0xFF7A5C00)
)

private data class DashboardStrings(
    val dashboard: String,
    val addExpense: String,
    val expenses: String,
    val shared: String,
    val income: String,
    val difference: String,
    val topCategory: String,
    val highestDay: String,
    val monthlySummary: String,
    val noExpensesForMonth: String,
    val noExpensesInPeriod: String,
    val unknownCategory: String,
    val cashFlow: String,
    val expensesByCategory: String,
    val currencySymbol: String,
    val weekdayNames: List<String>,
    val shortMonthNames: List<String>
)

@Composable
private fun rememberDashboardStrings(): DashboardStrings {
    val weekdayNames = stringArrayResource(Res.array.full_weekday_names).toList()
    val shortMonthNames = stringArrayResource(Res.array.short_month_names).toList()

    return DashboardStrings(
        dashboard = stringResource(Res.string.dashboard),
        addExpense = stringResource(Res.string.add_expense),
        expenses = stringResource(Res.string.expenses),
        shared = stringResource(Res.string.shared),
        income = stringResource(Res.string.income),
        difference = stringResource(Res.string.difference),
        topCategory = stringResource(Res.string.top_category),
        highestDay = stringResource(Res.string.highest_day),
        monthlySummary = stringResource(Res.string.monthly_summary),
        noExpensesForMonth = stringResource(Res.string.no_expenses_for_month),
        noExpensesInPeriod = stringResource(Res.string.no_expenses_in_period),
        unknownCategory = stringResource(Res.string.unknown_category),
        cashFlow = stringResource(Res.string.cash_flow),
        expensesByCategory = stringResource(Res.string.expenses_by_category),
        currencySymbol = stringResource(Res.string.currency_symbol),
        weekdayNames = weekdayNames,
        shortMonthNames = shortMonthNames
    )
}

class DashboardScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        DashboardRoute(
            showNavigationChrome = true,
            showFab = false,
            onOpenCategories = { navigator?.push(CategoriesScreen()) },
            onOpenAddExpense = { navigator?.push(AddExpenseScreen()) },
            onOpenDayExpenses = { year, month, day ->
                navigator?.push(DayExpensesScreen(year = year, month = month, day = day))
            },
            onOpenMonthlyIncomes = { year, month ->
                navigator?.push(MonthlyIncomesScreen(year = year, month = month))
            },
            onOpenMonthlyExpenses = { year, month ->
                navigator?.push(MonthlyExpensesScreen(year = year, month = month))
            },
            onOpenSharedExpenses = { year, month ->
                navigator?.push(SharedExpensesScreen(year = year, month = month))
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
    onOpenDayExpenses: (Int, Int, Int) -> Unit,
    onOpenMonthlyIncomes: (Int, Int) -> Unit,
    onOpenMonthlyExpenses: (Int, Int) -> Unit,
    onOpenSharedExpenses: (Int, Int) -> Unit,
    onOpenCategoryExpenses: (Int, Int, String) -> Unit
) {
    val repository: ExpenseRepository = koinInject()
    val strings = rememberDashboardStrings()
    val categoriesFlow = remember(repository) {
        repository.getAllCategories()
    }
    val categories by categoriesFlow.collectAsState(initial = emptyList())
    val categoriesById = remember(categories) {
        categories.associateBy { it.id }
    }
    var selectedMonth by remember {
        mutableStateOf(currentMonthCursor())
    }

    EnsureDefaultCategoriesInserted(repository)

    val summaryData by remember(repository, selectedMonth) {
        repository.getDashboardMonthSummary(selectedMonth.year, selectedMonth.month)
    }.collectAsState(initial = emptyDashboardMonthSummary())

    val cashFlowData by remember(repository) {
        repository.getDashboardCashFlow()
    }.collectAsState(initial = emptyDashboardCashFlow())

    val summary = remember(summaryData) {
        summaryData.toUiMonthlySummary()
    }

    val chartState = remember(cashFlowData, selectedMonth) {
        buildCashFlowChartState(
            cashFlow = cashFlowData,
            selectedMonth = selectedMonth
        )
    }

    val dashboardBody: @Composable (Modifier) -> Unit = { modifier ->
        DashboardBody(
            modifier = modifier,
            strings = strings,
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
            onOpenDayExpenses = { day ->
                onOpenDayExpenses(selectedMonth.year, selectedMonth.month, day)
            },
            onOpenSharedExpenses = {
                onOpenSharedExpenses(selectedMonth.year, selectedMonth.month)
            },
            onOpenCategoryExpenses = { categoryName ->
                onOpenCategoryExpenses(selectedMonth.year, selectedMonth.month, categoryName)
            }
        )
    }

    if (showNavigationChrome) {
        DashboardScreenScaffold(
            strings = strings,
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
                FloatingActionButton(
                    onClick = onOpenAddExpense,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    if (rememberIsIosPlatform()) {
                        Text("+")
                    } else {
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
    strings: DashboardStrings,
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
    val snackbarHostState = remember { SnackbarHostState() }
    val dataTransferState = rememberAndroidDataTransferSheetState()
    var showNavigationRail by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidDataTransferUi(
            snackbarHostState = snackbarHostState,
            state = dataTransferState
        )

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        DashboardMonthHeader(
                            selectedMonth = selectedMonth,
                            totalAmount = totalAmount,
                            currencySymbol = strings.currencySymbol,
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
                        DashboardVoiceExpenseAction()
                    }
                )
            },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(onClick = onOpenAddExpense) {
                        if (isIos) {
                            Text("+")
                        } else {
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
                onOpenCategories = onOpenCategories,
                onOpenCsvTransfer = dataTransferState::openCsvTransferSheet
            )
        }
    }
}

@Composable
private fun DashboardBody(
    modifier: Modifier,
    strings: DashboardStrings,
    showMonthHeaderCard: Boolean,
    selectedMonth: MonthCursor,
    summary: MonthlySummary,
    chartState: LineChartState,
    categoriesById: Map<String, Category>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenMonthlyIncomes: () -> Unit,
    onOpenMonthlyExpenses: () -> Unit,
    onOpenDayExpenses: (Int) -> Unit,
    onOpenSharedExpenses: () -> Unit,
    onOpenCategoryExpenses: (String) -> Unit
) {
    val resolveCategoryName = rememberCategoryNameResolver()

    Column(modifier = modifier) {
        if (showMonthHeaderCard) {
            DashboardMonthHeaderCard(
                selectedMonth = selectedMonth,
                totalAmount = summary.totalAmount,
                currencySymbol = strings.currencySymbol,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth
            )
            Spacer(Modifier.height(16.dp))
        }

        ExpenseSummary(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenMonthlyExpenses),
            strings = strings,
            selectedMonth = selectedMonth,
            summary = summary,
            categoriesById = categoriesById,
            onIncomeClick = onOpenMonthlyIncomes,
            onSharedClick = onOpenSharedExpenses,
            onHighestDayClick = {
                summary.highestDayOfMonth?.let(onOpenDayExpenses)
            },
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
            strings = strings,
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
    currencySymbol: String,
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
                currencySymbol = currencySymbol,
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
    currencySymbol: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
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
    strings: DashboardStrings,
    selectedMonth: MonthCursor,
    summary: MonthlySummary,
    categoriesById: Map<String, Category>,
    onIncomeClick: () -> Unit,
    onSharedClick: () -> Unit,
    onHighestDayClick: () -> Unit,
    onTopCategoryClick: () -> Unit
) {
    val isIos = rememberIsIosPlatform()
    val resolveCategoryName = rememberCategoryNameResolver()
    val colorScheme = MaterialTheme.colorScheme

    val topCategory = summary.topCategoryId?.let(categoriesById::get)
    val topCategoryIconKey = topCategory?.icon
    val topCategoryValue = topCategory
        ?.let { resolveCategoryName(it.id, it.name, it.isCustom) }
        ?: "-"
    val highestDayValue = remember(selectedMonth, summary.highestDayOfMonth, strings.weekdayNames) {
        summary.highestDayOfMonth?.let { selectedMonth.toDayLabel(it, strings.weekdayNames) } ?: "-"
    }

    val metricsRows = listOf(
        SummaryMetricUi(
            label = strings.expenses,
            value = summary.expenseCount.toString(),
            containerColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer
        ),
        SummaryMetricUi(
            label = strings.shared,
            value = formatAmount(summary.sharedAmount, strings.currencySymbol),
            containerColor = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer,
            onClick = onSharedClick
        ),
        SummaryMetricUi(
            label = strings.income,
            value = formatAmount(summary.incomeAmount, strings.currencySymbol),
            containerColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer,
            onClick = onIncomeClick
        ),
        SummaryMetricUi(
            label = strings.topCategory,
            value = topCategoryValue,
            valueIconColorKey = summary.topCategoryId,
            valueIconKey = topCategoryIconKey,
            containerColor = colorScheme.errorContainer,
            contentColor = colorScheme.onErrorContainer,
            onClick = if (summary.topCategoryId != null) onTopCategoryClick else null
        ),
        SummaryMetricUi(
            label = strings.highestDay,
            value = highestDayValue,
            containerColor = colorScheme.surfaceVariant,
            contentColor = colorScheme.onSurfaceVariant,
            trailingValue = formatAmount(summary.highestDayAmount, strings.currencySymbol),
            onClick = if (summary.highestDayOfMonth != null) onHighestDayClick else null
        )
    ).chunked(2)

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
    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    Card(
        modifier = clickableModifier,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = if (isIos) {
            BorderStroke(
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
            verticalArrangement = Arrangement.spacedBy(if (isIos) 3.dp else 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.8f)
            )

            if (trailingValue == null) {
                SummaryMetricValue(
                    value = value,
                    valueIconColorKey = valueIconColorKey,
                    valueIconKey = valueIconKey,
                    contentColor = contentColor
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SummaryMetricValue(
                        value = value,
                        valueIconColorKey = valueIconColorKey,
                        valueIconKey = valueIconKey,
                        contentColor = contentColor,
                        modifier = Modifier.fillMaxWidth(0.62f),
                        ellipsize = true
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
private fun SummaryMetricValue(
    value: String,
    valueIconColorKey: String?,
    valueIconKey: String?,
    contentColor: Color,
    modifier: Modifier = Modifier,
    ellipsize: Boolean = false
) {
    if (valueIconKey == null) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = modifier,
            maxLines = if (ellipsize) 1 else Int.MAX_VALUE,
            overflow = if (ellipsize) TextOverflow.Ellipsis else TextOverflow.Clip
        )
    } else {
        CategoryLabel(
            iconKey = valueIconKey,
            colorKey = valueIconColorKey,
            text = value,
            modifier = modifier,
            textStyle = MaterialTheme.typography.titleMedium,
            textColor = contentColor,
            maxLines = 1
        )
    }
}

@Composable
private fun DashboardCharts(
    modifier: Modifier,
    strings: DashboardStrings,
    lineChartState: LineChartState,
    categoryTotals: List<CategoryTotal>,
    categoriesById: Map<String, Category>
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val pageTitles = remember(strings.cashFlow, strings.expensesByCategory) {
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
                                color = if (pagerState.currentPage == index) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
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
                    0 -> LineChartPage(strings = strings, state = lineChartState)
                    else -> CategoryBreakdownPage(
                        strings = strings,
                        categoryTotals = categoryTotals,
                        categoriesById = categoriesById
                    )
                }
            }
        }
    }
}

@Composable
private fun LineChartPage(
    strings: DashboardStrings,
    state: LineChartState
) {
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val xAxisLabelBandHeight = 28.dp
    val xAxisLabels = state.months
    val density = LocalDensity.current
    var selectedPoint by remember(state) { mutableStateOf<SelectedChartPoint?>(null) }
    var rootPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var chartPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }
    val topInsetPx = with(density) { 8.dp.toPx() }
    val zeroAxisLabelHalfHeightPx = with(density) { 8.dp.toPx() }
    val hitTargetRadiusPx = with(density) { 18.dp.toPx() }
    val chartGeometry = remember(state, chartSize, topInsetPx) {
        state.buildChartGeometry(chartSize = chartSize, topInsetPx = topInsetPx)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .onGloballyPositioned { coordinates ->
                rootPositionInRoot = coordinates.positionInRoot()
                rootSize = coordinates.size
            }
            .pointerInput(chartGeometry, chartPositionInRoot, rootPositionInRoot, chartSize, hitTargetRadiusPx) {
                detectTapGestures { tapOffset ->
                    val chartOrigin = chartPositionInRoot - rootPositionInRoot
                    val tapInChart = tapOffset - chartOrigin
                    val insideChart = tapInChart.x in 0f..chartSize.width.toFloat() &&
                            tapInChart.y in 0f..chartSize.height.toFloat()

                    val nearestPoint = if (insideChart) {
                        chartGeometry?.findNearestPoint(
                            tapOffset = tapInChart,
                            hitTargetRadiusPx = hitTargetRadiusPx
                        )
                    } else {
                        null
                    }

                    selectedPoint = nearestPoint?.let { point ->
                        SelectedChartPoint(
                            monthIndex = point.monthIndex,
                            detail = state.monthSnapshots[point.monthIndex],
                            anchor = point.center + chartOrigin
                        )
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.series.isEmpty()) {
                Text(
                    text = strings.noExpensesInPeriod,
                    style = MaterialTheme.typography.bodyLarge
                )
                return@Column
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxHeight().padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(1f).width(36.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
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

                        chartGeometry?.zeroLineY?.let { zeroY ->
                            val maxOffsetY = (chartSize.height - zeroAxisLabelHalfHeightPx * 2)
                                .coerceAtLeast(0f)
                            Text(
                                text = "0",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset {
                                        IntOffset(
                                            x = 0,
                                            y = (zeroY - zeroAxisLabelHalfHeightPx)
                                                .coerceIn(0f, maxOffsetY)
                                                .roundToInt()
                                        )
                                    }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(xAxisLabelBandHeight))
                }

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                chartPositionInRoot = coordinates.positionInRoot()
                                chartSize = coordinates.size
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val lineWidth = 2.75.dp.toPx()
                            val gridStroke = 1.dp.toPx()
                            val geometry = chartGeometry ?: return@Canvas

                            geometry.horizontalGridYs.forEach { y ->
                                drawLine(
                                    color = outlineVariant,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = gridStroke
                                )
                            }

                            geometry.zeroLineY?.let { zeroY ->
                                drawLine(
                                    color = onSurfaceVariant.copy(alpha = 0.72f),
                                    start = Offset(0f, zeroY),
                                    end = Offset(size.width, zeroY),
                                    strokeWidth = 1.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }

                            geometry.verticalGridXs.forEach { x ->
                                drawLine(
                                    color = outlineVariant,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = gridStroke
                                )
                            }

                            geometry.series.forEach { series ->
                                drawPath(
                                    path = series.path,
                                    color = series.color,
                                    style = Stroke(width = lineWidth, cap = StrokeCap.Round)
                                )

                                series.markers.forEach { marker ->
                                    drawCircle(
                                        color = series.color,
                                        radius = if (selectedPoint?.monthIndex == marker.monthIndex) {
                                            6.5.dp.toPx()
                                        } else {
                                            5.75.dp.toPx()
                                        },
                                        center = marker.center
                                    )
                                }
                            }
                        }
                    }

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
                                    ChartSeriesKind.Expenses -> strings.expenses
                                    ChartSeriesKind.Income -> strings.income
                                    ChartSeriesKind.Difference -> strings.difference
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        selectedPoint?.let { popupPoint ->
            CashFlowPointPopup(
                strings = strings,
                point = popupPoint,
                rootSize = rootSize,
                popupSize = popupSize,
                onPopupSizeChanged = { popupSize = it }
            )
        }
    }
}

@Composable
private fun BoxScope.CashFlowPointPopup(
    strings: DashboardStrings,
    point: SelectedChartPoint,
    rootSize: IntSize,
    popupSize: IntSize,
    onPopupSizeChanged: (IntSize) -> Unit
) {
    val xMarginPx = with(LocalDensity.current) { 12.dp.toPx() }
    val yMarginPx = with(LocalDensity.current) { 8.dp.toPx() }
    val preferredX = point.anchor.x + xMarginPx
    val preferredY = point.anchor.y - popupSize.height / 2f
    val clampedX = if (rootSize.width == 0) {
        preferredX
    } else {
        min(
            max(8f, preferredX),
            max(8f, rootSize.width - popupSize.width - 8f)
        )
    }
    val clampedY = if (rootSize.height == 0) {
        preferredY
    } else {
        min(
            max(8f, preferredY),
            max(8f, rootSize.height - popupSize.height - yMarginPx)
        )
    }

    Card(
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
            .onGloballyPositioned { coordinates -> onPopupSizeChanged(coordinates.size) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = point.detail.month.shortLabelWithFullYear(strings.shortMonthNames),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CashFlowPointPopupRow(
                label = strings.expenses,
                value = formatAmount(point.detail.expenseAmount, strings.currencySymbol)
            )
            CashFlowPointPopupRow(
                label = strings.income,
                value = formatAmount(point.detail.incomeAmount, strings.currencySymbol)
            )
            CashFlowPointPopupRow(
                label = strings.difference,
                value = formatAmount(point.detail.differenceAmount, strings.currencySymbol)
            )
        }
    }
}

private fun MonthCursor.shortLabelWithFullYear(shortMonthNames: List<String>): String =
    "${shortMonthNames[month - 1]} $year"

@Composable
private fun CashFlowPointPopupRow(
    label: String,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun CategoryBreakdownPage(
    strings: DashboardStrings,
    categoryTotals: List<CategoryTotal>,
    categoriesById: Map<String, Category>
) {
    val resolveCategoryName = rememberCategoryNameResolver()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (categoryTotals.isEmpty()) {
            Text(text = strings.noExpensesForMonth, style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = categoryTotals,
                key = { index, categoryTotal -> categoryTotal.categoryId ?: "unknown-$index" }
            ) { _, categoryTotal ->
                val category = categoryTotal.categoryId?.let(categoriesById::get)
                val categoryName = category
                    ?.let { resolveCategoryName(it.id, it.name, it.isCustom) }
                    ?: strings.unknownCategory
                val categoryIconKey = category?.icon

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
                        Text(
                            text = formatAmount(categoryTotal.amount, strings.currencySymbol),
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

private fun emptyDashboardMonthSummary() = DashboardMonthSummary(
    expenseCount = 0,
    totalAmount = ZERO,
    incomeAmount = ZERO,
    sharedAmount = ZERO,
    averageAmount = ZERO,
    topCategoryId = null,
    highestDayOfMonth = null,
    highestDayAmount = ZERO,
    categoryTotals = emptyList()
)

private fun emptyDashboardCashFlow() = DashboardCashFlow(
    expenseTotalsByMonth = emptyList(),
    incomeTotalsByMonth = emptyList()
)

private fun DashboardMonthSummary.toUiMonthlySummary(): MonthlySummary {
    val totalAmountDouble = totalAmount.toDisplayDouble().coerceAtLeast(0.01)
    return MonthlySummary(
        totalAmount = totalAmount,
        expenseCount = expenseCount,
        incomeAmount = incomeAmount,
        sharedAmount = sharedAmount,
        averageAmount = averageAmount,
        topCategoryId = topCategoryId,
        highestDayOfMonth = highestDayOfMonth,
        highestDayAmount = highestDayAmount,
        categoryTotals = categoryTotals.mapIndexed { index, categoryTotal ->
            categoryTotal.toUiCategoryTotal(
                totalAmount = totalAmountDouble,
                color = chartPalette[index % chartPalette.size]
            )
        }
    )
}

private fun DashboardCategoryTotal.toUiCategoryTotal(
    totalAmount: Double,
    color: Color
): CategoryTotal {
    return CategoryTotal(
        categoryId = categoryId,
        amount = amount,
        fraction = (amount.toDisplayDouble() / totalAmount).coerceIn(0.0, 1.0),
        color = color
    )
}

private fun buildCashFlowChartState(
    cashFlow: DashboardCashFlow,
    selectedMonth: MonthCursor
): LineChartState {
    val months = selectedMonth.trailingMonths(count = 6)
    val expenseTotalsByMonth = cashFlow.expenseTotalsByMonth.associate { total ->
        MonthCursor(total.year, total.month) to total.amount
    }
    val incomeTotalsByMonth = cashFlow.incomeTotalsByMonth.associate { total ->
        MonthCursor(total.year, total.month) to total.amount
    }

    if (expenseTotalsByMonth.isEmpty() && incomeTotalsByMonth.isEmpty()) {
        return LineChartState(
            pointCount = months.size,
            minValue = 0.0,
            maxValue = 0.0,
            months = months,
            yAxisLabels = listOf("0", "0", "0"),
            monthSnapshots = emptyList(),
            series = emptyList()
        )
    }

    val monthSnapshots = months.map { month ->
        ChartMonthSnapshot(
            month = month,
            expenseAmount = expenseTotalsByMonth[month] ?: ZERO,
            incomeAmount = incomeTotalsByMonth[month] ?: ZERO
        )
    }
    val expenseValues = monthSnapshots.map { it.expenseAmount.toDisplayDouble() }
    val incomeValues = monthSnapshots.map { it.incomeAmount.toDisplayDouble() }
    val differenceValues = monthSnapshots.map { it.differenceAmount.toDisplayDouble() }

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
    val differenceMarkerDays = expenseMarkerDays + incomeMarkerDays

    val maxValue = maxOf(
        expenseValues.maxOrNull() ?: 0.0,
        incomeValues.maxOrNull() ?: 0.0,
        differenceValues.maxOrNull() ?: 0.0,
        1.0
    )
    val minValue = minOf(
        differenceValues.minOrNull() ?: 0.0,
        0.0
    )
    val middleValue = (maxValue + minValue) / 2.0

    return LineChartState(
        pointCount = months.size,
        minValue = minValue,
        maxValue = maxValue,
        months = months,
        yAxisLabels = listOf(
            formatAxisAmount(maxValue),
            formatAxisAmount(middleValue),
            formatAxisAmount(minValue)
        ),
        monthSnapshots = monthSnapshots,
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
            ),
            LineSeries(
                kind = ChartSeriesKind.Difference,
                color = Color(0xFF1565C0),
                values = differenceValues,
                markerDays = differenceMarkerDays
            )
        )
    )
}

private fun formatAxisAmount(amount: Double): String = amount.roundToInt().toString()

private fun MonthCursor.toDayLabel(dayOfMonth: Int, weekdayNames: List<String>): String {
    val dayOfWeek = kotlinx.datetime.LocalDate(year, month, dayOfMonth).dayOfWeek
    return "${weekdayNames[dayOfWeek.ordinal]} $dayOfMonth"
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
    val highestDayOfMonth: Int?,
    val highestDayAmount: BigInteger,
    val categoryTotals: List<CategoryTotal>
)

private data class CategoryTotal(
    val categoryId: String?,
    val amount: BigInteger,
    val fraction: Double,
    val color: Color
)

private data class SummaryMetricUi(
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
    val minValue: Double,
    val maxValue: Double,
    val months: List<MonthCursor>,
    val yAxisLabels: List<String>,
    val monthSnapshots: List<ChartMonthSnapshot>,
    val series: List<LineSeries>
)

private enum class ChartSeriesKind {
    Expenses,
    Income,
    Difference
}

private data class LineSeries(
    val kind: ChartSeriesKind,
    val color: Color,
    val values: List<Double>,
    val markerDays: Set<Int> = emptySet()
)

private data class ChartMonthSnapshot(
    val month: MonthCursor,
    val expenseAmount: BigInteger,
    val incomeAmount: BigInteger
) {
    val differenceAmount: BigInteger
        get() = incomeAmount - expenseAmount
}

private data class ChartPoint(
    val monthIndex: Int,
    val kind: ChartSeriesKind,
    val center: Offset
)

private data class SelectedChartPoint(
    val monthIndex: Int,
    val detail: ChartMonthSnapshot,
    val anchor: Offset
)

private data class RenderedLineSeries(
    val color: Color,
    val path: Path,
    val markers: List<ChartPoint>
)

private data class LineChartGeometry(
    val horizontalGridYs: List<Float>,
    val verticalGridXs: List<Float>,
    val zeroLineY: Float?,
    val series: List<RenderedLineSeries>,
    val points: List<ChartPoint>
) {
    fun findNearestPoint(
        tapOffset: Offset,
        hitTargetRadiusPx: Float
    ): ChartPoint? {
        var nearestPoint: ChartPoint? = null
        var nearestDistance = hitTargetRadiusPx

        points.forEach { point ->
            val distance = hypot(
                (tapOffset.x - point.center.x).toDouble(),
                (tapOffset.y - point.center.y).toDouble()
            ).toFloat()
            if (distance <= nearestDistance) {
                nearestDistance = distance
                nearestPoint = point
            }
        }

        return nearestPoint
    }
}

private fun LineChartState.buildChartGeometry(
    chartSize: IntSize,
    topInsetPx: Float
): LineChartGeometry? {
    if (chartSize.width <= 0 || chartSize.height <= 0 || pointCount <= 0 || series.isEmpty()) {
        return null
    }

    val plotHeight = (chartSize.height - topInsetPx).coerceAtLeast(1f)
    val normalizedMinValue = minValue
    val valueRange = (maxValue - normalizedMinValue).coerceAtLeast(1.0)

    fun xFor(index: Int): Float =
        if (pointCount == 1) chartSize.width / 2f
        else chartSize.width * index / (pointCount - 1).toFloat()

    fun yFor(value: Double): Float =
        topInsetPx + plotHeight -
                ((value - normalizedMinValue) / valueRange).toFloat() * plotHeight

    val horizontalGridYs = listOf(0f, 0.5f, 1f).map { marker ->
        topInsetPx + plotHeight * (1f - marker)
    }
    val verticalGridXs = List(pointCount, ::xFor)
    val zeroLineY = if (0.0 in normalizedMinValue..maxValue) {
        yFor(0.0)
    } else {
        null
    }

    val renderedSeries = series.map { series ->
        val points = series.values.mapIndexed { index, value ->
            Offset(x = xFor(index), y = yFor(value))
        }
        val path = Path().apply {
            points.forEachIndexed { index, offset ->
                if (index == 0) moveTo(offset.x, offset.y)
                else lineTo(offset.x, offset.y)
            }
        }
        val markers = series.markerDays.mapNotNull { index ->
            points.getOrNull(index)?.let { point ->
                ChartPoint(
                    monthIndex = index,
                    kind = series.kind,
                    center = point
                )
            }
        }
        RenderedLineSeries(
            color = series.color,
            path = path,
            markers = markers
        )
    }
    val chartPoints = buildList {
        renderedSeries.forEach { addAll(it.markers) }
    }
    return LineChartGeometry(
        horizontalGridYs = horizontalGridYs,
        verticalGridXs = verticalGridXs,
        zeroLineY = zeroLineY,
        series = renderedSeries,
        points = chartPoints
    )
}
