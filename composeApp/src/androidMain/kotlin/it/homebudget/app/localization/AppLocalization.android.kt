package it.homebudget.app.localization

import java.util.*

actual fun currentAppLanguageCode(): String = Locale.getDefault().language.ifBlank { "en" }
