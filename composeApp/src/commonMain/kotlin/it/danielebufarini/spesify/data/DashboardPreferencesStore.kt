package it.danielebufarini.spesify.data

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
    val hideFutureExpenses: StateFlow<Boolean>

    fun pinDashboardCard(page: DashboardCardPage?)

    fun setHideFutureExpenses(hide: Boolean)
}
