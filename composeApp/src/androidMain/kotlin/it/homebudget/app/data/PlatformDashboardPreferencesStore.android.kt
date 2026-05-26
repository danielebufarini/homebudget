package it.homebudget.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val DASHBOARD_PREFS = "home_budget_dashboard_preferences"
private const val PINNED_DASHBOARD_CARD_KEY = "pinned_dashboard_card"

class PlatformDashboardPreferencesStore(
    context: Context
) : DashboardPreferencesStore {
    private val preferences = context.getSharedPreferences(DASHBOARD_PREFS, Context.MODE_PRIVATE)
    private val pinnedCard = MutableStateFlow(
        preferences.getString(PINNED_DASHBOARD_CARD_KEY, null)?.let(DashboardCardPage::fromStoredValue)
    )

    override val pinnedDashboardCard: StateFlow<DashboardCardPage?> = pinnedCard.asStateFlow()

    override fun pinDashboardCard(page: DashboardCardPage?) {
        preferences.edit().apply {
            if (page == null) {
                remove(PINNED_DASHBOARD_CARD_KEY)
            } else {
                putString(PINNED_DASHBOARD_CARD_KEY, page.name)
            }
        }.apply()
        pinnedCard.value = page
    }
}
