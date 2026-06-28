package it.danielebufarini.spesify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSBundle

@Composable
actual fun rememberAppMetadata(): AppMetadata {
    return remember {
        val bundle = NSBundle.mainBundle
        val appName = (bundle.objectForInfoDictionaryKey("CFBundleDisplayName") as? String)
            ?.takeIf { it.isNotBlank() }
            ?: (bundle.objectForInfoDictionaryKey("CFBundleName") as? String)
                ?.takeIf { it.isNotBlank() }
            ?: GeneratedBuildInfo.APP_NAME
        val shortVersion = (bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String)
            ?.takeIf { it.isNotBlank() }
        val buildNumber = (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
            ?.takeIf { it.isNotBlank() }
        val version = when {
            shortVersion != null && buildNumber != null && shortVersion != buildNumber ->
                "$shortVersion ($buildNumber)"
            shortVersion != null -> shortVersion
            buildNumber != null -> buildNumber
            else -> GeneratedBuildInfo.VERSION_NAME
        }

        AppMetadata(
            appName = appName,
            version = version,
            buildDate = GeneratedBuildInfo.BUILD_DATE
        )
    }
}
