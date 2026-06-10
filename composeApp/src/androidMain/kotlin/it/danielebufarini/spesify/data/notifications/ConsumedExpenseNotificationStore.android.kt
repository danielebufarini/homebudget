package it.danielebufarini.spesify.data.notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private val Context.expenseNotificationActionsDataStore by preferencesDataStore(
    name = "expense_notification_actions"
)

internal interface ExpenseNotificationActionStore {
    suspend fun tryConsume(confirmationId: String): Boolean
}

internal class DataStoreExpenseNotificationActionStore(
    private val context: Context
) : ExpenseNotificationActionStore {
    override suspend fun tryConsume(confirmationId: String): Boolean {
        var consumed = false
        context.expenseNotificationActionsDataStore.edit { preferences ->
            val current = preferences[consumedConfirmationIdsKey].orEmpty()
            if (confirmationId !in current) {
                preferences[consumedConfirmationIdsKey] = (current + confirmationId).takeLast(MAX_STORED_CONFIRMATION_IDS).toSet()
                consumed = true
            }
        }
        return consumed
    }

    private fun Set<String>.takeLast(maxSize: Int): List<String> {
        if (size <= maxSize) return toList()
        return toList().takeLast(maxSize)
    }

    private companion object {
        private const val MAX_STORED_CONFIRMATION_IDS = 200
        private val consumedConfirmationIdsKey = stringSetPreferencesKey("consumed_confirmation_ids")
    }
}
