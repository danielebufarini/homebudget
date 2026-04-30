package it.homebudget.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.data.ExpenseRepository
import it.homebudget.app.data.formatAmount
import it.homebudget.app.data.sumBigIntegerOf
import it.homebudget.app.database.Category
import it.homebudget.app.database.Expense
import it.homebudget.app.localization.LocalStrings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Instant

class CalendarExpensesScreen : Screen {
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
        val repository: ExpenseRepository = koinInject()
        val strings = LocalStrings.current
        val isIos = rememberIsIosPlatform()
        val expenses by repository.getAllExpenses().collectAsState(initial = emptyList())
        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val categoriesById = remember(categories) { categories.associateBy(Category::id) }
        val today = remember { currentLocalDate() }
        var selectedMonth by remember { mutableStateOf(today.asMonthCursor()) }
        var selectedDate by remember { mutableStateOf(today) }

        LaunchedEffect(repository) {
            repository.insertDefaultCategoriesIfEmpty()
        }

        val monthExpenses = remember(expenses, selectedMonth) {
            expenses
                .filter { expense ->
                    val localDate = expense.date.toLocalDate()
                    localDate.year == selectedMonth.year &&
                        localDate.month.ordinal + 1 == selectedMonth.month
                }
                .sortedWith(
                    compareByDescending<Expense> { it.date }
                        .thenBy { it.description.orEmpty() }
                )
        }
        val expensesByDate = remember(monthExpenses) {
            monthExpenses.groupBy { expense -> expense.date.toLocalDate() }
        }
        val monthTotalsByDate = remember(expensesByDate) {
            expensesByDate.mapValues { (_, dailyExpenses) ->
                dailyExpenses.sumBigIntegerOf(Expense::amount)
            }
        }
        val monthTotal = remember(monthExpenses) {
            monthExpenses.sumBigIntegerOf(Expense::amount)
        }
        val selectedDayExpenses = remember(selectedDate, expensesByDate) {
            expensesByDate[selectedDate].orEmpty()
        }

        fun updateSelectedMonth(nextMonth: MonthCursor) {
            selectedMonth = nextMonth
            selectedDate = selectedDate.adjustToMonth(nextMonth)
        }

        val content: @Composable (Modifier) -> Unit = { modifier ->
            CalendarExpensesContent(
                modifier = modifier,
                showMonthHeaderCard = !showNavigationChrome,
                selectedMonth = selectedMonth,
                monthTotal = monthTotal,
                selectedDate = selectedDate,
                monthTotalsByDate = monthTotalsByDate,
                categoriesById = categoriesById,
                selectedDayExpenses = selectedDayExpenses,
                onPreviousMonth = { updateSelectedMonth(selectedMonth.previous()) },
                onNextMonth = { updateSelectedMonth(selectedMonth.next()) },
                onSelectDate = { date ->
                    val monthCursor = date.asMonthCursor()
                    if (monthCursor != selectedMonth) {
                        selectedMonth = monthCursor
                    }
                    selectedDate = date
                },
                onOpenExpense = onOpenExpense
            )
        }

        if (showNavigationChrome) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            MonthNavigationTitle(
                                selectedMonth = selectedMonth,
                                subtitle = "${strings.calendar} • ${formatAmount(monthTotal)}",
                                onPreviousMonth = { updateSelectedMonth(selectedMonth.previous()) },
                                onNextMonth = { updateSelectedMonth(selectedMonth.next()) }
                            )
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onBack) {
                                    Text(strings.back)
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                content(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                )
            }
        } else {
            content(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun CalendarExpensesContent(
    modifier: Modifier,
    showMonthHeaderCard: Boolean,
    selectedMonth: MonthCursor,
    monthTotal: BigInteger,
    selectedDate: LocalDate,
    monthTotalsByDate: Map<LocalDate, BigInteger>,
    categoriesById: Map<String, Category>,
    selectedDayExpenses: List<Expense>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onOpenExpense: (String) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (showMonthHeaderCard) {
            item {
                PlatformCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        MonthNavigationTitle(
                            selectedMonth = selectedMonth,
                            subtitle = formatAmount(monthTotal),
                            onPreviousMonth = onPreviousMonth,
                            onNextMonth = onNextMonth
                        )
                    }
                }
            }
        }

        item {
            CalendarMonthCard(
                selectedMonth = selectedMonth,
                selectedDate = selectedDate,
                monthTotalsByDate = monthTotalsByDate,
                onSelectDate = onSelectDate
            )
        }

        item {
            SelectedDayExpensesCard(
                selectedDate = selectedDate,
                categoriesById = categoriesById,
                expenses = selectedDayExpenses,
                onOpenExpense = onOpenExpense
            )
        }
    }
}

@Composable
private fun CalendarMonthCard(
    selectedMonth: MonthCursor,
    selectedDate: LocalDate,
    monthTotalsByDate: Map<LocalDate, BigInteger>,
    onSelectDate: (LocalDate) -> Unit
) {
    val strings = LocalStrings.current
    val today = remember { currentLocalDate() }
    val visibleDates = remember(selectedMonth) { buildVisibleCalendarDates(selectedMonth) }

    PlatformCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(7) { dayIndex ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strings.shortWeekdayName(dayIndex),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            visibleDates.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    week.forEach { date ->
                        CalendarDayCell(
                            modifier = Modifier.weight(1f),
                            date = date,
                            isInSelectedMonth = date.year == selectedMonth.year &&
                                date.month.ordinal + 1 == selectedMonth.month,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            hasExpenses = monthTotalsByDate.containsKey(date),
                            onClick = { onSelectDate(date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    date: LocalDate,
    isInSelectedMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    hasExpenses: Boolean,
    onClick: () -> Unit
) {
    val numberColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isInSelectedMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }
    val dotColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                border = if (!isSelected && isToday) {
                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier.size(34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = date.day.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = numberColor
                    )
                }
            }

            if (hasExpenses && isInSelectedMonth) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SelectedDayExpensesCard(
    selectedDate: LocalDate,
    categoriesById: Map<String, Category>,
    expenses: List<Expense>,
    onOpenExpense: (String) -> Unit
) {
    val strings = LocalStrings.current
    val dayTotal = remember(expenses) { expenses.sumBigIntegerOf(Expense::amount) }

    PlatformCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatExpenseDateGroupTitle(selectedDate),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = formatAmount(dayTotal),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            HorizontalDivider()

            if (expenses.isEmpty()) {
                Text(
                    text = strings.noExpensesForDay,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                expenses.forEachIndexed { index, expense ->
                    if (index > 0) {
                        HorizontalDivider()
                    }

                    val categoryName = categoriesById[expense.categoryId]
                        ?.let { strings.categoryName(it.id, it.name, it.isCustom) }
                        ?: strings.unknownCategory
                    val title = expense.description?.takeIf { it.isNotBlank() } ?: categoryName
                    val subtitle = when {
                        title != categoryName && expense.isShared == 1L -> "$categoryName • ${strings.shared}"
                        title != categoryName -> categoryName
                        expense.isShared == 1L -> strings.shared
                        else -> formatExpenseDateGroupTitle(selectedDate)
                    }

                    ExpenseListItemRow(
                        title = title,
                        subtitleText = subtitle,
                        amountText = formatAmount(expense.amount),
                        isRecurring = !expense.recurringSeriesId.isNullOrBlank(),
                        onClick = { onOpenExpense(expense.id) }
                    )
                }
            }
        }
    }
}

private fun buildVisibleCalendarDates(month: MonthCursor): List<LocalDate> {
    val firstOfMonth = LocalDate(
        year = month.year,
        month = month.month,
        day = 1
    )
    val leadingDayCount = (firstOfMonth.dayOfWeek.ordinal + 1) % 7

    return List(42) { index ->
        firstOfMonth.shiftDays(index - leadingDayCount)
    }
}

private fun LocalDate.adjustToMonth(month: MonthCursor): LocalDate {
    return LocalDate(
        year = month.year,
        month = month.month,
        day = day.coerceAtMost(daysInMonth(month.year, month.month))
    )
}

private fun LocalDate.asMonthCursor(): MonthCursor {
    return MonthCursor(year = year, month = month.ordinal + 1)
}

private fun currentLocalDate(): LocalDate {
    return Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.fromEpochMilliseconds(this)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

private fun daysInMonth(year: Int, month: Int): Int {
    return when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> 31
    }
}

private fun isLeapYear(year: Int): Boolean {
    return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

private fun LocalDate.shiftDays(days: Int): LocalDate {
    val utc = TimeZone.UTC
    val epochMillis = atStartOfDayIn(utc).toEpochMilliseconds()
    return Instant.fromEpochMilliseconds(epochMillis + days * MILLIS_PER_DAY)
        .toLocalDateTime(utc)
        .date
}

private const val MILLIS_PER_DAY = 86_400_000L
