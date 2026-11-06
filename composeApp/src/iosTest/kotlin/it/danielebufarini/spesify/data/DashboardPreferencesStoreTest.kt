package it.danielebufarini.spesify.data

import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.NSUserDefaults
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DashboardPreferencesStoreTest {
    @Test
    fun hideFutureExpensesDefaultsToFalseAndRestoresBothSavedValues() {
        val suite = "DashboardHideFutureExpensesTest"
        val defaults = NSUserDefaults(suiteName = suite)
        defaults.removePersistentDomainForName(suite)
        try {
            val store = PlatformDashboardPreferencesStore(defaults)
            assertFalse(store.hideFutureExpenses.value)
            assertFalse(store.hideFutureExpenses is MutableStateFlow<*>)

            store.setHideFutureExpenses(true)
            assertTrue(store.hideFutureExpenses.value)
            val reopened = PlatformDashboardPreferencesStore(NSUserDefaults(suiteName = suite))
            assertTrue(reopened.hideFutureExpenses.value)

            reopened.setHideFutureExpenses(false)
            assertFalse(reopened.hideFutureExpenses.value)
            assertFalse(PlatformDashboardPreferencesStore(NSUserDefaults(suiteName = suite)).hideFutureExpenses.value)
        } finally {
            defaults.removePersistentDomainForName(suite)
        }
    }
}
