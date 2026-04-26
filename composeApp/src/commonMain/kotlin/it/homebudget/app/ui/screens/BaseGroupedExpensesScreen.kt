package it.homebudget.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import it.homebudget.app.database.Expense
import it.homebudget.app.getPlatform
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.Instant

abstract class BaseGroupedExpensesScreen(
    private val year: Int,
    private val month: Int
) : Screen {

    protected abstract fun screenTitle(monthName: String): String
    protected abstract fun emptyStateText(): String
    protected abstract fun categoryCountLabel(count: Int): String
    protected abstract fun expenseFallbackTitle(): String
    protected abstract fun includeExpense(expense: Expense): Boolean

    protected open fun centerAlignedTitle(): Boolean = false
    protected open fun groupsExpandedByDefault(): Boolean = false
    protected open fun includeCategory(categoryName: String): Boolean = true

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        val repository: ExpenseRepository = koinInject()
        val isIos = remember { getPlatform().isIos }
        val expenses by repository.getAllExpenses().collectAsState(initial = emptyList())
        val categories by repository.getAllCategories().collectAsState(initial = emptyList())
        val expandedState = remember { mutableStateMapOf<String, Boolean>() }
        val categoriesById = remember(categories) { categories.associateBy { it.id } }

        LaunchedEffect(repository) {
            repository.insertDefaultCategoriesIfEmpty()
        }

        val filteredExpenses = remember(expenses, year, month) {
            expenses.filter { expense ->
                val localDate = expense.date.toLocalDate()
                localDate.year == year &&
                    localDate.month.ordinal + 1 == month &&
                    includeExpense(expense)
            }
        }

        val groupedExpenses = remember(filteredExpenses, categories) {
            filteredExpenses
                .groupBy { categoriesById[it.categoryId]?.name ?: "Unknown category" }
                .filterKeys { includeCategory(it) }
                .toList()
                .sortedBy { it.first }
        }
        val totalAmount = remember(groupedExpenses) {
            groupedExpenses
                .flatMap { it.second }
                .map { it.amount }
                .sumBigInteger()
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(screenTitle(monthName(month)))
                            Text(
                                text = formatAmount(totalAmount),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        if (isIos) {
                            TextButton(onClick = { navigator?.pop() }) {
                                Text("Back")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (groupedExpenses.isEmpty()) {
                    item {
                        ElevatedCard {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = emptyStateText(),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                    return@LazyColumn
                }

                groupedExpenses.forEach { (categoryName, categoryExpenses) ->
                    item(key = categoryName) {
                        val expanded = expandedState[categoryName] ?: groupsExpandedByDefault()
                        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedState[categoryName] = !expanded
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(0.72f)
                                    ) {
                                        Text(categoryName)
                                        Text(
                                            text = categoryCountLabel(categoryExpenses.size),
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = formatAmount(categoryExpenses.map { it.amount }.sumBigInteger()),
                                        textAlign = TextAlign.End
                                    )
                                }
                                if (expanded) {
                                    HorizontalDivider()
                                    categoryExpenses
                                        .sortedByDescending { it.date }
                                        .forEach { expense ->
                                            ExpenseListItemRow(
                                                title = expense.description?.ifBlank { expenseFallbackTitle() }
                                                    ?: expenseFallbackTitle(),
                                                dateText = formatDate(expense.date),
                                                amountText = formatAmount(expense.amount),
                                                onClick = {
                                                    navigator?.push(AddExpenseScreen(expense.id))
                                                }
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

    protected fun formatDate(epochMillis: Long): String {
        val date = epochMillis.toLocalDate()
        return "${date.year}-${(date.month.ordinal + 1).toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
    }

    protected fun monthName(month: Int): String {
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
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}
