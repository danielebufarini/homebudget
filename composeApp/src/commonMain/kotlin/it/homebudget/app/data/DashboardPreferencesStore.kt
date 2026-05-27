package it.homebudget.app.data

import kotlinx.coroutines.flow.StateFlow

enum class DashboardCardPage {
    Balance,
    ExpensesByCategory,
    RecentTransactions;

    companion object {
        fun fromStoredValue(value: String?): DashboardCardPage {
            return when (value) {
                "CashFlow" -> Balance
                else -> entries.firstOrNull { it.name == value } ?: Balance
            }
        }
    }
}

interface DashboardPreferencesStore {
    val pinnedDashboardCard: StateFlow<DashboardCardPage?>

    fun pinDashboardCard(page: DashboardCardPage?)
}
