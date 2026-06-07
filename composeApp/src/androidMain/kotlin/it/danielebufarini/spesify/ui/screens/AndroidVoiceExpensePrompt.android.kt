package it.danielebufarini.spesify.ui.screens

import org.json.JSONObject
import java.util.Locale

internal fun buildAndroidVoiceExpensePrompt(
    transcript: String,
    categories: List<AndroidVoiceExpenseCategory>,
    expenses: List<AndroidVoiceExpenseCandidate>
): String {
    val locale = Locale.getDefault()
    val today = currentSystemLocalDate().toString()
    val languageCode = locale.toLanguageTag().takeIf { it.isNotBlank() } ?: locale.language
    val languageName = locale.getDisplayLanguage(locale).takeIf { it.isNotBlank() } ?: languageCode
    val categoryList = categories.joinToString(separator = "\n") { category ->
        "- id=${category.id}, name=${category.name}"
    }.ifBlank { "- none" }
    val expenseList = expenses.joinToString(separator = "\n") { expense ->
        val description = expense.description?.takeIf { it.isNotBlank() } ?: "none"
        val shared = if (expense.isShared) "yes" else "no"
        "- id=${expense.id}, amount=${expense.amountInput}, date=${formatAndroidVoiceExpenseDate(expense.date)}, categoryId=${expense.categoryId}, categoryName=${expense.categoryName}, shared=$shared, description=$description"
    }.ifBlank { "- none" }

    return buildString {
        appendLine(buildVoiceExpensePromptInstructions(buildAndroidVoiceExpenseOutputContract()))
        appendLine()
        append(
            buildVoiceExpensePromptContext(
                currentDate = today,
                currentLanguageName = languageName,
                currentLanguageCode = languageCode,
                transcript = transcript,
                categoriesText = categoryList,
                expensesText = expenseList
            )
        )
    }
}

internal fun extractAndroidVoiceExpenseJson(rawResponse: String): JSONObject {
    val cleaned = rawResponse
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val start = cleaned.indexOf('{')
    val end = cleaned.lastIndexOf('}')
    val jsonText = if (start in 0..<end) {
        cleaned.substring(start, end + 1)
    } else {
        cleaned
    }
    return JSONObject(jsonText)
}

internal fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optString(name)
        .trim()
        .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

internal fun JSONObject.optNullableBoolean(name: String): Boolean? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return when (val value = opt(name)) {
        is Boolean -> value
        is String -> when (value.trim().lowercase(Locale.US)) {
            "true" -> true
            "false" -> false
            else -> null
        }
        else -> null
    }
}
