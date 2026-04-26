package it.homebudget.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.averageAmount
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigInteger
import it.homebudget.app.data.toDisplayDouble
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.getPlatform
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.math.max
import kotlin.time.Clock
import kotlin.time.Instant

class DashboardScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val isIos = remember { getPlatform().isIos }
        val repository: ExpenseRepository = koinInject()
        val expenses by repository.getAllExpenses().collectAsState(initial = emptyList())
        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val categoriesById = remember(categories) { categories.associateBy { it.id } }
        var selectedMonth by remember { mutableStateOf(currentMonthCursor()) }
        var showOptionsMenu by remember { mutableStateOf(false) }

        LaunchedEffect(repository) {
            repository.insertDefaultCategoriesIfEmpty()
        }

        val monthlyExpenses = remember(expenses, selectedMonth) {
            expenses.filter { expense ->
                val date = expense.date.asMonthCursor()
                date.year == selectedMonth.year && date.month == selectedMonth.month
            }
        }

        val summary = remember(monthlyExpenses, categories) {
            buildMonthlySummary(monthlyExpenses, categoriesById)
        }

        val chartState = remember(monthlyExpenses, categories) {
            buildChartState(monthlyExpenses, categoriesById, selectedMonth)
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArrowButton(direction = ArrowDirection.Left) {
                                    selectedMonth = selectedMonth.previous()
                                }
                                Text(
                                    text = selectedMonth.label(),
                                    style = MaterialTheme.typography.titleLarge
                                )
                                ArrowButton(direction = ArrowDirection.Right) {
                                    selectedMonth = selectedMonth.next()
                                }
                            }
                            Text(
                                text = formatAmount(summary.totalAmount),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        Box {
                            IconButton(onClick = { showOptionsMenu = true }) {
                                Text("≡", style = MaterialTheme.typography.titleLarge)
                            }
                            DropdownMenu(
                                expanded = showOptionsMenu,
                                onDismissRequest = { showOptionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Categories") },
                                    onClick = {
                                        showOptionsMenu = false
                                        if (isIos) {
                                            navigator?.replaceAll(CategoriesScreen())
                                        } else {
                                            navigator?.push(CategoriesScreen())
                                        }
                                    }
                                )
                            }
                        }
                    },
                    actions = {}
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { navigator?.push(AddExpenseScreen()) }) {
                    Text("+")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                ExpenseSummary(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable {
                            navigator?.push(
                                MonthlyExpensesScreen(
                                    year = selectedMonth.year,
                                    month = selectedMonth.month
                                )
                            )
                        },
                    summary = summary,
                    onSharedClick = {
                        navigator?.push(
                            SharedExpensesScreen(
                                year = selectedMonth.year,
                                month = selectedMonth.month
                            )
                        )
                    },
                    onHighestDayClick = {
                        summary.highestExpenseId?.let { expenseId ->
                            navigator?.push(AddExpenseScreen(expenseId = expenseId, readOnly = true))
                        }
                    },
                    onTopCategoryClick = {
                        if (summary.topCategory != "-") {
                            navigator?.push(
                                CategoryExpensesScreen(
                                    year = selectedMonth.year,
                                    month = selectedMonth.month,
                                    categoryName = summary.topCategory
                                )
                            )
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
    }

    @Composable
    private fun ExpenseSummary(
        modifier: Modifier,
        summary: MonthlySummary,
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
                label = "Average",
                value = formatAmount(summary.averageAmount),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
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

        ElevatedCard(modifier = modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
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
                    return@Column
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
        Card(
            modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            )
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ArrowButton(
        direction: ArrowDirection,
        onClick: () -> Unit
    ) {
        val arrowColor = MaterialTheme.colorScheme.onSurface
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(24.dp)
        ) {
            Canvas(modifier = Modifier.size(10.dp)) {
                val path = Path().apply {
                    if (direction == ArrowDirection.Left) {
                        moveTo(size.width * 0.75f, size.height * 0.15f)
                        lineTo(size.width * 0.3f, size.height * 0.5f)
                        lineTo(size.width * 0.75f, size.height * 0.85f)
                    } else {
                        moveTo(size.width * 0.25f, size.height * 0.15f)
                        lineTo(size.width * 0.7f, size.height * 0.5f)
                        lineTo(size.width * 0.25f, size.height * 0.85f)
                    }
                }
                drawPath(
                    path = path,
                    color = arrowColor,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
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
        val pageTitles = listOf("Category Trends", "By Category")

        ElevatedCard(modifier = modifier) {
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> CategoryLineChartPage(state = lineChartState)
                        else -> CategoryBreakdownPage(categoryTotals = categoryTotals)
                    }
                }
            }
        }
    }

    @Composable
    private fun CategoryLineChartPage(
        state: LineChartState
    ) {
        val outlineVariant = MaterialTheme.colorScheme.outlineVariant
        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.series.isEmpty()) {
                Text(
                    text = "No expenses for this month",
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
                            val maxValue = state.maxValue.coerceAtLeast(1.0)
                            val lineWidth = 2.dp.toPx()

                            listOf(0f, 0.5f, 1f).forEach { marker ->
                                val y = height - (height * marker)
                                drawLine(
                                    color = outlineVariant,
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            state.series.forEach { series ->
                                val path = Path()
                                series.values.forEachIndexed { index, value ->
                                    val x = if (state.daysInMonth == 1) {
                                        width / 2f
                                    } else {
                                        width * index / (state.daysInMonth - 1).toFloat()
                                    }
                                    val y = height - ((value / maxValue).toFloat() * height)
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
                                    if (value > 0.0) {
                                        val x = if (state.daysInMonth == 1) {
                                            width / 2f
                                        } else {
                                            width * index / (state.daysInMonth - 1).toFloat()
                                        }
                                        val y = height - ((value / maxValue).toFloat() * height)
                                        drawCircle(
                                            color = series.color,
                                            radius = 3.5.dp.toPx(),
                                            center = Offset(x, y)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        state.xAxisLabels.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            style = MaterialTheme.typography.bodyMedium
                        )
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
        categoriesById: Map<String, Category>
    ): MonthlySummary {
        if (expenses.isEmpty()) {
            return MonthlySummary(
                totalAmount = BigInteger.ZERO,
                expenseCount = 0,
                sharedAmount = BigInteger.ZERO,
                averageAmount = BigInteger.ZERO,
                topCategory = "-",
                highestDayLabel = "-",
                highestDayAmount = BigInteger.ZERO,
                highestExpenseId = null,
                categoryTotals = emptyList()
            )
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
            sharedAmount = sharedAmount,
            averageAmount = averageAmount(totalAmount, expenses.size),
            topCategory = topCategory,
            highestDayLabel = highestDay?.value?.firstOrNull()?.date?.toDayNameAndNumber() ?: "-",
            highestDayAmount = highestDay?.value?.map { it.amount }?.sumBigInteger() ?: BigInteger.ZERO,
            highestExpenseId = highestExpense?.id,
            categoryTotals = categoryTotals
        )
    }

    private fun buildChartState(
        expenses: List<Expense>,
        categoriesById: Map<String, Category>,
        month: MonthCursor
    ): LineChartState {
        if (expenses.isEmpty()) {
            return LineChartState(
                daysInMonth = daysInMonth(month.year, month.month),
                maxValue = 0.0,
                xAxisLabels = listOf("1", "", ""),
                yAxisLabels = listOf("EUR 0.00", "EUR 0.00", "EUR 0.00"),
                series = emptyList()
            )
        }

        val daysInMonth = daysInMonth(month.year, month.month)
        val categoriesInMonth = expenses
            .groupBy { categoryName(it.categoryId, categoriesById) }
            .toList()
            .sortedByDescending { (_, list) -> list.map { it.amount }.sumBigInteger() }

        val palette = chartPalette()
        val series = categoriesInMonth.mapIndexed { index, (label, groupedExpenses) ->
            val values = MutableList(daysInMonth) { 0.0 }
            groupedExpenses.forEach { expense ->
                val dayIndex = expense.date.dayOfMonth() - 1
                if (dayIndex in values.indices) {
                    values[dayIndex] += expense.amount.toDisplayDouble()
                }
            }
            LineSeries(
                label = label,
                color = palette[index % palette.size],
                values = values
            )
        }

        val maxValue = series.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
        val middleDay = max(1, ((daysInMonth + 1) / 2))

        return LineChartState(
            daysInMonth = daysInMonth,
            maxValue = maxValue,
            xAxisLabels = listOf("1", middleDay.toString(), daysInMonth.toString()),
            yAxisLabels = listOf(
                formatChartAmount(maxValue),
                formatChartAmount(maxValue / 2),
                formatChartAmount(0.0)
            ),
            series = series
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

    private fun Long.toLocalDateString(): String {
        val date = Instant.fromEpochMilliseconds(this)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
        return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    }

    private fun currentMonthCursor(): MonthCursor {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return MonthCursor(now.year, now.month.ordinal + 1)
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        return when (month) {
            1, 3, 5, 7, 8, 10, 12 -> 31
            4, 6, 9, 11 -> 30
            2 -> if (isLeapYear(year)) 29 else 28
            else -> 30
        }
    }

    private fun isLeapYear(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    }

    private fun formatChartAmount(amount: Double): String {
        val cents = (amount * 100).toLong().toString()
        return formatAmount(BigInteger.parseString(cents))
    }

    private data class MonthCursor(
        val year: Int,
        val month: Int
    ) {
        fun previous(): MonthCursor {
            return if (month == 1) MonthCursor(year - 1, 12) else MonthCursor(year, month - 1)
        }

        fun next(): MonthCursor {
            return if (month == 12) MonthCursor(year + 1, 1) else MonthCursor(year, month + 1)
        }

        fun label(): String {
            val monthNames = listOf(
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
            )
            return "${monthNames[month - 1]} $year"
        }
    }

    private data class MonthlySummary(
        val totalAmount: BigInteger,
        val expenseCount: Int,
        val sharedAmount: BigInteger,
        val averageAmount: BigInteger,
        val topCategory: String,
        val highestDayLabel: String,
        val highestDayAmount: BigInteger,
        val highestExpenseId: String?,
        val categoryTotals: List<CategoryTotal>
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
        val daysInMonth: Int,
        val maxValue: Double,
        val xAxisLabels: List<String>,
        val yAxisLabels: List<String>,
        val series: List<LineSeries>
    )

    private data class LineSeries(
        val label: String,
        val color: Color,
        val values: List<Double>
    )

    private enum class ArrowDirection {
        Left, Right
    }
}
