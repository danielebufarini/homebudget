package it.homebudget.app

import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    private var openVoiceExpenseRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableDebugStrictMode()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        consumeWidgetVoiceExpenseIntent(intent)
        setContent {
            App(openVoiceExpenseRequest = openVoiceExpenseRequest)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeWidgetVoiceExpenseIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        HomeBudgetWidgetProvider.updateAllWidgets(this)
    }

    private fun enableDebugStrictMode() {
        if (!BuildConfig.DEBUG) {
            return
        }

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build()
        )
    }

    private fun consumeWidgetVoiceExpenseIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_VOICE_EXPENSE) {
            openVoiceExpenseRequest += 1
        }
    }

    companion object {
        const val ACTION_OPEN_VOICE_EXPENSE = "it.homebudget.app.action.OPEN_VOICE_EXPENSE"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
