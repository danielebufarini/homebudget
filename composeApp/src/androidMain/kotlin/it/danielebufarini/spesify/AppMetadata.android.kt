package it.danielebufarini.spesify

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberAppMetadata(): AppMetadata {
    val context = LocalContext.current

    return remember(context) {
        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(context.packageName, 0)
        }
        val appName = context.applicationInfo.loadLabel(packageManager).toString()
        val versionName = packageInfo.versionName
            ?.takeIf { it.isNotBlank() }
            ?: GeneratedBuildInfo.VERSION_NAME

        AppMetadata(
            appName = appName,
            version = versionName,
            buildDate = GeneratedBuildInfo.BUILD_DATE
        )
    }
}
