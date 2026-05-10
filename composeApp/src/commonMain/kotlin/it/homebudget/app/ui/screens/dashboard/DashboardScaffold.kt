package it.homebudget.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ionspin.kotlin.bignum.integer.BigInteger
import it.homebudget.app.ui.screens.AndroidDataTransferUi
import it.homebudget.app.ui.screens.AndroidNavigationDestination
import it.homebudget.app.ui.screens.AndroidNavigationRailOverlay
import it.homebudget.app.ui.screens.DashboardVoiceExpenseAction
import it.homebudget.app.ui.screens.MonthCursor
import it.homebudget.app.ui.screens.rememberAndroidDataTransferSheetState
import it.homebudget.app.ui.screens.rememberIsIosPlatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreenScaffold(
    strings: DashboardStrings,
    openVoiceExpenseRequest: Int = 0,
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
                        if (!showFab) {
                            IconButton(onClick = onOpenAddExpense) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = strings.addExpense
                                )
                            }
                        }
                        DashboardVoiceExpenseAction(openVoiceExpenseRequest = openVoiceExpenseRequest)
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
