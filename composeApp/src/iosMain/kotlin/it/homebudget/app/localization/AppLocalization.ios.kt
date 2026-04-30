package it.homebudget.app.localization

import platform.Foundation.NSBundle

actual fun currentAppLanguageCode(): String {
    val preferredLocalization = NSBundle.mainBundle.preferredLocalizations.firstOrNull() as? String
    return preferredLocalization?.substringBefore("-") ?: "en"
}
