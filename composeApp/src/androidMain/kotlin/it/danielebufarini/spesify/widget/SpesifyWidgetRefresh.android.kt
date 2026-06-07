package it.danielebufarini.spesify.widget

import android.content.Context
import android.content.Intent
import org.koin.mp.KoinPlatformTools

actual object SpesifyWidgetRefresh {
    actual fun requestRefresh() {
        val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return
        val context = runCatching { koin.get<Context>() }.getOrNull() ?: return
        context.applicationContext.sendBroadcast(
            Intent(SPESIFY_WIDGET_REFRESH_ACTION).setPackage(context.packageName)
        )
    }
}
