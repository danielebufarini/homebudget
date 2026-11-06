package it.danielebufarini.spesify.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val DASHBOARD_PREFS = "spesify_dashboard_preferences"
private const val PINNED_DASHBOARD_CARD_KEY = "pinned_dashboard_card"
private const val HIDE_FUTURE_EXPENSES_KEY = "hide_future_expenses"

class PlatformDashboardPreferencesStore(
    context: Context
) : DashboardPreferencesStore {
    private val preferences = context.getSharedPreferences(DASHBOARD_PREFS, Context.MODE_PRIVATE)
    private val pinnedCard = MutableStateFlow(
        preferences.getString(PINNED_DASHBOARD_CARD_KEY, null)?.let(DashboardCardPage::fromStoredValue)
    )
    private val hideFuture = MutableStateFlow(preferences.getBoolean(HIDE_FUTURE_EXPENSES_KEY, false))

    override val pinnedDashboardCard: StateFlow<DashboardCardPage?> = pinnedCard.asStateFlow()
    override val hideFutureExpenses: StateFlow<Boolean> = hideFuture.asStateFlow()

    override fun setHideFutureExpenses(hide: Boolean) {
        preferences.edit().putBoolean(HIDE_FUTURE_EXPENSES_KEY, hide).apply()
        hideFuture.value = hide
    }

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
