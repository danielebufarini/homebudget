package it.danielebufarini.spesify.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

private const val PINNED_DASHBOARD_CARD_KEY = "pinned_dashboard_card"
private const val HIDE_FUTURE_EXPENSES_KEY = "hide_future_expenses"

class PlatformDashboardPreferencesStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : DashboardPreferencesStore {
    private val pinnedCard = MutableStateFlow(
        defaults.stringForKey(PINNED_DASHBOARD_CARD_KEY)?.let(DashboardCardPage::fromStoredValue)
    )
    private val hideFuture = MutableStateFlow(defaults.boolForKey(HIDE_FUTURE_EXPENSES_KEY))

    override val pinnedDashboardCard: StateFlow<DashboardCardPage?> = pinnedCard.asStateFlow()
    override val hideFutureExpenses: StateFlow<Boolean> = hideFuture.asStateFlow()

    override fun setHideFutureExpenses(hide: Boolean) {
        defaults.setBool(hide, forKey = HIDE_FUTURE_EXPENSES_KEY)
        hideFuture.value = hide
    }

    override fun pinDashboardCard(page: DashboardCardPage?) {
        if (page == null) {
            defaults.removeObjectForKey(PINNED_DASHBOARD_CARD_KEY)
        } else {
            defaults.setObject(page.name, forKey = PINNED_DASHBOARD_CARD_KEY)
        }
        pinnedCard.value = page
    }
}
