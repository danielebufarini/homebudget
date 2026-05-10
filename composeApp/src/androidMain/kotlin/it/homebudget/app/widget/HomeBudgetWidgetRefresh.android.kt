package it.homebudget.app.widget

import android.content.Context
import android.content.Intent
import org.koin.mp.KoinPlatformTools

actual object HomeBudgetWidgetRefresh {
    actual fun requestRefresh() {
        val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return
        val context = runCatching { koin.get<Context>() }.getOrNull() ?: return
        context.applicationContext.sendBroadcast(
            Intent(HOME_BUDGET_WIDGET_REFRESH_ACTION).setPackage(context.packageName)
        )
    }
}
