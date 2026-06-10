package it.danielebufarini.spesify

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import it.danielebufarini.spesify.data.notifications.ExpenseConfirmationNotificationContract
import it.danielebufarini.spesify.ui.screens.transactions.ExpenseEditorPrefill

class MainActivity : ComponentActivity() {
    private var openVoiceExpenseRequest by mutableIntStateOf(0)
    private var openExpenseEditorPrefill by mutableStateOf<ExpenseEditorPrefill?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        consumeLaunchIntent(intent)
        setContent {
            App(
                openVoiceExpenseRequest = openVoiceExpenseRequest,
                openExpenseEditorPrefill = openExpenseEditorPrefill
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        SpesifyWidgetProvider.updateAllWidgets(this)
    }

    private fun consumeLaunchIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_VOICE_EXPENSE -> openVoiceExpenseRequest += 1
            ExpenseConfirmationNotificationContract.ACTION_REVIEW -> {
                openExpenseEditorPrefill = intent.toExpenseEditorPrefill()
            }
        }
    }

    private fun Intent.toExpenseEditorPrefill(): ExpenseEditorPrefill? {
        val confirmationId = getStringExtra(ExpenseConfirmationNotificationContract.EXTRA_CONFIRMATION_ID)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val amountMinor = getLongExtra(ExpenseConfirmationNotificationContract.EXTRA_AMOUNT_MINOR, -1L)
            .takeIf { it > 0L }
            ?: return null
        return ExpenseEditorPrefill(
            requestId = confirmationId,
            amountMinor = amountMinor,
            description = getStringExtra(ExpenseConfirmationNotificationContract.EXTRA_MERCHANT)
                ?.takeIf(String::isNotBlank),
            categoryId = getStringExtra(ExpenseConfirmationNotificationContract.EXTRA_CATEGORY_ID)
                ?.takeIf(String::isNotBlank),
            dateMillis = getLongExtra(ExpenseConfirmationNotificationContract.EXTRA_DATE_MILLIS, 0L)
                .takeIf { it > 0L }
        )
    }

    companion object {
        const val ACTION_OPEN_VOICE_EXPENSE = "it.danielebufarini.spesify.action.OPEN_VOICE_EXPENSE"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
