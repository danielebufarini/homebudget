package it.homebudget.app.data

import kotlinx.coroutines.flow.StateFlow

enum class DashboardCardPage {
    CashFlow,
    ExpensesByCategory,
    RecentTransactions;

    companion object {
        fun fromStoredValue(value: String?): DashboardCardPage {
            return entries.firstOrNull { it.name == value } ?: CashFlow
        }
    }
}

interface DashboardPreferencesStore {
    val pinnedDashboardCard: StateFlow<DashboardCardPage?>

    fun pinDashboardCard(page: DashboardCardPage?)
}
