package it.danielebufarini.spesify.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DashboardPreferencesStoreTest {
    @Test
    fun hideFutureExpensesDefaultsToFalseAndRestoresBothSavedValues() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("spesify_dashboard_preferences", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val store = PlatformDashboardPreferencesStore(context)
            assertFalse(store.hideFutureExpenses.value)
            assertFalse(store.hideFutureExpenses is MutableStateFlow<*>)

            store.setHideFutureExpenses(true)
            assertTrue(store.hideFutureExpenses.value)
            val reopened = PlatformDashboardPreferencesStore(context)
            assertTrue(reopened.hideFutureExpenses.value)

            reopened.setHideFutureExpenses(false)
            assertFalse(reopened.hideFutureExpenses.value)
            assertFalse(PlatformDashboardPreferencesStore(context).hideFutureExpenses.value)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
