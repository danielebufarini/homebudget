package it.danielebufarini.homebudget.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSUserDefaults

private const val PINNED_DASHBOARD_CARD_KEY = "pinned_dashboard_card"

class PlatformDashboardPreferencesStore : DashboardPreferencesStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val pinnedCard = MutableStateFlow(
        defaults.stringForKey(PINNED_DASHBOARD_CARD_KEY)?.let(DashboardCardPage::fromStoredValue)
    )

    override val pinnedDashboardCard: StateFlow<DashboardCardPage?> = pinnedCard.asStateFlow()

    override fun pinDashboardCard(page: DashboardCardPage?) {
        if (page == null) {
            defaults.removeObjectForKey(PINNED_DASHBOARD_CARD_KEY)
        } else {
            defaults.setObject(page.name, forKey = PINNED_DASHBOARD_CARD_KEY)
        }
        pinnedCard.value = page
    }
}
