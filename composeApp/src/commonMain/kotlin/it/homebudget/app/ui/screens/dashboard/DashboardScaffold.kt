package it.homebudget.app.ui.screens.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.homebudget.app.ui.screens.AndroidDataTransferUi
import it.homebudget.app.ui.screens.AndroidNavigationDestination
import it.homebudget.app.ui.screens.AndroidNavigationRailOverlay
import it.homebudget.app.ui.screens.BottomTransactionQuickActions
import it.homebudget.app.ui.screens.MonthCursor
import it.homebudget.app.ui.screens.rememberAndroidDataTransferSheetState
import it.homebudget.app.ui.screens.rememberIsIosPlatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreenScaffold(
    strings: DashboardStrings,
    openVoiceExpenseRequest: Int = 0,
    selectedMonth: MonthCursor,
    totalAmount: Long,
    showFab: Boolean,
    onOpenCategories: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenVoiceExpense: () -> Unit,
    onOpenCsvTransfer: (() -> Unit)?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    val isIos = rememberIsIosPlatform()
    val snackbarHostState = remember { SnackbarHostState() }
    val dataTransferState = rememberAndroidDataTransferSheetState()
    val openCsvTransfer = onOpenCsvTransfer ?: dataTransferState::openCsvTransferSheet
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
                        IconButton(
                            onClick = { showNavigationRail = true },
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = strings.dashboard
                            )
                        }
                    },
                    actions = {
                        BottomTransactionQuickActions(
                            addContentDescription = strings.addExpense,
                            onAddTransaction = onOpenAddExpense,
                            modifier = Modifier.padding(end = 12.dp),
                            openVoiceExpenseRequest = openVoiceExpenseRequest,
                            voiceContentDescription = strings.voiceExpense,
                            onVoiceExpense = if (isIos) onOpenVoiceExpense else null
                        )
                    }
                )
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

        if (showNavigationRail) {
            AndroidNavigationRailOverlay(
                selectedDestination = AndroidNavigationDestination.Dashboard,
                onDismiss = { showNavigationRail = false },
                onOpenCategories = onOpenCategories,
                onOpenCsvTransfer = openCsvTransfer
            )
        }
    }
}
