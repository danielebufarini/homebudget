package it.danielebufarini.spesify.ui.screens.dashboard

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun LockDashboardOrientation(landscape: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    if (landscape && activity != null) {
        DisposableEffect(activity) {
            val previousOrientation = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            onDispose {
                activity.requestedOrientation = previousOrientation
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
