package it.danielebufarini.spesify.ui.screens.dashboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.danielebufarini.spesify.ui.screens.common.MonthCursor
import it.danielebufarini.spesify.ui.screens.platform.AndroidDataTransferUi
import it.danielebufarini.spesify.ui.screens.platform.AndroidNavigationDestination
import it.danielebufarini.spesify.ui.screens.platform.AndroidNavigationRailOverlay
import it.danielebufarini.spesify.ui.screens.platform.rememberAndroidDataTransferSheetState
import it.danielebufarini.spesify.ui.screens.platform.rememberIsIosPlatform
import it.danielebufarini.spesify.ui.screens.transactions.BottomTransactionQuickActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreenScaffold(
    strings: DashboardStrings,
    openVoiceExpenseRequest: Int = 0,
    selectedMonth: MonthCursor,
    totalAmount: Long,
    showFab: Boolean,
    showQuickActions: Boolean = true,
    onOpenCategories: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenVoiceExpense: () -> Unit,
    onOpenCsvTransfer: (() -> Unit)?,
    onNavigationDrawerVisibilityChange: (Boolean) -> Unit = {},
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
                if (isIos) {
                    IosDashboardTopBar(
                        strings = strings,
                        openVoiceExpenseRequest = openVoiceExpenseRequest,
                        selectedMonth = selectedMonth,
                        totalAmount = totalAmount,
                        onOpenMenu = {
                            showNavigationRail = true
                            onNavigationDrawerVisibilityChange(true)
                        },
                        onOpenAddExpense = onOpenAddExpense,
                        onOpenVoiceExpense = onOpenVoiceExpense,
                        showQuickActions = showQuickActions,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth
                    )
                } else {
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
                                onVoiceExpense = null
                            )
                        }
                    )
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

        if (showNavigationRail) {
            AndroidNavigationRailOverlay(
                selectedDestination = AndroidNavigationDestination.Dashboard,
                onDismiss = {
                    showNavigationRail = false
                    onNavigationDrawerVisibilityChange(false)
                },
                onOpenCategories = {
                    onNavigationDrawerVisibilityChange(false)
                    onOpenCategories()
                },
                onOpenCsvTransfer = {
                    onNavigationDrawerVisibilityChange(false)
                    openCsvTransfer()
                }
            )
        }
    }
}

@Composable
private fun IosDashboardTopBar(
    strings: DashboardStrings,
    openVoiceExpenseRequest: Int,
    selectedMonth: MonthCursor,
    totalAmount: Long,
    onOpenMenu: () -> Unit,
    onOpenAddExpense: () -> Unit,
    onOpenVoiceExpense: () -> Unit,
    showQuickActions: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = strings.dashboard
            )
        }

        DashboardMonthHeader(
            selectedMonth = selectedMonth,
            totalAmount = totalAmount,
            currencySymbol = strings.currencySymbol,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth,
            useIosGlassStyle = true,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 72.dp)
        )

        if (showQuickActions) {
            BottomTransactionQuickActions(
                addContentDescription = strings.addExpense,
                onAddTransaction = onOpenAddExpense,
                modifier = Modifier.align(Alignment.CenterEnd),
                openVoiceExpenseRequest = openVoiceExpenseRequest,
                voiceContentDescription = strings.voiceExpense,
                onVoiceExpense = onOpenVoiceExpense
            )
        }
    }
}
