package it.danielebufarini.homebudget.ui.screens.transactions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import homebudget.composeapp.generated.resources.Res
import homebudget.composeapp.generated.resources.add_expense
import homebudget.composeapp.generated.resources.add_income
import homebudget.composeapp.generated.resources.back
import homebudget.composeapp.generated.resources.expense
import homebudget.composeapp.generated.resources.income
import it.danielebufarini.homebudget.ui.screens.expenses.AddExpenseScreen
import it.danielebufarini.homebudget.ui.screens.income.AddIncomeScreen
import it.danielebufarini.homebudget.ui.screens.platform.rememberIsIosPlatform
import org.jetbrains.compose.resources.stringResource

class AddTransactionScreen(
    private val initialKind: TransactionEditorKind = TransactionEditorKind.Expense,
    private val initialIncomeYear: Int? = null,
    private val initialIncomeMonth: Int? = null
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current
        RouteContent(showNavigationChrome = true, onClose = { navigator?.pop() })
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun RouteContent(
        showNavigationChrome: Boolean = true,
        onClose: () -> Unit
    ) {
        val isIos = rememberIsIosPlatform()
        val addExpenseLabel = stringResource(Res.string.add_expense)
        val addIncomeLabel = stringResource(Res.string.add_income)
        val backLabel = stringResource(Res.string.back)
        val expenseLabel = stringResource(Res.string.expense)
        val incomeLabel = stringResource(Res.string.income)
        var selectedKind by remember(initialKind) { mutableStateOf(initialKind) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (showNavigationChrome) {
                    TopAppBar(
                        title = {
                            Text(
                                text = when (selectedKind) {
                                    TransactionEditorKind.Expense -> addExpenseLabel
                                    TransactionEditorKind.Income -> addIncomeLabel
                                }
                            )
                        },
                        navigationIcon = {
                            if (isIos) {
                                TextButton(onClick = onClose) {
                                    Text(backLabel)
                                }
                            }
                        }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TransactionKindSelector(
                    selectedKind = selectedKind,
                    expenseLabel = expenseLabel,
                    incomeLabel = incomeLabel,
                    onKindSelected = { selectedKind = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 6.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (selectedKind) {
                        TransactionEditorKind.Expense -> AddExpenseScreen().RouteContent(
                            showNavigationChrome = false,
                            onClose = onClose
                        )
                        TransactionEditorKind.Income -> AddIncomeScreen(
                            initialYear = initialIncomeYear,
                            initialMonth = initialIncomeMonth
                        ).RouteContent(
                            showNavigationChrome = false,
                            onClose = onClose
                        )
                    }
                }
            }
        }
    }
}
